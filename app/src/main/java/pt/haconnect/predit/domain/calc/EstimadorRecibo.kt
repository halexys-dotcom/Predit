package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.ParametrosCCT
import pt.haconnect.predit.domain.model.Rubrica
import pt.haconnect.predit.domain.model.TipoTurno
import pt.haconnect.predit.domain.model.dividirArredondando
import java.time.LocalDate
import java.time.YearMonth

/**
 * Estimador do recibo: cruza os dias reais, a projeção, os parâmetros do CCT e as
 * tabelas de IRS e devolve as rubricas estimadas, com o mesmo rubricaId do catálogo —
 * é isso que permite pôr a estimativa ao lado do recibo e marcar divergências.
 *
 * Domínio puro: sem Room, sem Android.
 */

enum class NaturezaRubrica { ABONO, DESCONTO }

data class RubricaEstimada(
    val rubricaId: Long,
    val codigo: String,
    val nome: String,
    val valorMil: Long,                 // 1/10000 €
    val natureza: NaturezaRubrica
)

data class EstimativaRecibo(
    val anoMes: String,                 // "2026-08"
    val rubricas: List<RubricaEstimada>,
    val totalAbonos: Long,
    val totalDescontos: Long,
    val liquido: Long
)

data class ContextoEstimativa(
    val anoMes: YearMonth,
    val contrato: ContratoUtilizador,
    val parametrosCCT: ParametrosCCT,
    val rubricas: List<Rubrica>,
    val diasReais: List<DiaReal>,
    val projecao: List<DiaProjetado>,
    val feriados: Set<Long> = emptySet(),
    /**
     * Feriado municipal do município do contrato, em dia/mês (0 = não definido). O ano vem
     * do mês estimado: quem guarda a data é a tabela `municipio`, não o contrato. O cálculo
     * do epochDay do ano concreto é feito em [estimarRecibo].
     */
    val municipioFeriadoDia: Int = 0,
    val municipioFeriadoMes: Int = 0,
    val escaloesIRS: List<EscalaoIRS>,
    /**
     * Catálogo de tipos de turno — só é preciso para saber se um dia com horas
     * suplementares é dia de descanso. Sem catálogo, assume-se dia normal.
     */
    val tiposTurno: List<TipoTurno> = emptyList(),
    /**
     * Ausências registadas (férias, baixa). Additivo e com valor por omissão: sem
     * ausências o comportamento é o de antes desta fase. Para além de retirarem os dias
     * reais de origem PDF, cortam os subsídios pelos dias úteis que cobrem (8.3).
     */
    val ausencias: List<Ausencia> = emptyList()
)

/** Códigos que o estimador calcula. Tudo o resto sai com 0 (introduzido à mão). */
private val CODIGOS_ESTIMADOS = setOf(
    "VENC", "SUP_ALIM", "SUP_TRAN", "HNOT",
    "HSUP_DN", "HSUP_NT", "HSUP_DN_FER", "HSUP_NT_FER", "HSUP_DN_DESC", "HSUP_NT_DESC",
    "DESC_FER", "DESC_DESC",
    "D01", "D02", "D04"
)

/** Códigos de desconto conhecidos. A 8.2b.2 traz a lista completa do recibo. */
private val CODIGOS_DESCONTO = setOf("D01", "D02", "D04")

/** Jornada diária normal (8h) e mês comercial usado nos proporcionais. */
private const val MINUTOS_JORNADA_DIA = 8 * 60
private const val DIAS_MES_COMERCIAL = 30L

/** Taxas em basis points: 11% de Segurança Social e 1% de sindicato. */
private const val TAXA_SEGURANCA_SOCIAL_BPS = 1_100L
private const val TAXA_SINDICATO_BPS = 100L

