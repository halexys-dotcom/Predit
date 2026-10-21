package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.data.local.TABELAS_IRS_2026
import pt.haconnect.predit.data.repository.paraEscalao
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.EstadoCivil
import pt.haconnect.predit.domain.model.LIMIAR_DIVERGENCIA_UNIDADES
import pt.haconnect.predit.domain.model.ParametrosCCT
import pt.haconnect.predit.domain.model.RegimeHorario
import pt.haconnect.predit.domain.model.Rubrica
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** Ids dos tipos de turno semeados (PreditApplication.garantirTiposTurno): 3 = Férias, 4 = Baixa médica. */
private const val TIPO_FERIAS = 3L
private const val TIPO_BAIXA = 4L

/**
 * Estimador do recibo contra dados reais: catálogo de rubricas da Fase 8.1, parâmetros
 * do CCT de 2026 e as tabelas de IRS de 2026 (Continente, tabela I) dos XLSX oficiais.
 */
class EstimadorReciboTest {

    /** Escalões reais da tabela I (trabalho, Continente) para 2026. */
    private val tabelaI: List<EscalaoIRS> = TABELAS_IRS_2026
        .filter { it.regiao == "CONTINENTE" && it.categoria == "TRABALHO" && it.tabelaNumero == 1 }
        .map { it.paraEscalao() }

    /** Catálogo igual ao que a app semeia (PreditApplication.garantirRubricas). */
    private val catalogo = listOf(
        Rubrica(1, "VENC", "Vencimento", true, true, true, "FIXO", true, 1),
        Rubrica(2, "HNOT", "Horas noturnas", true, true, true, "HORAS", true, 2),
        Rubrica(3, "HSUP_DN", "Sup. diurno dia normal", true, true, true, "HORAS", true, 3),
        Rubrica(4, "HSUP_NT", "Sup. noturno dia normal", true, true, true, "HORAS", true, 4),
        Rubrica(5, "HSUP_DN_FER", "Sup. diurno feriado", true, true, true, "HORAS", true, 5),
        Rubrica(6, "HSUP_NT_FER", "Sup. noturno feriado", true, true, true, "HORAS", true, 6),
        Rubrica(7, "HSUP_DN_DESC", "Sup. diurno descanso", true, true, true, "HORAS", true, 7),
        Rubrica(8, "HSUP_NT_DESC", "Sup. noturno descanso", true, true, true, "HORAS", true, 8),
        Rubrica(9, "SUP_ALIM", "Sub. alimentação", false, false, false, "FIXO", true, 9),
        Rubrica(10, "SUP_TRAN", "Sub. transporte", true, true, false, "FIXO", true, 10),
        Rubrica(11, "DESC_FER", "Dia feriado trabalhado", true, true, true, "HORAS", true, 11),
        Rubrica(12, "DESC_DESC", "Dia descanso trabalhado", true, true, true, "HORAS", true, 12),
        Rubrica(15, "FERIAS", "Subsídio de férias", true, false, false, "FIXO", false, 15),
        Rubrica(16, "NATAL", "Subsídio de Natal", true, false, false, "FIXO", false, 16),
        // Descontos: acrescentados ao catálogo semeado na Fase 8.2b.0, com tipoCalculo DERIVADO.
        Rubrica(20, "D01", "Segurança Social (11%)", false, false, false, "DERIVADO", true, 20),
        Rubrica(21, "D02", "IRS", false, false, false, "DERIVADO", true, 21),
        Rubrica(22, "D04", "Sindicato (1%)", false, false, false, "DERIVADO", true, 22),
        // 2026-10-21: o complemento de baixa (R51) incide SS e IRS — o recibo real de maio/2026
        // desconta 31,51 € de SS sobre 286,41 € de complemento. Sindicato não (o D04 só vê o VENC).
        Rubrica(24, "COMP_BAIXA", "Complemento de baixa", true, true, false, "MANUAL", true, 24),
        Rubrica(25, "ATRASO", "Ausências", false, false, false, "DERIVADO", true, 25),
        Rubrica(99, "OUTROS", "Outros", true, true, true, "MANUAL", false, 99)
    )

    private val parametros2026 = ParametrosCCT(
        id = 1,
        validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
        vencimentoBaseMil = 11_379_800,       // 1 137,98 €
        subAlimentacaoDiaMil = 78_500,        // 7,85 €/dia
        subTransporteMesMil = 520_900,        // 52,09 €/mês
        horarioSemanalReferencia = 40
    )

    /** Casado 2 titulares, 2 dependentes, Continente, 40h → tabela I. */
    private val contrato = ContratoUtilizador(
        estadoCivil = EstadoCivil.CASADO,
        titulares = 2,
        numeroDependentes = 2,
        regimeHorario = RegimeHorario.NORMAL,
        horarioSemanalH = 40,
        dataAdmissao = LocalDate.of(2020, 1, 1).toEpochDay(),
        regiao = RegiaoIRS.CONTINENTE
    )

    private val tipoTrabalho = TipoTurno(
        id = 1,
        nome = "Tarde 8h",
        abreviatura = "T08H",
        cor = 0xFFC0392BL,
        inicioMin = 13 * 60,
        fimMin = 21 * 60,
        pausaMin = 0,
        categoria = CategoriaTurno.TRABALHO,
        ativo = true
    )

    private val tipoFerias = TipoTurno(
        id = 3,
        nome = "Ferias",
        abreviatura = "FER",
        cor = 0xFF2196F3L,
        inicioMin = 0,
        fimMin = 0,
        pausaMin = 0,
        categoria = CategoriaTurno.FERIAS,
        ativo = true
    )

    private val tipoBaixa = TipoTurno(
        id = 4,
        nome = "Baixa medica",
        abreviatura = "BM",
        cor = 0xFFF44336L,
        inicioMin = 0,
        fimMin = 0,
        pausaMin = 0,
        categoria = CategoriaTurno.BAIXA,
        ativo = true
    )

    /**
     * Dias reais trabalhados do mês — `quantos` dias de trabalho a partir do dia 1, com o
     * horário indicado, saltando os feriados nacionais: ninguém regista horas num dia que a
     * escala dá como feriado. Sem o salto, agosto de 2026 traria o 15 (Assunção, sábado) e o
     * mês deixaria de ser "sem extras" — os dias de feriado trabalhado têm testes próprios
     * (T12 e T14), explícitos e com a data à vista.
     */
    private fun dias(
        anoMes: YearMonth,
        quantos: Int,
        inicioMin: Int = 8 * 60,
        fimMin: Int = 16 * 60
    ): List<DiaReal> {
        val feriados = feriadosNacionais(anoMes.year)
        val uteis = mutableListOf<DiaReal>()
        var dia = 1
        while (uteis.size < quantos) {
            val candidato = anoMes.atDay(dia)
            if (candidato.toEpochDay() !in feriados) {
                uteis.add(
                    DiaReal(
                        data = candidato.toEpochDay(),
                        tipoTurnoId = null,
                        inicioMin = inicioMin,
                        fimMin = fimMin,
                        pausaMin = 0,
                        origem = "MANUAL"
                    )
                )
            }
            dia++
        }
        return uteis
    }

