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

/**
 * Natureza de uma rubrica do recibo.
 *
 * [INFORMATIVA] é a linha que se mostra mas não paga nem desconta — hoje só as ausências (horas
 * em falta). Não entra em abonos nem em descontos, e não mexe no líquido.
 */
enum class NaturezaRubrica { ABONO, DESCONTO, INFORMATIVA }

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
    val liquido: Long,
    /**
     * v2.2.0: o escalão e a taxa efetiva de IRS que produziram o D02 estimado (ver [ResultadoIRS]),
     * de onde a conferência do recibo tira o "IRS (4,31%)" do título da rubrica. `null` quando o mês
     * não é estimado — sem dias reais nem projeção não há base, logo não há taxa a anunciar.
     *
     * É o detalhe da **base mensal**: a retenção autónoma dos subsídios soma-se ao D02 sem mexer no
     * escalão nem na taxa aqui guardados — o valor dela viaja em
     * [ResultadoIRS.irsAutonomoSubsidiosMil], para a conferência poder explicar a diferença.
     */
    val detalheIrs: ResultadoIRS? = null
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
    val ausencias: List<Ausencia> = emptyList(),
    /**
     * Desfasamento entre o mês trabalhado e o mês pago para as rubricas variáveis.
     * 0 = mesmo mês (padrão). 1 = mês anterior (ex.: ICTS — SAC, R14, feriados e
     * suplementares refletem o mês anterior). O VENC não depende disto.
     */
    val desfasamentoPagamentoVariaveis: Int = 0,
    /**
     * Complemento de baixa (rubrica COMP_BAIXA, código R51 do recibo) do mês, em 1/10000 €.
     * Não se estima: vem do recibo — importado do PDF ou introduzido à mão — e por isso tem
     * valor por omissão 0 (mês sem complemento). É um abono tributado: entra na base de SS e na
     * de IRS quando o catálogo o disser (ver [estimarRecibo]), nunca na do sindicato, e é
     * espelhado em [RubricaEstimada.valorMil] para o total de abonos estimado fechar.
     */
    val complementoBaixaMil: Long = 0L,
    /**
     * Subsídio de Natal pago no mês, em 1/10000 €. **O valor do recibo prevalece**: quando vem
     * preenchido (importado do PDF ou editado) é esse que é tributado e comparado. Sem valor — 0,
     * o omisso — o motor estima-o em dezembro pelo tempo de serviço do ano: vencimento base, data
     * de admissão e dias de baixa (ver [calcularSubsidioProporcional]). A partir do Despacho
     * n.º 12523-A/2026 tem retenção autónoma — ver [estimarRecibo]: o IRS é calculado sobre este
     * valor (e não sobre a base mensal), somado ao D02 e reduzido a 50 % quando o pagamento cai em
     * novembro de 2026 no Continente. Entra na base de SS segundo o catálogo.
     */
    val natalMil: Long = 0L,
    /**
     * Subsídio de férias pago no mês, em 1/10000 €. Mesma mecânica de [natalMil]: o valor do recibo
     * prevalece e, sem ele, o motor estima-o quando a regra de visibilidade das férias o mostra
     * ([temFeriasNoMesOuSeguinte]). Desde 2026-11-21 é matéria coletável — o catálogo diz SS e IRS
     * —, por isso entra na base de SS e tem retenção de IRS AUTÓNOMA somada ao D02, reduzida a
     * 50 % quando o pagamento cai em novembro de 2026 no Continente (Despacho n.º 12523-A/2026).
     */
    val feriasMil: Long = 0L,
    /**
     * O subsídio de férias deste ano civil **já pago** nos meses anteriores, em 1/10000 € — a soma
     * do `valorReal` da rubrica FERIAS desses meses (ver `totalFeriasJaPagas` no `ReciboViewModel`).
     *
     * O subsídio é um valor fixo anual: paga-se todo de uma vez ou em duas metades (50 % + 50 %), e
     * há empresas que o liquidam em duodécimos. Abaixo de **metade do direito do ano** o que se
     * estima é o que **falta receber** (`direito até ao fim do mês − feriasJaPagoMil`, nunca abaixo
     * de zero) — são as prestações e os duodécimos. A partir de metade o subsídio considera-se
     * liquidado ([subsidioFeriasLiquidado]) e o estimado fica a 0,00: a empresa não anda a acumular
     * um "direito" que cresce com os meses para pagar um remanescente em outubro. O valor do recibo
     * deste mês prevalece sempre e não é tocado por esta conta ([feriasMil]).
     */
    val feriasJaPagoMil: Long = 0L,
    /**
     * Data de admissão, para o proporcional dos subsídios (ver [calcularSubsidioProporcional]).
     * `null` quando não se conhece: nesse caso não se estima subsídio nenhum (ficam a 0) — melhor
     * não inventar do que assumir um ano inteiro de serviço. As ausências do ano entram por
     * [ausencias] e pelo catálogo de [tiposTurno]: é a baixa que corta a proporção.
     */
    val dataAdmissao: LocalDate? = null
)