fun estimarRecibo(ctx: ContextoEstimativa): EstimativaRecibo {
    val anoMes = "%04d-%02d".format(ctx.anoMes.year, ctx.anoMes.monthValue)
    val catalogo = ctx.rubricas.sortedBy { it.ordem }
    val porCodigo = ctx.rubricas.associateBy { it.codigo }

    // Feriados do mês estimado (Fase 10): nacionais (fixos + móveis, calculados do ano) e o
    // municipal do contrato, mais os que o chamador marcar em ctx.feriados. É este conjunto
    // que decide se as horas suplementares de um dia caem em HSUP_*_FER.
    val feriados = buildSet {
        addAll(feriadosNacionais(ctx.anoMes.year))
        feriadoMunicipal(ctx.anoMes.year, ctx.municipioFeriadoDia, ctx.municipioFeriadoMes)?.let { add(it) }
        addAll(ctx.feriados)
    }

    val reaisDoMes = ctx.diasReais.filter { pertenceAoMes(it.data, ctx.anoMes) }
    val projecaoDoMes = ctx.projecao.filter { pertenceAoMes(it.epochDay, ctx.anoMes) }

    // Um DiaReal de origem PDF dentro de uma ausência (férias, baixa, feriado) representa
    // o horário planeado que a empresa imprime no PDF, não trabalho efetivo. Só conta
    // se a origem for MANUAL — nesse caso o utilizador disse explicitamente "trabalhei".
    val reaisEfetivos = reaisDoMes.filter { dia ->
        dia.origem == "MANUAL" || ctx.ausencias.none { a ->
            dia.data >= a.dataInicio && dia.data <= a.dataFim
        }
    }

    // Fronteira: mês sem dias reais e sem projeção não se estima.
    if (reaisDoMes.isEmpty() && projecaoDoMes.isEmpty()) {
        return EstimativaRecibo(
            anoMes = anoMes,
            rubricas = catalogo.map { r ->
                RubricaEstimada(r.id, r.codigo, r.nome, 0L, naturezaDe(r.codigo))
            },
            totalAbonos = 0L,
            totalDescontos = 0L,
            liquido = 0L
        )
    }

    val tipos = ctx.tiposTurno.associateBy { it.id }
    val janela = janelaNoturna(ctx.contrato.dataAdmissao ?: dataAdmissaoPadrao())
    val contexto = ContextoCalculo(
        vencimentoBaseMil = ctx.parametrosCCT.vencimentoBaseMil,
        horarioSemanalH = ctx.contrato.horarioSemanalH,
        janelaNoturna = janela,
        diasUteisMes = 0,
        numDependentes = ctx.contrato.numeroDependentes
    )
    val valorHora = valorHoraMil(contexto).toLong()

    val diasTrabalhados = reaisEfetivos.filter { ehTrabalho(it, tipos) }
    val diasUteisMes = if (projecaoDoMes.isNotEmpty()) {
        projecaoDoMes.count { ehTrabalhoProjetado(it, tipos) }
    } else {
        diasTrabalhados.size
    }

    var minutosNoturnos = 0L
    var minSupDn = 0L
    var minSupNt = 0L
    var minSupDnFer = 0L
    var minSupNtFer = 0L
    var minSupDnDesc = 0L
    var minSupNtDesc = 0L

    for (dia in diasTrabalhados) {
        minutosNoturnos += minutosNaJanela(dia.inicioMin, dia.fimMin, janela)

        val extra = duracaoMinutos(dia.inicioMin, dia.fimMin, dia.pausaMin) - MINUTOS_JORNADA_DIA
        if (extra <= 0) continue

        val noturno = minutosNaJanela(dia.inicioMin + MINUTOS_JORNADA_DIA, dia.fimMin, janela)
        val diurno = extra - noturno
        when {
            dia.data in feriados -> {
                minSupDnFer += diurno
                minSupNtFer += noturno
            }
            ehDescanso(dia, projecaoDoMes, tipos) -> {
                minSupDnDesc += diurno
                minSupNtDesc += noturno
            }
            else -> {
                minSupDn += diurno
                minSupNt += noturno
            }
        }
    }

    // Feriado e folga de escala trabalhados (8.5): a jornada de um desses dias vale 100% em
    // feriado (DESC_FER) e 200% em folga de escala (DESC_DESC) — mas só até às 8h de jornada;
    // o que passar disso é suplementar e sai pelas HSUP_*_FER / HSUP_*_DESC acima, que olham
    // para o mesmo dia. Um dia que seja feriado E folga paga como feriado (100%), porque o
    // feriado é o que a CCT protege primeiro.
    // Base: os dias reais efetivos, não os "trabalhados" — um dia de folga pode chegar aqui
    // com um tipo de categoria FOLGA, que ehTrabalho() descarta.
    var descFerMil = 0L
    var descDescMil = 0L
    for (dia in reaisEfetivos) {
        val minutosDoDia = duracaoMinutos(dia.inicioMin, dia.fimMin, dia.pausaMin)
        if (minutosDoDia <= 0) continue
        val minutosDeJornada = minOf(minutosDoDia, MINUTOS_JORNADA_DIA).toLong()
        if (dia.data in feriados) {
            // min(8h, horas no dia) × valorHora × 1,0
            descFerMil += dividirArredondando(minutosDeJornada * valorHora * 1L, 60L)
        } else if (ehDescanso(dia, projecaoDoMes, tipos)) {
            // min(8h, horas no dia) × valorHora × 2,0
            descDescMil += dividirArredondando(minutosDeJornada * valorHora * 2L, 60L)
        }
    }

    val venc = ctx.parametrosCCT.vencimentoBaseMil.toLong()

    // Subsídios (8.3): regra fixa do recibo, que não olha aos DiaReal. O que os corta são
    // os dias úteis — segunda a sexta — cobertos por ausências registadas, e só esses:
    //     SUP_ALIM = (dias úteis do mês − dias úteis de ausência) × valor/dia
    //     SUP_TRAN = valor/mês × (30 − dias úteis de ausência) ÷ 30
    // Os "dias úteis do mês" são os do cabeçalho do recibo (N.º Dias Úteis): a projeção,
    // que é o mesmo número que o ViewModel grava em ReciboMes.numDiasUteis. Quantos dias
    // se trabalhou não entra nesta conta (num mês sem ausências o subsídio é o do mês).
    val diasUteisAusencia = diasUteisDeAusencia(ctx.ausencias, ctx.anoMes)
    val subAlim = (diasUteisMes - diasUteisAusencia).coerceAtLeast(0).toLong() *
        ctx.parametrosCCT.subAlimentacaoDiaMil
    val subTran = dividirArredondando(
        ctx.parametrosCCT.subTransporteMesMil.toLong() *
            (DIAS_MES_COMERCIAL - diasUteisAusencia).coerceAtLeast(0L),
        DIAS_MES_COMERCIAL
    )

    val valores = linkedMapOf(
        "VENC" to venc,
        "HNOT" to valorMinutos(minutosNoturnos, valorHora, TipoHora.NOTURNA),
        "HSUP_DN" to valorMinutos(minSupDn, valorHora, TipoHora.SUP_DIURNO_NORMAL),
        "HSUP_NT" to valorMinutos(minSupNt, valorHora, TipoHora.SUP_NOTURNO_NORMAL),
        "HSUP_DN_FER" to valorMinutos(minSupDnFer, valorHora, TipoHora.SUP_DIURNO_FERIADO),
        "HSUP_NT_FER" to valorMinutos(minSupNtFer, valorHora, TipoHora.SUP_NOTURNO_FERIADO),
        "HSUP_DN_DESC" to valorMinutos(minSupDnDesc, valorHora, TipoHora.SUP_DIURNO_DESCANSO),
        "HSUP_NT_DESC" to valorMinutos(minSupNtDesc, valorHora, TipoHora.SUP_NOTURNO_DESCANSO),
        "SUP_ALIM" to subAlim,
        "SUP_TRAN" to subTran,
        "DESC_FER" to descFerMil,
        "DESC_DESC" to descDescMil
    )

    // Bases de incidência: só as rubricas estimadas que incidem (o cartão de refeição,
    // SUP_ALIM, não incide SS nem IRS — é o que torna detetável o erro de agosto de 2026).
    fun incide(codigo: String, campo: (Rubrica) -> Boolean): Boolean =
        porCodigo[codigo]?.let(campo) ?: false

    val baseSS = valores.filterKeys { incide(it) { r -> r.incideSS } }.values.sum()
    val baseIRS = valores.filterKeys { incide(it) { r -> r.incideIRS } }.values.sum()

    valores["D01"] = dividirArredondando(baseSS * TAXA_SEGURANCA_SOCIAL_BPS, 10_000L)
    valores["D02"] = calcularIRS(
        baseTributavel = baseIRS,
        tabelaNumero = selecionarTabela(
            estadoCivil = ctx.contrato.estadoCivil.name,
            titulares = ctx.contrato.titulares,
            numeroDependentes = ctx.contrato.numeroDependentes,
            pessoaComDeficiencia = false
        ),
        categoria = CATEGORIA_TRABALHO,
        numDependentes = ctx.contrato.numeroDependentes,
        ano = ctx.anoMes.year,
        regiao = ctx.contrato.regiao,
        tabelas = ctx.escaloesIRS
    )
    // Decisão do utilizador (2026-09-16): o sindicato (1%) incide SÓ sobre o VENC — não
    // sobre as horas suplementares nem sobre o subsídio de transporte. Por isso esta conta
    // não passa pelas bases de incidência: o flag incideSindicato do catálogo fica por usar
    // aqui, ao contrário de incideSS e incideIRS acima. Não "corrigir" isto para baseSS.
    valores["D04"] = dividirArredondando(venc * TAXA_SINDICATO_BPS, 10_000L)

    val estimadas = catalogo.map { r ->
        RubricaEstimada(
            rubricaId = r.id,
            codigo = r.codigo,
            nome = r.nome,
            valorMil = if (r.codigo in CODIGOS_ESTIMADOS) valores[r.codigo] ?: 0L else 0L,
            natureza = naturezaDe(r.codigo)
        )
    }
    val abonos = estimadas.filter { it.natureza == NaturezaRubrica.ABONO }.sumOf { it.valorMil }
    val descontos = estimadas.filter { it.natureza == NaturezaRubrica.DESCONTO }.sumOf { it.valorMil }

    return EstimativaRecibo(
        anoMes = anoMes,
        rubricas = estimadas,
        totalAbonos = abonos,
        totalDescontos = descontos,
        liquido = abonos - descontos
    )
}