    /** Projeção do mês: dias úteis de trabalho, o N.º Dias Úteis do cabeçalho do recibo. */
    private fun projecaoDe(anoMes: YearMonth, quantos: Int = 22): List<DiaProjetado> =
        (1..quantos).map { DiaProjetado(anoMes.atDay(it).toEpochDay(), tipoTurnoId = 1L) }

    private fun contexto(
        anoMes: YearMonth,
        diasReais: List<DiaReal>,
        projecao: List<DiaProjetado> = emptyList(),
        rubricas: List<Rubrica> = catalogo,
        ausencias: List<Ausencia> = emptyList()
    ) = ContextoEstimativa(
        anoMes = anoMes,
        contrato = contrato,
        parametrosCCT = parametros2026,
        rubricas = rubricas,
        diasReais = diasReais,
        projecao = projecao,
        feriados = emptySet(),
        escaloesIRS = tabelaI,
        ausencias = ausencias
    )

    /** Turno das 13h às 22h — 9h, das quais 1h na janela noturna (21h–22h). */
    /** Turno da tarde de 9h (14h–23h): 1h noturna dentro da jornada e a 9.ª hora também noturna. */
    private fun turnoDaTarde(epochDay: Long, origem: String) = DiaReal(
        data = epochDay,
        tipoTurnoId = null,
        inicioMin = 14 * 60,
        fimMin = 23 * 60,
        pausaMin = 0,
        origem = origem
    )

    /** Férias de 5 dias, com o dia indicado ao centro (tipoTurnoId 3 = "Férias"/FER). */
    private fun ferias(epochDay: Long) = Ausencia(
        id = 1,
        tipoTurnoId = 3,
        dataInicio = epochDay - 2,
        dataFim = epochDay + 2
    )

    @Test
    fun `T1 - agosto 2026 mes completo sem extras`() {
        val agosto = YearMonth.of(2026, 8)
        val est = estimarRecibo(contexto(agosto, dias(agosto, 22)))

        assertEquals("2026-08", est.anoMes)
        assertEquals(11_379_800L, est.valor("VENC"))      // 1 137,98 €
        assertEquals(1_727_000L, est.valor("SUP_ALIM"))   // 22 × 7,85 €
        assertEquals(520_900L, est.valor("SUP_TRAN"))     // mês completo
        assertEquals(0L, est.valor("HNOT"))
        assertEquals(0L, est.valor("HSUP_DN"))

        // Base de incidência: VENC + SUP_TRAN (o cartão de refeição não incide)
        assertEquals(1_309_077L, est.valor("D01"))        // 11% de 1 190,07 € · comando: 1 309 100
        assertEquals(512_548L, est.valor("D02"))          // motor IRS · igual ao comando
        assertEquals(113_798L, est.valor("D04"))          // 1% de 1 137,98 € · comando: 113 800

        assertEquals(13_627_700L, est.totalAbonos)        // 1 362,77 € — bate com o recibo
        assertEquals(11_692_277L, est.liquido)
    }

    /**
     * 2026-10-21: a rubrica de recurso dos descontos desconhecidos (OUTROS_DESC, onde cai o D05 da
     * Fundação) tem de contar como DESCONTO. Se fosse abono, o valor real importado somava ao
     * líquido em vez de o tirar — que é o que a guarda do C7 existe para impedir.
     */
    @Test
    fun `29 - Outros Desconto conta como desconto e nao como abono`() {
        val agosto = YearMonth.of(2026, 8)
        val comOutrosDesc = catalogo + Rubrica(
            98, "OUTROS_DESC", "Outros", false, false, false, "MANUAL", false, 98
        )
        val est = estimarRecibo(contexto(agosto, dias(agosto, 22), rubricas = comOutrosDesc))

        assertEquals(NaturezaRubrica.DESCONTO, est.rubricas.first { it.codigo == "OUTROS_DESC" }.natureza)
        // Não é estimada (manual), por isso não mexe nos totais por si — mas se fosse abono,
        // saía daqui somada.
        val semEla = estimarRecibo(contexto(agosto, dias(agosto, 22)))
        assertEquals(semEla.totalAbonos, est.totalAbonos)
        assertEquals(semEla.totalDescontos, est.totalDescontos)
    }