/** Códigos que o estimador calcula. Tudo o resto sai com 0 (introduzido à mão). */
private val CODIGOS_ESTIMADOS = setOf(
    "VENC", "SUP_ALIM", "SUP_TRAN", "SUP_FUNCAO", "HNOT",
    "HSUP_DN", "HSUP_NT", "HSUP_DN_FER", "HSUP_NT_FER", "HSUP_DN_DESC", "HSUP_NT_DESC",
    "DESC_FER", "DESC_DESC", "ATRASO",
    CODIGO_SUBSIDIO_NATAL, CODIGO_SUBSIDIO_FERIAS,
    "D01", "D02", "D04"
)

/** Códigos de desconto conhecidos. A 8.2b.2 traz a lista completa do recibo. */
private val CODIGOS_DESCONTO = setOf("D01", "D02", "D04")

/**
 * Rubrica das horas em falta (atrasos, saídas mais cedo). É INFORMATIVA: o valor é calculado e
 * mostrado, mas não entra nos descontos — o corte faz-se uma única vez, dentro do VENC (ver
 * [estimarRecibo]). Fica no catálogo — e editável no ecrã — para o utilizador ver as horas em
 * falta do mês.
 */
private const val CODIGO_AUSENCIAS = "ATRASO"

/**
 * 2026-10-21: rubrica dos descontos que o recibo traz e a app não conhece (o D05 da Fundação).
 * É de DESCONTO, como as D0x — sem isto o valor entrava nos abonos e somava ao líquido.
 */
private const val CODIGO_OUTROS_DESC = "OUTROS_DESC"

/**
 * Complemento de baixa (R51). Não é estimado — vem do recibo — mas é matéria coletável: usando
 * [ContextoEstimativa.complementoBaixaMil] entra nas bases de SS e IRS, se o catálogo o disser, e
 * é espelhado no abono da rubrica para o líquido estimado fechar (ver o fim de [estimarRecibo]).
 */
private const val CODIGO_COMPLEMENTO_BAIXA = "COMP_BAIXA"

/**
 * Subsídio de Natal. Estimado em dezembro (ou espelhado do recibo, que prevalece), com retenção
 * própria desde o Despacho n.º 12523-A/2026. O valor de [ContextoEstimativa.natalMil] — ou, sem
 * ele, o proporcional calculado por `calcularSubsidioProporcional` — é tributado de forma
 * AUTÓNOMA (ver [estimarRecibo]): entra na base de SS como os outros abonos, mas não na base de
 * IRS mensal — o IRS do Natal é calculado à parte, sobre o próprio subsídio, e somado ao D02.
 */
private const val CODIGO_SUBSIDIO_NATAL = "NATAL"

/**
 * Subsídio de férias (R30/R97). Mesma mecânica do [CODIGO_SUBSIDIO_NATAL]: o valor do recibo
 * prevalece e, sem ele, é estimado quando a linha é mostrada (férias no mês ou no seguinte).
 * Desde a correção da semente em 2026-11-21 é matéria coletável em SS e IRS: entra na base de SS e
 * tem retenção AUTÓNOMA somada ao D02 (ver [estimarRecibo]).
 */
private const val CODIGO_SUBSIDIO_FERIAS = "FERIAS"

/**
 * Mês em que o subsídio de Natal é estimado: dezembro, o mês em que se paga. A antecipação de
 * novembro de 2026 (Despacho n.º 12523-A/2026) chega pelo valor do recibo — [ContextoEstimativa
 * .natalMil] —, que prevalece sobre a estimativa.
 */
private const val MES_PAGAMENTO_NATAL = 12

/**
 * O fim do ano civil. Passado como mês de pagamento a [calcularSubsidioProporcional] dá o direito
 * ao subsídio do **ano inteiro** — o valor fixo anual que decide se as férias já estão liquidadas
 * ([subsidioFeriasLiquidado]).
 */
private const val MES_FECHO_DO_ANO = 12

/**
 * Subsídios anuais. Têm retenção de IRS autónoma, por isso ficam FORA da base mensal (somá-los lá
 * tributava-os duas vezes, uma na tabela do mês e outra na retenção própria).
 */
private val CODIGOS_SUBSIDIOS = setOf(CODIGO_SUBSIDIO_NATAL, CODIGO_SUBSIDIO_FERIAS)


/** Jornada diária normal (8h) e mês comercial usado nos proporcionais. */
private const val MINUTOS_JORNADA_DIA = 8 * 60
private const val DIAS_MES_COMERCIAL = 30L

/**
 * Base de dias do subsídio de função (13a). Regra pedida pelo utilizador: 22 dias, independente
 * dos 30 do subsídio de transporte — não "corrigir" para 30 sem nova instrução.
 */
private const val DIAS_BASE_SUBFUNCAO = 22L

/** Taxas em basis points: 11% de Segurança Social e 1% de sindicato. */
private const val TAXA_SEGURANCA_SOCIAL_BPS = 1_100L
private const val TAXA_SINDICATO_BPS = 100L

