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

    /** Dias reais do mês, do dia 1 ao dia `quantos`, com o horário indicado. */
    private fun dias(
        anoMes: YearMonth,
        quantos: Int,
        inicioMin: Int = 8 * 60,
        fimMin: Int = 16 * 60
    ): List<DiaReal> = (1..quantos).map { dia ->
        DiaReal(
            data = anoMes.atDay(dia).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = inicioMin,
            fimMin = fimMin,
            pausaMin = 0,
            origem = "MANUAL"
        )
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
    private fun turnoDaTarde(epochDay: Long, origem: String) = DiaReal(
        data = epochDay,
        tipoTurnoId = null,
        inicioMin = 13 * 60,
        fimMin = 22 * 60,
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

    @Test
    fun `T2 - julho 2026 com 18 dias de ferias, a divergencia do IRS`() {
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

        // 1h × 6,5653 € × 1,5 = 98 480 (9,85 €)
        assertEquals(98_480L, comExtra.valor("HSUP_DN"))
        assertEquals(0L, comExtra.valor("HNOT"))

        // As horas suplementares entram nas bases de SS e de IRS
        assertEquals(1_319_910L, comExtra.valor("D01"))   // 11% de 11 999 180
        assertEquals(533_426L, comExtra.valor("D02"))     // IRS sobre 11 999 180
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
        // 1h noturna (21h–22h): 65 653 × 5 ÷ 4 = 82 066 (8,21 €).
        assertEquals(82_066L, est.valor("HNOT"))
        // O dia tem 9h: a hora extra cai toda dentro da janela noturna.
        assertEquals(143_616L, est.valor("HSUP_NT"))
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
            ).copy(tiposTurno = listOf(tipoTrabalho))
        )

        // 11 dias úteis dentro das férias: 10–14, 17–21 e 24 (10 e 24 são segundas-feiras).
        assertEquals(863_500L, est.valor("SUP_ALIM"))   // (22 − 11) × 7,85 € = 86,35 €
        assertEquals(329_903L, est.valor("SUP_TRAN"))   // 52,09 € × 19 ÷ 30 = 32,99 €

        // As noites de 10, 11, 12 e 24/08 caem todas dentro das férias → 0,00 €.
        assertEquals(0L, est.valor("HNOT"))
        assertEquals(0L, est.valor("HSUP_NT"))

        // Base de incidência: 1 137,98 + 32,99 = 1 170,97 € (o cartão de refeição não incide).
        assertEquals(1_288_067L, est.valor("D01"))      // 11% → 128,81 €
        assertEquals(472_057L, est.valor("D02"))        // escalão 4 → 47,21 €
        assertEquals(113_798L, est.valor("D04"))        // 1% do VENC → 11,38 €
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

    private fun EstimativaRecibo.valor(codigo: String): Long =
        rubricas.first { it.codigo == codigo }.valorMil
}
