package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.EstadoCivil
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
    val escaloesIRS: List<EscalaoIRS>,
    /**
     * Catálogo de tipos de turno — só é preciso para saber se um dia com horas
     * suplementares é dia de descanso. Sem catálogo, assume-se dia normal.
     */
    val tiposTurno: List<TipoTurno> = emptyList()
)

/** Códigos que o estimador calcula. Tudo o resto sai com 0 (introduzido à mão). */
private val CODIGOS_ESTIMADOS = setOf(
    "VENC", "SUP_ALIM", "SUP_TRAN", "HNOT",
    "HSUP_DN", "HSUP_NT", "HSUP_DN_FER", "HSUP_NT_FER", "HSUP_DN_DESC", "HSUP_NT_DESC",
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

    val reaisDoMes = ctx.diasReais.filter { pertenceAoMes(it.data, ctx.anoMes) }
    val projecaoDoMes = ctx.projecao.filter { pertenceAoMes(it.epochDay, ctx.anoMes) }

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

    val diasTrabalhados = reaisDoMes.filter { ehTrabalho(it, tipos) }
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
            dia.data in ctx.feriados -> {
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

    val venc = ctx.parametrosCCT.vencimentoBaseMil.toLong()
    val subAlim = diasTrabalhados.size.toLong() * ctx.parametrosCCT.subAlimentacaoDiaMil
    val subTran = if (diasUteisMes == 0 || diasTrabalhados.size >= diasUteisMes) {
        // Mês completo: valor integral. Com faltas: proporcional ao mês comercial.
        ctx.parametrosCCT.subTransporteMesMil.toLong()
    } else {
        dividirArredondando(
            ctx.parametrosCCT.subTransporteMesMil.toLong() * diasTrabalhados.size,
            DIAS_MES_COMERCIAL
        )
    }

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
        "SUP_TRAN" to subTran
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
        tabelaNumero = selecionarTabela(perfilDoContrato(ctx.contrato), pessoaDeficiente = false),
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

/** Perfil das tabelas I a VII a partir do contrato (o contrato ainda não tem deficiência). */
private fun perfilDoContrato(contrato: ContratoUtilizador): String {
    val casado = contrato.estadoCivil == EstadoCivil.CASADO
    return when {
        casado && contrato.titulares >= 2 -> PERFIL_CASADO_2_TITULARES
        casado -> PERFIL_CASADO_1_TITULAR
        contrato.numeroDependentes > 0 -> PERFIL_NAO_CASADO_COM_DEP
        else -> PERFIL_NAO_CASADO_SEM_DEP
    }
}