fun estimarRecibo(ctx: ContextoEstimativa): EstimativaRecibo {
    val anoMes = "%04d-%02d".format(ctx.anoMes.year, ctx.anoMes.monthValue)
    val catalogo = ctx.rubricas.sortedBy { it.ordem }
    val porCodigo = ctx.rubricas.associateBy { it.codigo }

    // Mês de origem das rubricas variáveis. Com desfasamento 0, é o próprio mês; com
    // desfasamento 1, é o mês anterior (o VENC fica sempre no mês corrente).
    val anoMesVariaveis = ctx.anoMes.minusMonths(ctx.desfasamentoPagamentoVariaveis.toLong())

    // Feriados do mês das variáveis (Fase 10 + desfasamento): nacionais (fixos + móveis,
    // calculados do ano) e o municipal do contrato, mais os que o chamador marcar em
    // ctx.feriados. É este conjunto que decide se as horas suplementares de um dia caem
    // em HSUP_*_FER.
    val feriados = buildSet {
        addAll(feriadosNacionais(anoMesVariaveis.year))
        feriadoMunicipal(anoMesVariaveis.year, ctx.municipioFeriadoDia, ctx.municipioFeriadoMes)?.let { add(it) }
        addAll(ctx.feriados)
    }

    val reaisDoMes = ctx.diasReais.filter { pertenceAoMes(it.data, anoMesVariaveis) }
    val projecaoDoMes = ctx.projecao.filter { pertenceAoMes(it.epochDay, anoMesVariaveis) }

    // Um DiaReal de origem PDF dentro de uma ausência (férias, baixa, feriado) representa
    // o horário planeado que a empresa imprime no PDF, não trabalho efetivo. Só conta
    // se a origem for MANUAL — nesse caso o utilizador disse explicitamente "trabalhei".
    val reaisEfetivos = reaisDoMes.filter { dia ->
        dia.origem == "MANUAL" || !dentroDeAusencia(dia.data, ctx.ausencias)
    }

    // Fronteira: mês sem dias reais e sem projeção não se estima. O complemento de baixa também
    // não é espelhado aqui: sem dias não há bases, logo não há D01/D02 a descontar e o líquido
    // sairia inflado (o valor real continua na linha do recibo, na coluna Real).
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
        val realDoDia = duracaoMinutos(dia.inicioMin, dia.fimMin, dia.pausaMin)
        // HNOT: só a janela noturna dentro da jornada prevista. As horas extra noturnas (para
        // lá da jornada) já são pagas via HSUP_NT*, e é a mesma `noturno` que as classifica lá.
        // A janela é contada em minutos do dia, por isso aguenta turnos que atravessam a
        // meia-noite — somar a jornada ao início não dá uma hora do mesmo dia.
        val jornadaDoDia = minOf(realDoDia, jornadaPrevistaDoDia(dia, projecaoDoMes, tipos))
        minutosNoturnos += minutosNaJanela(dia.inicioMin, dia.inicioMin + jornadaDoDia, janela)

        // Horas extra (2026-10-19): só há suplemento se o dia render mais do que a jornada
        // prevista para ESSE dia. Um dia coberto por ausência registada (férias, baixa, folga)
        // não foi trabalhado, mesmo que haja um registo real por cima: não paga suplemento.
        // O que fica dentro da jornada desse dia é atraso (rubrica informativa), não suplemento —
        // e um dia curto não se compensa com um dia longo.
        if (dentroDeAusencia(dia.data, ctx.ausencias)) continue
        val extra = realDoDia - jornadaDoDia
        if (extra <= 0) continue

        // A janela vai de início + jornada até ao fim, e a pausa fica dentro desse intervalo: com
        // pausa, a janela mede mais minutos do que o extra real. O noturno nunca passa o extra
        // (que já vem sem a pausa), e por isso o diurno nunca fica negativo.
        val noturno = minOf(minutosNaJanela(dia.inicioMin + jornadaDoDia, dia.fimMin, janela), extra)
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

    // Dias úteis — segunda a sexta — do mês das variáveis cobertos por ausências: é o corte dos
    // subsídios (8.3). Serve também de recurso ao corte do vencimento quando não há projeção.
    val diasUteisAusencia = diasUteisDeAusencia(ctx.ausencias, anoMesVariaveis)

    // Mês inteiro coberto por ausência (todos os dias do calendário). Nesse caso não sobra dia
    // nenhum de trabalho nem de subsídio: a conta de 8.3 corta dias úteis de um mês comercial de
    // 30, e num mês de 31 dias isso deixava 9 dias de transporte por cortar. Os recibos reais de
    // Março de 2026 (mês inteiro de baixa) pagam R14 = 0,00 e anulam o cartão de refeição, e o
    // vencimento não tem dia a que se agarrar. Nenhuma ausência parcial entra neste ramo.
    val mesInteiroAusente = diasCobertosDeAusencia(
        ctx.ausencias,
        anoMesVariaveis,
        apenasDiasUteis = false
    ).size >= anoMesVariaveis.lengthOfMonth()
    val diasAusenciaSubsidios = if (mesInteiroAusente) {
        maxOf(DIAS_MES_COMERCIAL.toInt(), diasUteisMes)
    } else {
        diasUteisAusencia
    }

    // Projeção do mês do recibo (M), não do mês das variáveis: é a régua dos atrasos, mais abaixo.
    val projecaoDoMesAtual = ctx.projecao.filter { pertenceAoMes(it.epochDay, ctx.anoMes) }

    // Subsídios (8.3): regra fixa do recibo, que não olha aos DiaReal. O que os corta são
    // os dias úteis — segunda a sexta — cobertos por ausências registadas, e só esses:
    //     SUP_ALIM = (dias úteis do mês − dias úteis de ausência) × valor/dia
    //     SUP_TRAN = valor/mês × (30 − dias úteis de ausência) ÷ 30
    // "Dias úteis do mês" aqui é o mês das variáveis (M com desfasamento 0, M-1 com 1),
    // contados a partir da projeção. NÃO é o mesmo número que o ViewModel grava em
    // ReciboMes.numDiasUteis (esse é do mês do recibo, M). Quantos dias se trabalhou
    // não entra nesta conta (num mês sem ausências o subsídio é o do mês).
    // `diasAusenciaSubsidios` só difere de `diasUteisAusencia` no mês inteiro coberto (ver acima).
    val subAlim = (diasUteisMes - diasAusenciaSubsidios).coerceAtLeast(0).toLong() *
        ctx.parametrosCCT.subAlimentacaoDiaMil
    val subTran = dividirArredondando(
        ctx.parametrosCCT.subTransporteMesMil.toLong() *
            (DIAS_MES_COMERCIAL - diasAusenciaSubsidios).coerceAtLeast(0L),
        DIAS_MES_COMERCIAL
    )
    // Subsídio de função (13a): só as categorias que o têm (hoje o Team Leader, 43,00 €/mês).
    // Mensal × (22 − dias úteis de ausência) ÷ 22, com o mesmo corte por ausências do SUP_ALIM.
    // Zero para as restantes categorias — e nesse caso nem se faz a conta.
    val supFuncaoMil = if (ctx.parametrosCCT.subsidioFuncaoMil > 0L) {
        dividirArredondando(
            ctx.parametrosCCT.subsidioFuncaoMil * (DIAS_BASE_SUBFUNCAO - diasAusenciaSubsidios).coerceAtLeast(0L),
            DIAS_BASE_SUBFUNCAO
        )
    } else {
        0L
    }

    // Atrasos: minutos em falta por dia com turno projetado de trabalho e registo real
    // MANUAL, fora de ausencias. Cada minuto vale o valor/minuto (0,1095 EUR).
    // A régua é a do dia: no modo PDF são as horas do turno impresso (minutosPrevistos), não a
    // duração do tipo derivado — senão um turno de 12h medido contra um tipo de 8h inventava 4h
    // de atraso, tal como inventava 4h de suplemento quando era o contrário.
    fun minutosAtrasoDoDia(dia: DiaProjetado): Int {
        val tipo = dia.tipoTurnoId?.let { tipos[it] } ?: return 0
        if (tipo.categoria != CategoriaTurno.TRABALHO) return 0
        if (dentroDeAusencia(dia.epochDay, ctx.ausencias)) return 0
        val real = ctx.diasReais.firstOrNull { it.data == dia.epochDay && it.origem == "MANUAL" } ?: return 0
        val prevista = dia.minutosPrevistos?.takeIf { it > 0 }
            ?: duracaoMinutos(tipo.inicioMin, tipo.fimMin, tipo.pausaMin)
        val realMin = duracaoMinutos(real.inicioMin, real.fimMin, real.pausaMin)
        return (prevista - realMin).coerceAtLeast(0)
    }
    val minutosAtraso = projecaoDoMesAtual.sumOf { minutosAtrasoDoDia(it) }
    val atrasoMil = minutosAtraso.toLong() * valorMinutoMil(contexto).toLong()

    // VENC proporcional aos dias efetivos E líquido dos atrasos — regra do CCT validada em
    // 2026-10-19 e reposta em 2026-10-21 (a auditoria tinha-o fixado no valor base do contrato,
    // e o vencimento deixou de refletir a baixa):
    //   - as FÉRIAS contam como dias pagos: não cortam o VENC;
    //   - a BAIXA não conta: corta o VENC na proporção dos dias úteis que cobre;
    //   - o ATRASO sai UMA vez, do próprio VENC (mais abaixo); a rubrica ATRASO fica INFORMATIVA,
    //     a justificar o corte — não volta a sair nos descontos.
    // Devolver o vencimento base do contrato é regressão: os testes T8/T11/23/24/25 fixam-no.
    val diasUteisTotais = if (projecaoDoMesAtual.isNotEmpty()) {
        projecaoDoMesAtual.count { ehTrabalhoProjetado(it, tipos) }
    } else {
        ctx.diasReais.filter { pertenceAoMes(it.data, ctx.anoMes) && ehTrabalho(it, tipos) }.size
    }
    val diasUteisBaixa = if (projecaoDoMesAtual.isNotEmpty()) {
        projecaoDoMesAtual.count { dia ->
            ehTrabalhoProjetado(dia, tipos) && dentroDeBaixa(dia.epochDay, ctx.ausencias, tipos)
        }
    } else {
        ctx.diasReais.count { real ->
            pertenceAoMes(real.data, ctx.anoMes) && ehTrabalho(real, tipos) &&
                dentroDeBaixa(real.data, ctx.ausencias, tipos)
        }
    }
    val diasEfetivos = (diasUteisTotais - diasUteisBaixa).coerceAtLeast(0)
    // Sem dias nenhuns no mês não há proporção a fazer: fica o vencimento base do contrato.
    val vencProporcional = if (diasUteisTotais > 0) {
        dividirArredondando(
            ctx.parametrosCCT.vencimentoBaseMil.toLong() * diasEfetivos,
            diasUteisTotais.toLong()
        )
    } else {
        ctx.parametrosCCT.vencimentoBaseMil.toLong()
    }

    // O atraso é deduzido aqui, no VENC que o recibo mostra (que assim já vem líquido das horas
    // em falta). A rubrica ATRASO fica ao lado como justificação e é INFORMATIVA: se voltasse a
    // entrar nos descontos, o mesmo valor saía duas vezes do líquido.
    val vencFinal = (vencProporcional - atrasoMil).coerceAtLeast(0L)

    // Subsídios de Natal e de férias: proporcionais aos DIAS de serviço no ano civil, com os dias
    // de baixa descontados (ver `calcularSubsidioProporcional`) — um ano inteiro sem baixa vale o
    // vencimento base, e cinco meses de baixa cortam quase metade. Estimam-se no mês em que são
    // pagos: o Natal em dezembro, as férias quando a regra de visibilidade as mostra (o próprio mês
    // das férias ou o anterior). O valor do recibo prevalece: quem já importou o recibo sabe quanto
    // foi pago, e é esse valor que tem de ser tributado e comparado com o estimado. Sem data de
    // admissão não se estima — e sem catálogo de tipos também não se desconta baixa nenhuma
    // (`diasDeAusenciaQueNaoContam`): o catálogo é que diz que uma ausência é baixa.
    val categoriasPorTipo = ctx.tiposTurno.associate { it.id to it.categoria }
    val baseSubsidiosMil = ctx.parametrosCCT.vencimentoBaseMil.toLong()

    /**
     * O **direito** ao subsídio até ao fim do mês indicado (por omissão, o mês visível), sem olhar
     * a pagamentos nem à visibilidade: é o valor que o subsídio teria se fosse liquidado nesse mês.
     * Serve os dois subsídios (Natal e férias). Com [mesPagamento] = dezembro dá o direito do ano
     * inteiro — o "valor fixo anual" do subsídio, que decide se as férias já estão liquidadas
     * ([subsidioFeriasLiquidado]).
     */
    fun direitoDoSubsidio(mesPagamento: Int = ctx.anoMes.monthValue): Long {
        val admissao = ctx.dataAdmissao ?: return 0L
        return calcularSubsidioProporcional(
            baseMil = baseSubsidiosMil,
            dataAdmissao = admissao,
            ano = ctx.anoMes.year,
            mesPagamento = mesPagamento,
            ausencias = ctx.ausencias,
            tiposTurno = ctx.tiposTurno
        )
    }
    val valorNatal = ctx.natalMil.takeIf { it > 0L }
        ?: if (ctx.anoMes.monthValue == MES_PAGAMENTO_NATAL) direitoDoSubsidio() else 0L
    // Férias: o valor do recibo prevalece. Sem ele, estima-se o que **falta receber**:
    //   direito até ao fim do mês − já pago nos meses anteriores do ano  (nunca abaixo de zero)
    // com uma paragem antes: o subsídio é um valor FIXO ANUAL e quem já recebeu metade ou mais não
    // vai receber um "remanescente" no fim do ano ([subsidioFeriasLiquidado]). Sem ela, outubro de
    // 2026 — subsídio pago todo em julho, férias marcadas no mês — voltava a mostrar a diferença
    // entre o direito acumulado até outubro e o que já tinha sido pago: dinheiro que ninguém
    // recebe. Abaixo de metade a conta mantém-se: são as prestações e os duodécimos.
    // As férias que fazem aparecer a linha continuam a ser as marcadas no mês ou no seguinte.
    val valorFerias = ctx.feriasMil.takeIf { it > 0L }
        ?: run {
            val feriasLiquidadas = subsidioFeriasLiquidado(
                pagoNoAnoMil = ctx.feriasJaPagoMil,
                direitoAnoMil = direitoDoSubsidio(mesPagamento = MES_FECHO_DO_ANO)
            )
            val temFeriasNoMes = temFeriasNoMesOuSeguinte(
                ctx.ausencias,
                categoriasPorTipo,
                ctx.anoMes
            )
            if (temFeriasNoMes && !feriasLiquidadas) {
                (direitoDoSubsidio() - ctx.feriasJaPagoMil).coerceAtLeast(0L)
            } else {
                0L
            }
        }

    val valores = linkedMapOf(
        "VENC" to vencFinal,
        "HNOT" to valorMinutos(minutosNoturnos, valorHora, TipoHora.NOTURNA_ACRESCIMO),
        "HSUP_DN" to valorMinutos(minSupDn, valorHora, TipoHora.SUP_DIURNO_NORMAL),
        "HSUP_NT" to valorMinutos(minSupNt, valorHora, TipoHora.SUP_NOTURNO_NORMAL),
        "HSUP_DN_FER" to valorMinutos(minSupDnFer, valorHora, TipoHora.SUP_DIURNO_FERIADO),
        "HSUP_NT_FER" to valorMinutos(minSupNtFer, valorHora, TipoHora.SUP_NOTURNO_FERIADO),
        "HSUP_DN_DESC" to valorMinutos(minSupDnDesc, valorHora, TipoHora.SUP_DIURNO_DESCANSO),
        "HSUP_NT_DESC" to valorMinutos(minSupNtDesc, valorHora, TipoHora.SUP_NOTURNO_DESCANSO),
        "SUP_ALIM" to subAlim,
        "SUP_TRAN" to subTran,
        "SUP_FUNCAO" to supFuncaoMil,
        "DESC_FER" to descFerMil,
        "DESC_DESC" to descDescMil,
        "ATRASO" to atrasoMil,
        CODIGO_SUBSIDIO_NATAL to valorNatal,
        CODIGO_SUBSIDIO_FERIAS to valorFerias
    )

    // Bases de incidência: as rubricas estimadas que incidem (o cartão de refeição, SUP_ALIM, não
    // incide SS nem IRS — é o que torna detetável o erro de agosto de 2026), mais o complemento
    // de baixa. O ATRASO é informativo e não incide (incideSS e incideIRS = false): não mexe nas
    // bases.
    fun incide(codigo: String, campo: (Rubrica) -> Boolean): Boolean =
        porCodigo[codigo]?.let(campo) ?: false

    // O complemento de baixa não está em `valores` porque é MANUAL (vem do recibo, não se
    // estima), mas é tributado: o recibo real de maio/2026 desconta D01 = 31,51 € sobre 286,41 €
    // de complemento. Entra nas bases pela mão do catálogo (`incide`), não por um `if` fixo. No
    // IRS idem: entra na base e é a tabela que decide (naquele mês deu 0 por o valor ficar abaixo
    // do 1.º escalão — não por isenção). No sindicato não entra: o D04 incide só sobre o VENC.
    val complementoBaseSS =
        if (incide(CODIGO_COMPLEMENTO_BAIXA) { r -> r.incideSS }) ctx.complementoBaixaMil else 0L
    val complementoBaseIRS =
        if (incide(CODIGO_COMPLEMENTO_BAIXA) { r -> r.incideIRS }) ctx.complementoBaixaMil else 0L

    // Os subsídios pagos no mês (Natal e férias) são matéria coletável. A Segurança Social não
    // distingue retenções autónomas: cada um paga os 11 % no mês em que é pago, se o catálogo o
    // disser. A base de IRS mensal fica SEM eles de propósito — somá-los aqui tributava-os duas
    // vezes, uma na tabela mensal e outra na retenção autónoma logo abaixo. É por isso que a
    // filtragem de `valores` os exclui por código, além do `incide` do catálogo.
    val subsidiosDoMes = listOf(
        CODIGO_SUBSIDIO_NATAL to valorNatal,
        CODIGO_SUBSIDIO_FERIAS to valorFerias
    )
    val subsidiosBaseSS = subsidiosDoMes
        .filter { (codigo, _) -> incide(codigo) { r -> r.incideSS } }
        .sumOf { (_, valorMil) -> valorMil }

    val baseSS = valores
        .filterKeys { codigo -> codigo !in CODIGOS_SUBSIDIOS && incide(codigo) { r -> r.incideSS } }
        .values.sum() + complementoBaseSS + subsidiosBaseSS
    val baseIRS = valores
        .filterKeys { codigo -> codigo !in CODIGOS_SUBSIDIOS && incide(codigo) { r -> r.incideIRS } }
        .values.sum() + complementoBaseIRS

    valores["D01"] = dividirArredondando(baseSS * TAXA_SEGURANCA_SOCIAL_BPS, 10_000L)

    // Fase 20 — IRS Jovem: a percentagem é calculada antes do D02 porque a ordem das correções é
    // IRS Jovem → redução de novembro. [calcularIRSMensal] aplica as duas pela ordem certa: isenta
    // a retenção normal e só depois reduz a metade o que sobra (Despacho n.º 12523-A/2026). Sem
    // IRS Jovem aplicável (percentagem null) o resultado é a tabela normal, eventualmente
    // reduzida a 50 % em novembro de 2026 no Continente.
    val anoNascimento = ctx.contrato.anoNascimento
    val anoPrimeiroRendimento = ctx.contrato.anoPrimeiroRendimento
    val percentagemIrsJovem = if (
        ctx.contrato.aplicarIrsJovem && anoNascimento != null && anoPrimeiroRendimento != null
    ) {
        percentagemIsencaoIrsJovem(
            anoNascimento = anoNascimento,
            anoPrimeiroRendimento = anoPrimeiroRendimento,
            anoAtual = ctx.anoMes.year
        )
    } else null

    val tabelaNumero = selecionarTabela(
        estadoCivil = ctx.contrato.estadoCivil.name,
        titulares = ctx.contrato.titulares,
        numeroDependentes = ctx.contrato.numeroDependentes,
        pessoaComDeficiencia = false
    )

    // v2.2.0: a mesma conta do D02, mas com o escalão e a taxa à vista — é daqui que a conferência
    // do recibo tira o "Escalão 5 · Taxa 21,2%". A base de IRS e a tabela só existem aqui dentro;
    // recalculá-las no ViewModel duplicava as incidências do catálogo.
    val detalheIrs = calcularIRSMensalComDetalhe(
        baseTributavel = baseIRS,
        tabelaNumero = tabelaNumero,
        categoria = CATEGORIA_TRABALHO,
        numDependentes = ctx.contrato.numeroDependentes,
        ano = ctx.anoMes.year,
        regiao = ctx.contrato.regiao,
        tabelas = ctx.escaloesIRS,
        // O mês do PAGAMENTO manda: é o recibo de novembro que tem a redução, mesmo quando as
        // variáveis vêm do mês anterior (desfasamento ICTS).
        mes = ctx.anoMes.monthValue,
        percentagemIrsJovem = percentagemIrsJovem
    )
    valores["D02"] = detalheIrs.valorMil

    // Retenção autónoma dos subsídios (opção B da investigação, 2026-11-21): cada um tem base e
    // tabela próprias, nunca se somam à base mensal. Em novembro de 2026 no Continente a redução de
    // 50 % entra pela mesma função; em dezembro (ou fora do Continente) é a tabela normal. O IRS
    // Jovem não é aplicado aqui: a isenção tem um teto mensal e aplicá-la outra vez aos subsídios
    // duplicaria o benefício no mesmo mês.
    val irsAutonomoDosSubsidios = subsidiosDoMes
        .filter { (codigo, valorMil) -> valorMil > 0L && incide(codigo) { r -> r.incideIRS } }
        .sumOf { (_, valorMil) -> irsAutonomoDoSubsidio(valorMil, tabelaNumero, ctx) }
    // Soma ao D02: o recibo real traz uma única linha de IRS, já com a retenção dos subsídios. Uma
    // rubrica D02_NATAL/D02_FERIAS à parte obrigaria a mexer no catálogo e a comparar várias linhas
    // com uma só do lado do real; somando, a conferência é direta.
    if (irsAutonomoDosSubsidios > 0L) {
        valores["D02"] = (valores["D02"] ?: 0L) + irsAutonomoDosSubsidios
    }

    // Decisão do utilizador (2026-09-16): o sindicato (1%) incide SÓ sobre o VENC — não
    // sobre as horas suplementares nem sobre o subsídio de transporte. Por isso esta conta
    // não passa pelas bases de incidência: o flag incideSindicato do catálogo fica por usar
    // aqui, ao contrário de incideSS e incideIRS acima. Não "corrigir" isto para baseSS.
    // Fase 25: o desconto sindical só incide nos contratos sindicalizados (campo do contrato,
    // ligado pelo switch no ecrã). Desligado, a rubrica fica a 0 — a base não muda (o D04 sempre
    // incidiu só sobre o VENC); o que muda é haver ou não desconto.
    // 2026-10-21: a base é o VENC já proporcional aos dias efetivos e líquido dos atrasos (a
    // baixa corta-o; as férias não) — acompanha as restantes incidências, como antes da auditoria.
    valores["D04"] = if (ctx.contrato.sindicalizado) {
        dividirArredondando(vencFinal * TAXA_SINDICATO_BPS, 10_000L)
    } else {
        0L
    }

    // O complemento de baixa fica FORA de CODIGOS_ESTIMADOS (o motor não o calcula), mas o valor
    // que o recibo traz tem de aparecer na coluna Estimado: é um espelho do real, não uma conta.
    // Sem isto o abono ficava de fora do total e o líquido estimado saía negativo num mês de baixa
    // (maio/2026: 286,41 € de complemento contra 31,51 € de D01). Decisão do utilizador em
    // 2026-10-21 — o líquido tem de fechar. O Natal e o subsídio de férias já não precisam deste
    // ramo: são estimados (ou espelhados do recibo) e entram em `valores`.
    val estimadas = catalogo.map { r ->
        RubricaEstimada(
            rubricaId = r.id,
            codigo = r.codigo,
            nome = r.nome,
            valorMil = when {
                r.codigo in CODIGOS_ESTIMADOS -> valores[r.codigo] ?: 0L
                r.codigo == CODIGO_COMPLEMENTO_BAIXA -> ctx.complementoBaixaMil
                else -> 0L
            },
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
        liquido = abonos - descontos,
        // v2.2.0: o IRS autónomo dos subsídios já está somado ao D02 (`valores["D02"]`), mas não na
        // base mensal de onde saiu o detalhe. Levá-lo junto é o que permite à conferência dizer que
        // o valor da linha inclui esse IRS (ver notasIrs).
        detalheIrs = detalheIrs.copy(irsAutonomoSubsidiosMil = irsAutonomoDosSubsidios)
    )
}

/**
 * IRS de um subsídio pago no mês, retido de forma AUTÓNOMA: base e tabela próprias, nunca somado à
 * base mensal. Passa por [calcularIRSMensal] para herdar a redução de 50 % de novembro de 2026 no
 * Continente (Despacho n.º 12523-A/2026) sem repetir aqui essa regra. O IRS Jovem fica de fora de
 * propósito: a isenção tem um teto mensal e voltar a aplicá-la duplicava o benefício no mesmo mês.
 * Sem valor pago devolve 0.
 */
private fun irsAutonomoDoSubsidio(
    valorMil: Long,
    tabelaNumero: Int,
    ctx: ContextoEstimativa
): Long = if (valorMil <= 0L) {
    0L
} else {
    calcularIRSMensal(
        baseTributavel = valorMil,
        tabelaNumero = tabelaNumero,
        categoria = CATEGORIA_TRABALHO,
        numDependentes = ctx.contrato.numeroDependentes,
        ano = ctx.anoMes.year,
        regiao = ctx.contrato.regiao,
        tabelas = ctx.escaloesIRS,
        // O mês do PAGAMENTO manda, como no D02 mensal: é o recibo de novembro que tem a redução.
        mes = ctx.anoMes.monthValue
    )
}

/**
 * Dias do mês cobertos por ausências registadas — a união de todas as ausências (períodos
 * sobrepostos contam uma vez só), recortada ao mês: uma ausência que atravessa o mês conta apenas
 * a parte que lhe pertence.
 *
 * [apenasDiasUteis] limita a segunda a sexta (é o corte dos subsídios, 8.3); sem ele conta também
 * fins de semana, que é o que permite detetar um mês inteiro coberto por baixa.
 */
private fun diasCobertosDeAusencia(
    ausencias: List<Ausencia>,
    anoMes: YearMonth,
    apenasDiasUteis: Boolean = true
): Set<Long> {
    val dias = mutableSetOf<Long>()
    val primeiroDoMes = anoMes.atDay(1).toEpochDay()
    val ultimoDoMes = anoMes.atEndOfMonth().toEpochDay()
    for (ausencia in ausencias) {
        val fim = minOf(ausencia.dataFim, ultimoDoMes)
        var dia = maxOf(ausencia.dataInicio, primeiroDoMes)
        while (dia <= fim) {
            if (!apenasDiasUteis || LocalDate.ofEpochDay(dia).dayOfWeek.value <= 5) dias.add(dia)
            dia++
        }
    }
    return dias
}

/**
 * Quantos dias úteis — segunda a sexta — do mês estão cobertos por ausências. É o corte dos
 * subsídios (8.3).
 */
private fun diasUteisDeAusencia(ausencias: List<Ausencia>, anoMes: YearMonth): Int =
    diasCobertosDeAusencia(ausencias, anoMes).size

private fun pertenceAoMes(epochDay: Long, anoMes: YearMonth): Boolean =
    YearMonth.from(LocalDate.ofEpochDay(epochDay)) == anoMes

/** O dia cai dentro de alguma ausência registada? Datas inclusivas nas duas pontas. */
private fun dentroDeAusencia(epochDay: Long, ausencias: List<Ausencia>): Boolean =
    ausencias.any { epochDay >= it.dataInicio && epochDay <= it.dataFim }

/**
 * O dia cai dentro de uma ausência que CORTA o vencimento? Só a BAIXA (CDD) corta: as férias
 * (SDD) contam como dias efetivos/pagos e as folgas não são ausências registadas.
 *
 * A categoria vem do tipo de turno da ausência, não do dia: a [Ausencia] só guarda o
 * `tipoTurnoId`, e é o tipo (Férias = [CategoriaTurno.FERIAS], Baixa médica =
 * [CategoriaTurno.BAIXA]) que diz de que ausência se trata. Sem catálogo de tipos não se corta
 * nada — melhor manter o vencimento do que cortá-lo por um id que não se sabe o que é.
 */
private fun dentroDeBaixa(
    epochDay: Long,
    ausencias: List<Ausencia>,
    tipos: Map<Long, TipoTurno>
): Boolean = ausencias.any { ausencia ->
    epochDay >= ausencia.dataInicio && epochDay <= ausencia.dataFim &&
        tipos[ausencia.tipoTurnoId]?.categoria == CategoriaTurno.BAIXA
}

private fun naturezaDe(codigo: String): NaturezaRubrica = when {
    codigo == CODIGO_AUSENCIAS -> NaturezaRubrica.INFORMATIVA
    codigo in CODIGOS_DESCONTO || codigo == CODIGO_OUTROS_DESC -> NaturezaRubrica.DESCONTO
    else -> NaturezaRubrica.ABONO
}

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

/**
 * Minutos de trabalho previstos para o dia — a régua das horas suplementares.
 *
 * Regra do CCT confirmada pelo utilizador (2026-10-19): o suplemento só existe quando as horas
 * reais passam as horas previstas **daquele dia**; um dia curto não se compensa com um dia
 * longo. A régua é, por ordem: as horas do próprio dia projetado quando a projeção as traz
 * ([DiaProjetado.minutosPrevistos] — no modo PDF a escala é o turno impresso, mesmo que não haja
 * nenhum tipo de turno com essas horas); o turno projetado pela rotação; o tipo do próprio registo
 * (o editor guarda o tipo escolhido); e, sem nenhum deles, a jornada normal de 8h — que é também a
 * régua de quem trabalha uma folga (aí a folga vale 200% em DESC_DESC e o excedente sai pelas
 * HSUP_*_DESC).
 */
private fun jornadaPrevistaDoDia(
    dia: DiaReal,
    projecaoDoMes: List<DiaProjetado>,
    tipos: Map<Long, TipoTurno>
): Int {
    val projetadoDoDia = projecaoDoMes.firstOrNull { it.epochDay == dia.data }
    val minutosProjetados = projetadoDoDia?.minutosPrevistos ?: 0
    if (minutosProjetados > 0) return minutosProjetados
    val projetado = projetadoDoDia?.tipoTurnoId?.let { tipos[it] }
    if (projetado != null && projetado.categoria == CategoriaTurno.TRABALHO) {
        val previsto = duracaoMinutos(projetado.inicioMin, projetado.fimMin, projetado.pausaMin)
        if (previsto > 0) return previsto
    }
    val doRegisto = dia.tipoTurnoId?.let { tipos[it] }
    if (doRegisto != null && doRegisto.categoria == CategoriaTurno.TRABALHO) {
        val previsto = duracaoMinutos(doRegisto.inicioMin, doRegisto.fimMin, doRegisto.pausaMin)
        if (previsto > 0) return previsto
    }
    return MINUTOS_JORNADA_DIA
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