/**
 * Quantos dias úteis — segunda a sexta — do mês estão cobertos por ausências. É a união
 * de todas as ausências (períodos sobrepostos contam uma vez só) e só os dias deste mês
 * entram: uma ausência que atravessa o mês corta apenas a parte que lhe pertence.
 */
private fun diasUteisDeAusencia(ausencias: List<Ausencia>, anoMes: YearMonth): Int {
    val dias = mutableSetOf<Long>()
    val primeiroDoMes = anoMes.atDay(1).toEpochDay()
    val ultimoDoMes = anoMes.atEndOfMonth().toEpochDay()
    for (ausencia in ausencias) {
        val fim = minOf(ausencia.dataFim, ultimoDoMes)
        var dia = maxOf(ausencia.dataInicio, primeiroDoMes)
        while (dia <= fim) {
            if (LocalDate.ofEpochDay(dia).dayOfWeek.value <= 5) dias.add(dia)
            dia++
        }
    }
    return dias.size
}

private fun pertenceAoMes(epochDay: Long, anoMes: YearMonth): Boolean =
    YearMonth.from(LocalDate.ofEpochDay(epochDay)) == anoMes

private fun naturezaDe(codigo: String): NaturezaRubrica =
    if (codigo in CODIGOS_DESCONTO) NaturezaRubrica.DESCONTO else NaturezaRubrica.ABONO