    @Test
    fun `T2 - julho 2026 sem ausencia registada, a divergencia do IRS`() {
        val julho = YearMonth.of(2026, 7)
        // Projeção do mês inteiro: 22 dias úteis, o N.º Dias Úteis do cabeçalho do recibo.
        val projecao = projecaoDe(julho)

        val est = estimarRecibo(
            contexto(julho, dias(julho, 11), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        // 8.3: as férias de julho nunca foram registadas como ausência, por isso os
        // subsídios saem a mês completo — não olham aos 11 dias que se trabalhou.
        assertEquals(1_727_000L, est.valor("SUP_ALIM"))   // (22 − 0) × 7,85 €
        assertEquals(520_900L, est.valor("SUP_TRAN"))     // 52,09 € × 30 ÷ 30
        // IRS sobre 1 137,98 + 52,09 = 1 190,07 € → 512 548 (51,25 €)
        assertEquals(512_548L, est.valor("D02"))

        // O recibo real de julho reteve 48,00 € de IRS e pagou 19,10 € de transporte: a
        // diferença passa o limiar de 1 cêntimo — é o que a conferência tem de assinalar.
        val doRecibo = 480_000L   // 48,00 € — o que o recibo de julho reteve
        val diferenca = abs(est.valor("D02") - doRecibo)
        assertEquals(32_548L, diferenca)
        assertTrue(
            "a divergência de $diferenca unidades tem de passar o limiar de 1 cêntimo",
            diferenca > LIMIAR_DIVERGENCIA_UNIDADES
        )
    }

    @Test
    fun `T3 - mes sem dias reais nem projecao devolve tudo a zero`() {
        val est = estimarRecibo(contexto(YearMonth.of(2026, 9), emptyList()))

        assertEquals("2026-09", est.anoMes)
        assertTrue(est.rubricas.all { it.valorMil == 0L })
        assertEquals(0L, est.totalAbonos)
        assertEquals(0L, est.totalDescontos)
        assertEquals(0L, est.liquido)
    }

    @Test
    fun `T4 - dia de 9h gera hora suplementar diurna e entra nas bases`() {
        val agosto = YearMonth.of(2026, 8)
        val comExtra = estimarRecibo(contexto(agosto, dias(agosto, 1, 8 * 60, 17 * 60)))
        val semExtra = estimarRecibo(contexto(agosto, dias(agosto, 1)))

        // 1h x 6,57 EUR x 1,5 = 98 550 (9,86 EUR).
        assertEquals(98_550L, comExtra.valor("HSUP_DN"))
        assertEquals(0L, comExtra.valor("HNOT"))

        // As horas suplementares entram nas bases de SS e de IRS
        assertEquals(1_319_918L, comExtra.valor("D01"))   // 11% de 11 999 250
        assertEquals(533_441L, comExtra.valor("D02"))     // IRS sobre 11 999 250
        assertTrue("D01 sobe com o suplemento", comExtra.valor("D01") > semExtra.valor("D01"))
        assertTrue("D02 sobe com o suplemento", comExtra.valor("D02") > semExtra.valor("D02"))
    }

    @Test
    fun `T5 - subsidio de ferias nao incide IRS e nao altera D02`() {
        val agosto = YearMonth.of(2026, 8)
        val semFerias = estimarRecibo(contexto(agosto, dias(agosto, 22)))
        val comFerias = estimarRecibo(
            contexto(
                agosto,
                dias(agosto, 22),
                rubricas = catalogo + Rubrica(
                    97, "R97", "Subsídio de férias", true, false, false, "FIXO", false, 97
                )
            )
        )

        assertEquals(semFerias.valor("D02"), comFerias.valor("D02"))
        assertEquals(0L, comFerias.valor("R97"))   // não estimada: fica para o utilizador
    }

    @Test
    fun `T6 - dia PDF dentro de ferias nao conta horas`() {
        val agosto = YearMonth.of(2026, 8)
        val dia = agosto.atDay(5).toEpochDay()

        val est = estimarRecibo(
            contexto(
                agosto,
                listOf(turnoDaTarde(dia, "PDF")),
                projecao = projecaoDe(agosto),
                ausencias = listOf(ferias(dia))
            ).copy(tiposTurno = listOf(tipoTrabalho))
        )

        // O que o PDF imprime nesse dia é o horário planeado da empresa, não trabalho
        // efetivo: as horas noturnas que teria não contam.
        assertEquals(0L, est.valor("HNOT"))
        assertEquals(0L, est.valor("HSUP_NT"))
        // O subsídio é o do mês menos os dias úteis de férias (3 a 7 de agosto são 5):
        // (22 − 5) × 7,85 €. O dia de PDF não o recupera nem o faz perder mais.
        assertEquals(1_334_500L, est.valor("SUP_ALIM"))
    }

    @Test
    fun `T7 - dia MANUAL dentro de ferias conta as horas noturnas`() {
        val agosto = YearMonth.of(2026, 8)
        val dia = agosto.atDay(5).toEpochDay()

        val est = estimarRecibo(
            contexto(
                agosto,
                listOf(turnoDaTarde(dia, "MANUAL")),
                projecao = projecaoDe(agosto),
                ausencias = listOf(ferias(dia))
            ).copy(tiposTurno = listOf(tipoTrabalho))
        )

        // O utilizador registou o dia à mão: disse "trabalhei", conta apesar das férias.
        // 1h noturna dentro da jornada (21h-22h): 65 700 x 1 / 4 = 16 425 (1,64 EUR) - o HNOT e
        // o acréscimo de 25%, a hora em si já vem no VENC.
        assertEquals(16_425L, est.valor("HNOT"))
        // Mas horas extra não: um dia de ausência registada (férias, baixa, folga) nunca gera
        // suplemento, mesmo com um registo real por cima — nesse dia o utilizador não
        // trabalhou. A 9.ª hora (22h–23h) fica sem HSUP_NT.
        assertEquals(0L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("HSUP_DN"))
        // Trabalhar num dia de férias dá as horas, mas não devolve o dia útil ao subsídio:
        // é o mesmo valor do T6, porque a regra dos subsídios não olha aos dias trabalhados.
        assertEquals(1_334_500L, est.valor("SUP_ALIM"))
    }

    @Test
    fun `T8 - ferias de 10 a 24 de agosto cortam 11 dias uteis de subsidio`() {
        val agosto = YearMonth.of(2026, 8)
        // Férias a sério: 10 a 24 de agosto de 2026 (epochDay 20675 a 20689), tipo FER.
        val feriasDeAgosto = Ausencia(
            id = 2,
            tipoTurnoId = 3,
            dataInicio = agosto.atDay(10).toEpochDay(),
            dataFim = agosto.atDay(24).toEpochDay()
        )

        val est = estimarRecibo(
            contexto(
                agosto,
                diasReaisDeAgosto2026(),
                projecao = projecaoDe(agosto),
                ausencias = listOf(feriasDeAgosto)
            ).copy(tiposTurno = listOf(tipoTrabalho, tipoFerias))
        )

        // 11 dias úteis dentro das férias: 10–14, 17–21 e 24 (10 e 24 são segundas-feiras).
        assertEquals(863_500L, est.valor("SUP_ALIM"))   // (22 − 11) × 7,85 € = 86,35 €
        assertEquals(329_903L, est.valor("SUP_TRAN"))   // 52,09 € × 19 ÷ 30 = 32,99 €

        // As noites de 10, 11, 12 e 24/08 caem todas dentro das férias → 0,00 €.
        assertEquals(0L, est.valor("HNOT"))
        assertEquals(0L, est.valor("HSUP_NT"))

        // Ferias contam como dias pagos: o VENC nao e cortado por ferias.
        // (so a baixa corta o VENC; aqui so ha ferias.)
        assertEquals(11_379_800L, est.valor("VENC"))

        // Base de incidencia: 1 137,98 + 32,99 = 1 170,97 EUR (o cartao de refeicao nao incide).
        assertEquals(1_288_067L, est.valor("D01"))      // 11% = 128,81 EUR
        // 1 170,97 EUR cai no 4. escalao da tabela I: 21,20% - 158,18 - 2 x 21,43 = 47,21 EUR.
        assertEquals(472_057L, est.valor("D02"))
        assertEquals(113_798L, est.valor("D04"))        // 1% do VENC = 11,38 EUR
    }

    @Test
    fun `T9 - ausencia a cavalo de dois meses so corta os dias uteis de julho`() {
        val julho = YearMonth.of(2026, 7)
        // Férias de 28/07 a 05/08/2026: atravessam a fronteira do mês.
        val cavaloDeDoisMeses = Ausencia(
            id = 9,
            tipoTurnoId = TIPO_FERIAS,
            dataInicio = LocalDate.of(2026, 7, 28).toEpochDay(),
            dataFim = LocalDate.of(2026, 8, 5).toEpochDay()
        )

        val est = estimarRecibo(
            contexto(
                julho,
                dias(julho, 11),
                projecao = projecaoDe(julho),
                ausencias = listOf(cavaloDeDoisMeses)
            ).copy(tiposTurno = listOf(tipoTrabalho))
        )

        // Só a parte de julho conta: 28, 29, 30 e 31 (terça a sexta) → 4 dias úteis.
        // 1 e 2 de agosto são fim de semana e 3 a 5 pertencem ao mês seguinte: não entram.
        assertEquals(1_413_000L, est.valor("SUP_ALIM"))   // (22 − 4) × 7,85 € = 141,30 €
        assertEquals(451_447L, est.valor("SUP_TRAN"))     // 52,09 € × (30 − 4) ÷ 30 = 45,14 €
    }

    @Test
    fun `T10 - duas ausencias sobrepostas contam a uniao uma vez`() {
        val agosto = YearMonth.of(2026, 8)
        // 10–17 e 14–21 de agosto: sobrepõem-se de 14 a 17.
        val primeira = Ausencia(
            id = 10,
            tipoTurnoId = TIPO_FERIAS,
            dataInicio = agosto.atDay(10).toEpochDay(),
            dataFim = agosto.atDay(17).toEpochDay()
        )
        val segunda = Ausencia(
            id = 11,
            tipoTurnoId = TIPO_FERIAS,
            dataInicio = agosto.atDay(14).toEpochDay(),
            dataFim = agosto.atDay(21).toEpochDay()
        )

        val est = estimarRecibo(
            contexto(
                agosto,
                dias(agosto, 5),
                projecao = projecaoDe(agosto),
                ausencias = listOf(primeira, segunda)
            ).copy(tiposTurno = listOf(tipoTrabalho))
        )

        // União 10–21 de agosto: 10, 11, 12, 13, 14, 17, 18, 19, 20 e 21 → 10 dias úteis.
        // Nem 8 (só a primeira), nem 16 (as duas somadas): os dias sobrepostos contam uma vez.
        assertEquals(942_000L, est.valor("SUP_ALIM"))     // (22 − 10) × 7,85 € = 94,20 €
        assertEquals(347_267L, est.valor("SUP_TRAN"))     // 52,09 € × (30 − 10) ÷ 30 = 34,73 €
    }

    @Test
    fun `T11 - baixa medica corta o subsidio como as ferias`() {
        val agosto = YearMonth.of(2026, 8)
        // Baixa médica (id 4, categoria BAIXA) no mesmo intervalo do T8, com o mesmo mês.
        val baixa = Ausencia(
            id = 12,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = agosto.atDay(10).toEpochDay(),
            dataFim = agosto.atDay(24).toEpochDay()
        )

        val est = estimarRecibo(
            contexto(
                agosto,
                diasReaisDeAgosto2026(),
                projecao = projecaoDe(agosto),
                ausencias = listOf(baixa)
            ).copy(tiposTurno = listOf(tipoTrabalho, tipoBaixa))
        )

        // 2026-10-21: a baixa corta o VENC. 13 dos 22 dias úteis projetados estão cobertos:
        // 1 137,98 € × (22 − 13) ÷ 22 = 465,5373 €.
        assertEquals(4_655_373L, est.valor("VENC"))
        assertEquals(863_500L, est.valor("SUP_ALIM"))     // (22 - 11) x 7,85 EUR = 86,35 EUR
        assertEquals(329_903L, est.valor("SUP_TRAN"))     // 32,99 EUR
        assertEquals(0L, est.valor("HNOT"))
        assertEquals(0L, est.valor("HSUP_NT"))
        // Base de IRS 465,5373 + 32,9903 = 498,5276 EUR: abaixo dos 920,00 EUR do 1.º escalão
        // (taxa 0), por isso não há retenção.
        assertEquals(0L, est.valor("D02"))
    }

    /**
     * Baixa o mes inteiro: não fica um único dia efetivo, por isso o VENC vai a 0 — e com ele
     * as bases de incidência e os três descontos derivados. Os subsídios mantêm a regra 8.3 e
     * anulam-se no mês inteiro coberto.
     */
    @Test
    fun `23 - baixa o mes inteiro - vencimento zero e subsidios a zero`() {
        val agosto = YearMonth.of(2026, 8)
        val baixaOMes = Ausencia(
            id = 23,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = agosto.atDay(1).toEpochDay(),
            dataFim = agosto.atEndOfMonth().toEpochDay()
        )
        // Todos os dias de agosto ao serviço na projeção: 31 dias de trabalho projetados.
        val projecaoAgosto = (1..31).map {
            DiaProjetado(agosto.atDay(it).toEpochDay(), tipoTurnoId = 1L)
        }

        val est = estimarRecibo(
            contexto(
                agosto,
                diasReaisDeAgosto2026(),
                projecao = projecaoAgosto,
                ausencias = listOf(baixaOMes)
            ).copy(tiposTurno = listOf(tipoTrabalho, tipoBaixa))
        )

        // A baixa cobre o mês inteiro: os 31 dias projetados são todos dias de baixa, não sobra
        // um dia efetivo. VENC = 0 e os descontos derivados também — não há matéria coletável.
        assertEquals(0L, est.valor("VENC"))
        assertEquals(0L, est.valor("D01"))
        assertEquals(0L, est.valor("D02"))
        assertEquals(0L, est.valor("D04"))
        // É o que o recibo real de Março de 2026 paga: R14 = 0,00 e o cartão anula-se.
        assertEquals(0L, est.valor("SUP_ALIM"))
        assertEquals(0L, est.valor("SUP_TRAN"))
        // O que o PDF imprime dentro da baixa é horário planeado: não gera horas.
        assertEquals(0L, est.valor("HNOT"))
        assertEquals(0L, est.valor("HSUP_NT"))
    }

    /**
     * Complemento de baixa (R51) num mês de baixa total. O recibo real de maio/2026 (ICTS, fecho
     * 13/05) traz 286,41 € de complemento e 31,51 € de D01 — 286,41 × 11% = 31,505. O IRS ficou a
     * 0 porque 286,41 € está abaixo do 1.º escalão, não por isenção: o complemento entra na base e
     * é a tabela que decide. Não é sindicato: o D04 incide só sobre o VENC.
     */
    @Test
    fun `30 - complemento de baixa paga SS e IRS e nao entra no sindicato`() {
        val maio = YearMonth.of(2026, 5)
        val baixaOMes = Ausencia(
            id = 30,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = maio.atDay(1).toEpochDay(),
            dataFim = maio.atEndOfMonth().toEpochDay()
        )
        val projecaoMaio = (1..31).map {
            DiaProjetado(maio.atDay(it).toEpochDay(), tipoTurnoId = 1L)
        }

        val est = estimarRecibo(
            contexto(
                maio,
                dias(maio, 22),
                projecao = projecaoMaio,
                ausencias = listOf(baixaOMes)
            ).copy(
                tiposTurno = listOf(tipoTrabalho, tipoBaixa),
                contrato = contrato.copy(sindicalizado = true),
                complementoBaixaMil = 2_864_100L        // 286,41 €: o complemento do recibo
            )
        )

        // Nenhum dia efetivo no mês: VENC e subsídios a 0, como no T23.
        assertEquals(0L, est.valor("VENC"))
        assertEquals(0L, est.valor("SUP_ALIM"))
        assertEquals(0L, est.valor("SUP_TRAN"))
        // Base de SS = 286,41 € do complemento: 2 864 100 × 11% = 315 051 (31,51 € no recibo).
        assertEquals(315_051L, est.valor("D01"))
        // Base de IRS = 286,41 €, abaixo dos 920,00 € do 1.º escalão: retenção 0 — como no recibo.
        assertEquals(0L, est.valor("D02"))
        // Sindicato: 1% do VENC, que é 0. O complemento não entra, mesmo com contrato sindicalizado.
        assertEquals(0L, est.valor("D04"))

        // O complemento é espelho do recibo, não conta do motor: aparece no Estimado como abono
        // (fora de CODIGOS_ESTIMADOS) e o líquido fecha em 286,41 − 31,51 = 254,90 €.
        assertEquals(2_864_100L, est.valor("COMP_BAIXA"))
        assertEquals(2_864_100L, est.totalAbonos)
        assertEquals(315_051L, est.totalDescontos)
        assertEquals(2_549_049L, est.liquido)

        // A prova de que o D04 não vê o complemento: num mês completo com o mesmo complemento, o
        // sindicato continua a ser 1% de 1 137,98 € (113 798) e não 1% de (VENC + 286,41) = 142 419.
        // O D01 leva o complemento: 11% de (11 379 800 + 520 900 + 2 864 100) = 1 624 128.
        val comMesTrabalhado = estimarRecibo(
            contexto(maio, dias(maio, 22)).copy(
                contrato = contrato.copy(sindicalizado = true),
                complementoBaixaMil = 2_864_100L
            )
        )
        assertEquals(113_798L, comMesTrabalhado.valor("D04"))
        assertEquals(1_624_128L, comMesTrabalhado.valor("D01"))
    }

    /**
     * O campo novo tem valor por omissão 0: sem complemento no recibo, as bases de um mês de baixa
     * total ficam a 0 e os três descontos também — o comportamento de antes (T23).
     */
    @Test
    fun `31 - sem complemento de baixa as bases nao mudam`() {
        val maio = YearMonth.of(2026, 5)
        val baixaOMes = Ausencia(
            id = 31,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = maio.atDay(1).toEpochDay(),
            dataFim = maio.atEndOfMonth().toEpochDay()
        )
        val projecaoMaio = (1..31).map {
            DiaProjetado(maio.atDay(it).toEpochDay(), tipoTurnoId = 1L)
        }

        val est = estimarRecibo(
            contexto(
                maio,
                dias(maio, 22),
                projecao = projecaoMaio,
                ausencias = listOf(baixaOMes)
            ).copy(
                tiposTurno = listOf(tipoTrabalho, tipoBaixa),
                contrato = contrato.copy(sindicalizado = true)
            )
        )

        assertEquals(0L, est.valor("VENC"))
        assertEquals(0L, est.valor("D01"))
        assertEquals(0L, est.valor("D02"))
        assertEquals(0L, est.valor("D04"))
    }

    /**
     * Regra reposta (2026-10-21): o VENC é proporcional aos dias efetivos. Em maio, 10 dias de
     * férias contam como pagos e 21 dias de baixa não contam — paga-se 10/31 do vencimento.
     */
    @Test
    fun `24 - maio com 10 ferias e 21 baixa paga o vencimento dos 10 dias efetivos`() {
        val maio = YearMonth.of(2026, 5)
        val feriasMaio = Ausencia(
            id = 24,
            tipoTurnoId = TIPO_FERIAS,
            dataInicio = maio.atDay(1).toEpochDay(),
            dataFim = maio.atDay(10).toEpochDay()
        )
        val baixaMaio = Ausencia(
            id = 25,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = maio.atDay(11).toEpochDay(),
            dataFim = maio.atEndOfMonth().toEpochDay()
        )
        // Projecao do mes inteiro ao servico (escala com fins de semana): 31 dias.
        val projecaoMaio = (1..31).map {
            DiaProjetado(maio.atDay(it).toEpochDay(), tipoTurnoId = 1L)
        }

        val est = estimarRecibo(
            contexto(
                maio,
                emptyList(),
                projecao = projecaoMaio,
                ausencias = listOf(feriasMaio, baixaMaio)
            ).copy(tiposTurno = listOf(tipoTrabalho, tipoFerias, tipoBaixa))
        )

        // As férias contam como dias pagos; a baixa não. 1 137,98 € × 10 ÷ 31 = 367,0903 €.
        assertEquals(3_670_903L, est.valor("VENC"))
    }

    /**
     * Atrasos: os minutos em falta num dia com registo MANUAL valem o valor/minuto
     * (65 700 / 60 = 1 095 = 0,1095 EUR). O valor sai do próprio VENC — o recibo mostra o
     * vencimento já descontado — e a rubrica AUSENCIAS fica registada como INFORMATIVA, só
     * para justificar o corte: se voltasse a contar como desconto, saía duas vezes do líquido.
     * SS, IRS e sindicato incidem sobre o VENC final.
     * O que vem do PDF é horário planeado, não trabalho efetivo: não gera atraso.
     */
    @Test
    fun `25 - atraso sai do VENC e a rubrica de ausencias e informativa`() {
        val outubro = YearMonth.of(2026, 10)
        val dia = outubro.atDay(2).toEpochDay()
        // Jornada projetada das 13h as 21h (8h); o registo manual saiu 1h01 mais cedo.
        val saidaMaisCedo = DiaReal(
            data = dia,
            tipoTurnoId = null,
            inicioMin = 13 * 60,
            fimMin = 21 * 60 - 61,
            pausaMin = 0,
            origem = "MANUAL"
        )
        val projecao = listOf(DiaProjetado(dia, tipoTurnoId = 1L))

        val est = estimarRecibo(
            contexto(outubro, listOf(saidaMaisCedo), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        // 61 minutos x 1 095 = 66 795 unidades (6,6795 EUR, 6,68 EUR no recibo).
        assertEquals(66_795L, est.valor("ATRASO"))
        // Informativa: existe para a UI mostrar, mas nao e abono nem desconto.
        assertEquals(
            NaturezaRubrica.INFORMATIVA,
            est.rubricas.first { it.codigo == "ATRASO" }.natureza
        )
        // VENC final = 1 137,98 − 6,6795 = 1 131,3005 EUR (um dia projetado, um efetivo).
        assertEquals(11_313_005L, est.valor("VENC"))
        // Bases de SS e IRS: VENC final + SUP_TRAN = 1 183,3905 EUR — o atraso não volta a ser
        // subtraído, porque já saiu do VENC.
        assertEquals(1_301_730L, est.valor("D01"))   // 11 %
        assertEquals(498_388L, est.valor("D02"))     // escalão de 21,2 %
        // Sindicato: 1 % do VENC já descontado (113,13005 EUR).
        assertEquals(113_130L, est.valor("D04"))
        // Descontos: só D01 + D02 + D04. A rubrica das ausências não entra.
        assertEquals(1_913_248L, est.totalDescontos)
        // Abonos: 1 131,3005 + 7,85 + 52,09 = 1 191,2405 EUR.
        assertEquals(11_912_405L, est.totalAbonos)
        // Líquido: 1 191,2405 − 191,3248 = 999,9157 EUR — uma única dedução do atraso.
        assertEquals(9_999_157L, est.liquido)
        assertEquals(est.totalAbonos - est.totalDescontos, est.liquido)

        // O mesmo dia vindo do PDF nao conta: e o horario planeado da empresa.
        val doPdf = estimarRecibo(
            contexto(outubro, listOf(saidaMaisCedo.copy(origem = "PDF")), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )
        assertEquals(0L, doPdf.valor("ATRASO"))
        assertEquals(11_379_800L, doPdf.valor("VENC"))
    }

    /**
     * O caso real de julho/agosto de 2026: a folha de agosto — que paga as variáveis de julho,
     * por causa do desfasamento 1 — traz 9,86 € em "Sup. diurno dia normal", e a conta é
     * 1h x 6,57 EUR x 1,5 (não 1,5h x 6,57: 9,855 € é o valor de UMA hora a 150%). É o dia 7 de
     * julho, das 12h às 21h: 9h contra as 8h da jornada diária prevista, 1h suplementar. O
     * motor já acertava — o que faltava era o dia de férias por cima de um registo não pagar
     * nada (T7). O saldo mensal negativo do Horário não anula isto: não há compensação entre
     * dias.
     */
    @Test
    fun `26 - dia de 9h contra jornada de 8h paga 1h de suplemento como na folha`() {
        val julho = YearMonth.of(2026, 7)
        val dia = julho.atDay(7).toEpochDay()
        val doPdf = DiaReal(
            data = dia,
            tipoTurnoId = null,
            inicioMin = 12 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            origem = "PDF"
        )
        val projecao = listOf(DiaProjetado(dia, tipoTurnoId = 1L))

        val est = estimarRecibo(
            contexto(julho, listOf(doPdf), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        assertEquals(98_550L, est.valor("HSUP_DN"))
        assertEquals(0L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("HNOT"))
    }

    /**
     * Regra do CCT confirmada pelo utilizador (2026-10-19): o suplemento é por dia e não se
     * compensa com os dias curtos do mesmo mês. Dia 6: 9h30 reais contra 8h previstas → 1h30 de
     * suplemento. Dia 7: 4h reais contra 8h previstas → 4h de atraso, que saem do VENC e ficam
     * justificadas na rubrica ATRASO. Uma regra não anula a outra.
     */
    @Test
    fun `27 - suplemento por dia nao se compensa com o atraso do dia seguinte`() {
        val outubro = YearMonth.of(2026, 10)
        val longo = DiaReal(
            data = outubro.atDay(6).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = 9 * 60,
            fimMin = 18 * 60 + 30,
            pausaMin = 0,
            origem = "MANUAL"
        )
        val curto = DiaReal(
            data = outubro.atDay(7).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = 9 * 60,
            fimMin = 13 * 60,
            pausaMin = 0,
            origem = "MANUAL"
        )
        val projecao = listOf(
            DiaProjetado(longo.data, tipoTurnoId = 1L),
            DiaProjetado(curto.data, tipoTurnoId = 1L)
        )

        val est = estimarRecibo(
            contexto(outubro, listOf(longo, curto), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        // 90 min x 6,57 EUR x 1,5 = 14,78 EUR.
        assertEquals(147_825L, est.valor("HSUP_DN"))
        // 240 min x 0,1095 EUR = 26,28 EUR, já deduzidos no VENC e justificados no ATRASO.
        assertEquals(262_800L, est.valor("ATRASO"))
        // Turno das 9h às 18h30: nada cai na janela noturna.
        assertEquals(0L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("HNOT"))
    }

    /**
     * A2: com pausa, a janela das horas extra (de início + jornada até ao fim) mede mais do que o
     * extra real, porque a pausa fica lá dentro. Turno previsto 14h–22h30 com 30 min de pausa (8h);
     * trabalhado até às 00h30 com a mesma pausa: 10h reais, 2h de extra. A janela mede 22h–00h30
     * (150 min), mas o extra é 120 min — o noturno fica nos 120 e o diurno não fica negativo.
     */
    @Test
    fun `28 - pausa nao faz o noturno das horas extra passar o extra real`() {
        val outubro = YearMonth.of(2026, 10)
        val dia = outubro.atDay(6).toEpochDay()   // terça-feira, sem feriado
        val turnoComPausa = tipoTrabalho.copy(
            id = 7,
            inicioMin = 14 * 60,
            fimMin = 22 * 60 + 30,
            pausaMin = 30
        )
        val real = DiaReal(
            data = dia,
            tipoTurnoId = null,
            inicioMin = 14 * 60,
            fimMin = 30,              // 00h30 do dia seguinte
            pausaMin = 30,
            origem = "MANUAL"
        )

        val est = estimarRecibo(
            contexto(outubro, listOf(real), projecao = listOf(DiaProjetado(dia, tipoTurnoId = 7L)))
                .copy(tiposTurno = listOf(turnoComPausa))
        )

        // 120 min x 6,57 EUR x 2,1875 = 28,74 EUR. Sem o teto davam 150 min (35,93 EUR).
        assertEquals(287_438L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("HSUP_DN"))
        // HNOT: a jornada vai das 14h às 22h (sem contar a pausa), 1h na janela 21h–06h.
        assertEquals(16_425L, est.valor("HNOT"))
        // O dia rendeu mais do que o previsto: não há atraso.
        assertEquals(0L, est.valor("ATRASO"))
    }

    @Test
    fun `T12 - trabalho em feriado paga a jornada a 100 por cento`() {
        val maio = YearMonth.of(2026, 5)
        // 1 de maio de 2026 (sexta-feira) é feriado nacional, Dia do Trabalhador: o dia entra
        // no conjunto por conta de feriadosNacionais(2026), sem nada em ctx.feriados.
        val feriado = DiaReal(
            data = maio.atDay(1).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            origem = "MANUAL"
        )

        val est = estimarRecibo(contexto(maio, listOf(feriado)))

        // 8h (480 min) x 6,57 EUR/h x 1,0 = 52,56 EUR
        assertEquals(525_600L, est.valor("DESC_FER"))
        assertEquals(0L, est.valor("DESC_DESC"))

        // 13h–21h são exatamente 8h: não sobra hora suplementar, e a janela noturna
        // (21h–06h) só começa no minuto em que o turno acaba.
        assertEquals(0L, est.valor("HNOT"))
        assertEquals(0L, est.valor("HSUP_DN"))
        assertEquals(0L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("HSUP_DN_FER"))
        assertEquals(0L, est.valor("HSUP_NT_FER"))
    }

    @Test
    fun `T13 - folga de escala trabalhada paga a jornada a 200 por cento`() {
        val agosto = YearMonth.of(2026, 8)
        val dia = agosto.atDay(5).toEpochDay()
        // Tipo 2 é folga de escala (categoria FOLGA): é a projeção que decide, não o fim de
        // semana do calendário — 5 de agosto de 2026 é uma quarta-feira.
        val tipoFolga = TipoTurno(
            id = 2,
            nome = "Folga",
            abreviatura = "FOL",
            cor = 0xFF7F8C8DL,
            inicioMin = 0,
            fimMin = 0,
            pausaMin = 0,
            categoria = CategoriaTurno.FOLGA,
            ativo = true
        )
        val trabalhadoNaFolga = DiaReal(
            data = dia,
            tipoTurnoId = null,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            origem = "MANUAL"
        )

        val est = estimarRecibo(
            contexto(
                agosto,
                listOf(trabalhadoNaFolga),
                projecao = listOf(DiaProjetado(dia, tipoTurnoId = 2L))
            ).copy(tiposTurno = listOf(tipoTrabalho, tipoFolga))
        )

        // 8h (480 min) x 6,57 EUR/h x 2,0 = 105,12 EUR
        assertEquals(1_051_200L, est.valor("DESC_DESC"))
        assertEquals(0L, est.valor("DESC_FER"))
        // Trabalhar uma folga com 8h exatas não gera suplementar nenhuma.
        assertEquals(0L, est.valor("HSUP_DN_DESC"))
        assertEquals(0L, est.valor("HSUP_NT_DESC"))
    }

    @Test
    fun `T14 - feriado com 10h paga 8h em DESC_FER e o resto em HSUP_FER`() {
        val maio = YearMonth.of(2026, 5)
        // Mesmo feriado do T12, mas com 10h: 13h–23h.
        val feriadoLongo = DiaReal(
            data = maio.atDay(1).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = 13 * 60,
            fimMin = 23 * 60,
            pausaMin = 0,
            origem = "MANUAL"
        )

        val est = estimarRecibo(contexto(maio, listOf(feriadoLongo)))

        // O acrescimo para nas 8h: 480 min x 6,57 EUR x 1,0 = 52,56 EUR (as 2h a mais nao
        // entram aqui, senao pagavam-se duas vezes).
        assertEquals(525_600L, est.valor("DESC_FER"))
        assertEquals(0L, est.valor("DESC_DESC"))

        // As 2h extra (21h–23h, já dentro da janela noturna) saem pelas suplementares de
        // feriado: 120 min x 6,57 EUR x 4,375 = 574 875 - HSUP_NT_FER
        assertEquals(574_875L, est.valor("HSUP_NT_FER"))
        assertEquals(0L, est.valor("HSUP_DN_FER"))
        // O HNOT não soma nada: as 8h de jornada (13h–21h) são todas diurnas, e as 2h noturnas
        // que restam já vão dentro do HSUP_NT_FER acima (4,375 = 1,75 × 1,25, com a noite lá
        // dentro) — somá-las aqui outra vez pagava o acréscimo noturno em duplicado.
        assertEquals(0L, est.valor("HNOT"))
    }


    @Test
    fun `18 - desfasamento 0 - recibo de setembro nao consome dados de agosto`() {
        // Regressão: sem desfasamento, o recibo de setembro não olha para os dias
        // de agosto. Como não há dias em setembro, a estimativa fica a zero.
        val estimativa = estimarRecibo(
            ContextoEstimativa(
                anoMes = YearMonth.of(2026, 9),
                contrato = contrato,
                parametrosCCT = parametros2026,
                rubricas = catalogo,
                diasReais = diasReaisDeAgosto2026(),
                projecao = emptyList(),
                escaloesIRS = tabelaI
            )
        )

        assertEquals(0L, estimativa.valor("VENC"))
        assertEquals(0L, estimativa.valor("SUP_ALIM"))
        assertEquals(0L, estimativa.valor("SUP_TRAN"))
    }

    @Test
    fun `19 - desfasamento 1 - recibo de setembro consome dados de agosto`() {
        val contratoDesfasado = contrato.copy(desfasamentoPagamentoVariaveis = 1)

        val estimativa = estimarRecibo(
            ContextoEstimativa(
                anoMes = YearMonth.of(2026, 9),
                contrato = contratoDesfasado,
                // O contexto recebe o desfasamento do contrato, como faz o ReciboViewModel.
                desfasamentoPagamentoVariaveis = contratoDesfasado.desfasamentoPagamentoVariaveis,
                parametrosCCT = parametros2026,
                rubricas = catalogo,
                diasReais = diasReaisDeAgosto2026(),
                projecao = emptyList(),
                escaloesIRS = tabelaI
            )
        )

        // SUP_ALIM calculado com os 21 dias de agosto × 7,85 EUR = 164,85 EUR
        assertEquals(1_648_500L, estimativa.valor("SUP_ALIM"))
        // SUP_TRAN = 52,09 EUR (mês completo, sem ausências)
        assertEquals(520_900L, estimativa.valor("SUP_TRAN"))
        // Setembro não tem dias (nem projeção nem reais): sem dias efetivos não há proporção a
        // aplicar, fica o vencimento base — o de agosto não entra na conta.
        assertEquals(11_379_800L, estimativa.valor("VENC"))
    }


    @Test
    fun `20 - desfasamento 1 com projecao do mes anterior - folga detetada`() {
        // Cenário real: recibo de setembro, mas com desfasamento 1 e a projeção de
        // agosto (que tem folgas). Verifica que o motor deteta DESC_DESC e não cai no
        // fallback de diasTrabalhados.size.
        val contratoDesfasado = contrato.copy(desfasamentoPagamentoVariaveis = 1)

        val agosto = YearMonth.of(2026, 8)
        val folgaTipo = TipoTurno(
            id = 99L,
            nome = "Folga",
            abreviatura = "F",
            cor = 0xFF1ABC9CL,
            emoji = "",
            inicioMin = 0,
            fimMin = 0,
            pausaMin = 0,
            categoria = CategoriaTurno.FOLGA,
            ativo = true
        )
        // Projeção de agosto: os sábados e domingos ficam marcados como folga.
        val diasProjecao = (1..31).mapNotNull { dia ->
            val local = agosto.atDay(dia)
            val ehFolga = local.dayOfWeek.value >= 6
            DiaProjetado(
                epochDay = local.toEpochDay(),
                tipoTurnoId = if (ehFolga) folgaTipo.id else null
            )
        }
        // 1 de agosto de 2026 é um sábado: a projeção diz folga e o DiaReal diz que se
        // trabalhou — é isto que o motor tem de ver através do desfasamento.
        val diaRealSabado = DiaReal(
            data = agosto.atDay(1).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            origem = "MANUAL"
        )

        val estimativa = estimarRecibo(
            ContextoEstimativa(
                anoMes = YearMonth.of(2026, 9),
                contrato = contratoDesfasado,
                parametrosCCT = parametros2026,
                rubricas = catalogo,
                diasReais = listOf(diaRealSabado),
                projecao = diasProjecao,
                escaloesIRS = tabelaI,
                tiposTurno = listOf(folgaTipo),
                // O contexto recebe o desfasamento do contrato, como faz o ReciboViewModel.
                desfasamentoPagamentoVariaveis = contratoDesfasado.desfasamentoPagamentoVariaveis
            )
        )

        // O sábado trabalhado com folga projetada é DESC_DESC (2x o valor/hora)
        // 8h x 65 700 x 2 = 525 600 x 2 = 1 051 200
        assertEquals(1_051_200L, estimativa.valor("DESC_DESC"))
        // A projeção é de agosto e não conta para setembro: sem dias efetivos em setembro, o
        // VENC fica no valor base.
        assertEquals(11_379_800L, estimativa.valor("VENC"))
    }

    @Test
    fun `21 - sindicalizado true - D04 incide 1 por cento do VENC`() {
        // O default do contrato é sindicalizado = true, por isso o D04 não muda para quem já
        // usava a app: continua a 1 % do VENC.
        val estimativa = estimarRecibo(
            ContextoEstimativa(
                anoMes = YearMonth.of(2026, 8),
                contrato = contrato,
                parametrosCCT = parametros2026,
                rubricas = catalogo,
                diasReais = diasReaisDeAgosto2026(),
                projecao = emptyList(),
                escaloesIRS = tabelaI
            )
        )

        // 1 % de 1 137,98 EUR = 11,38 EUR = 113 798
        assertEquals(113_798L, estimativa.valor("D04"))
    }

    @Test
    fun `22 - sindicalizado false - D04 fica a 0`() {
        val contratoSemSindicato = contrato.copy(sindicalizado = false)

        val estimativa = estimarRecibo(
            ContextoEstimativa(
                anoMes = YearMonth.of(2026, 8),
                contrato = contratoSemSindicato,
                parametrosCCT = parametros2026,
                rubricas = catalogo,
                diasReais = diasReaisDeAgosto2026(),
                projecao = emptyList(),
                escaloesIRS = tabelaI
            )
        )

        // Sem sindicato não há desconto: a rubrica fica a 0, e o VENC não se mexe.
        assertEquals(0L, estimativa.valor("D04"))
        assertEquals(11_379_800L, estimativa.valor("VENC"))
    }


    /**
     * Dias reais com horas do PDF de agosto de 2026, como estão na BD: 21 dos 24 dias
     * registados (14, 15 e 20 são folgas de 0 min), todos de origem PDF e sem tipo de
     * turno — 11 dias de 8h, 8 de 9h, um de 7h e um de 10h.
     */
    private fun diasReaisDeAgosto2026(): List<DiaReal> {
        val agosto = YearMonth.of(2026, 8)

        fun registo(dia: Int, inicioMin: Int, fimMin: Int) = DiaReal(
            data = agosto.atDay(dia).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = inicioMin,
            fimMin = fimMin,
            pausaMin = 0,
            origem = "PDF"
        )

        val oitoHoras = listOf(1, 2, 4, 5, 6, 7, 13, 23, 27, 28, 30)
            .map { registo(it, 13 * 60, 21 * 60) }
        val noveHoras = listOf(10, 11, 12, 16, 17, 19, 21, 22)
            .map { registo(it, 13 * 60, 22 * 60) }
        val seteHoras = listOf(18).map { registo(it, 13 * 60, 20 * 60) }
        val dezHoras = listOf(24).map { registo(it, 14 * 60, 24 * 60) }

        return (oitoHoras + noveHoras + seteHoras + dezHoras).sortedBy { it.data }
    }

    /**
     * T31 (2026-10-21): contrato em modo PDF_MENSAL, como o ReciboViewModel o passa desde a
     * correção. A grelha do mês é a dos dias importados do PDF e o tipo de turno é derivado
     * (o importador grava `tipoTurnoId = null`) — é isso que dá dias úteis ao estimador.
     *
     * Sem esta projeção, o mês ficava sem régua: o VENC não era proporcional aos dias efetivos,
     * os atrasos e os suplementos não tinham contra o que ser medidos e o cabeçalho gravava
     * 0 dias úteis.
     */
    @Test
    fun `T31 - contrato PDF mensal estima o mes a partir dos dias do PDF`() {
        val setembro = YearMonth.of(2026, 9)
        // Os dias úteis do PDF: 13h-21h, sem registo manual. Setembro de 2026 tem 22.
        val doPdf = (1..setembro.lengthOfMonth()).mapNotNull { numero ->
            val data = setembro.atDay(numero)
            if (data.dayOfWeek.value >= 6) {
                null
            } else {
                DiaReal(
                    data = data.toEpochDay(),
                    tipoTurnoId = null,
                    inicioMin = 13 * 60,
                    fimMin = 21 * 60,
                    pausaMin = 0,
                    origem = ORIGEM_PDF
                )
            }
        }

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = doPdf,
            deEpochDay = setembro.atDay(1).toEpochDay(),
            ateEpochDay = setembro.atEndOfMonth().toEpochDay(),
            aplicacoes = emptyList(),      // contrato PDF não tem rotação nenhuma
            tiposTurno = listOf(tipoTrabalho)
        )

        val est = estimarRecibo(
            contexto(setembro, doPdf, projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        // 22 dias úteis × 7,85 € e o transporte do mês inteiro.
        assertEquals(1_727_000L, est.valor("SUP_ALIM"))
        assertEquals(520_900L, est.valor("SUP_TRAN"))
        // Mês completo: VENC inteiro e IRS do 3.º escalão (base 1 190,07 €, 2 dependentes).
        assertEquals(11_379_800L, est.valor("VENC"))
        assertEquals(512_548L, est.valor("D02"))
    }

    private fun EstimativaRecibo.valor(codigo: String): Long =
        rubricas.first { it.codigo == codigo }.valorMil
}