/** Sem data de admissão assume-se a janela noturna moderna (21h–06h). */
private fun dataAdmissaoPadrao(): Long = LocalDate.of(2004, 7, 15).toEpochDay()

/** Valor de N minutos a um multiplicador da CCT, em unidades de 1/10000 €. */
private fun valorMinutos(minutos: Long, valorHora: Long, tipo: TipoHora): Long =
    if (minutos <= 0L) 0L
    else dividirArredondando(
        minutos * valorHora * tipo.multiplicadorNumerador,
        60L * tipo.multiplicadorDenominador
    )

/** Minutos de um turno dentro da janela noturna (a janela atravessa a meia-noite). */
private fun minutosNaJanela(inicioMin: Int, fimMin: Int, janela: Pair<Int, Int>): Int {
    val (ini, fim) = janela
    val duracao = Math.floorMod(fimMin - inicioMin, MINUTOS_POR_DIA)
    var dentro = 0
    for (m in 0 until duracao) {
        val minutoDoDia = Math.floorMod(inicioMin + m, MINUTOS_POR_DIA)
        val naJanela = if (ini <= fim) minutoDoDia in ini until fim
        else minutoDoDia >= ini || minutoDoDia < fim
        if (naJanela) dentro++
    }
    return dentro
}

private fun ehTrabalho(dia: DiaReal, tipos: Map<Long, TipoTurno>): Boolean {
    val tipo = dia.tipoTurnoId?.let { tipos[it] }
    return if (tipo != null) {
        tipo.categoria == CategoriaTurno.TRABALHO
    } else {
        duracaoMinutos(dia.inicioMin, dia.fimMin, dia.pausaMin) > 0
    }
}

private fun ehTrabalhoProjetado(dia: DiaProjetado, tipos: Map<Long, TipoTurno>): Boolean {
    val tipo = dia.tipoTurnoId?.let { tipos[it] } ?: return false
    return tipo.categoria == CategoriaTurno.TRABALHO
}

/** Dia de descanso: a projeção dizia folga e o dia acabou trabalhado. */
private fun ehDescanso(
    dia: DiaReal,
    projecaoDoMes: List<DiaProjetado>,
    tipos: Map<Long, TipoTurno>
): Boolean {
    val projetado = projecaoDoMes.firstOrNull { it.epochDay == dia.data } ?: return false
    val tipo = projetado.tipoTurnoId?.let { tipos[it] } ?: return false
    return tipo.categoria == CategoriaTurno.FOLGA
}
