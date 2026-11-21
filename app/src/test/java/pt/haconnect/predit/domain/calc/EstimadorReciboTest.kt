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
        // 2026-11-21: os subsídios incidem SS e IRS. O IRS é retido à parte (base própria, somada
        // ao D02), mas o catálogo diz a verdade: não são isentos.
        Rubrica(15, "FERIAS", "Subsídio de férias", true, true, false, "FIXO", false, 15),
        Rubrica(16, "NATAL", "Subsídio de Natal", true, true, false, "FIXO", false, 16),
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

    /**
     * Contexto de um mês com a admissão do contrato do teste (2020-01-01) por omissão — ano
     * completo, por isso o subsídio estimado é o vencimento base inteiro. Os testes dos
     * proporcionais passam a data que querem ver.
     */
    private fun contexto(
        anoMes: YearMonth,
        diasReais: List<DiaReal>,
        projecao: List<DiaProjetado> = emptyList(),
        rubricas: List<Rubrica> = catalogo,
        ausencias: List<Ausencia> = emptyList(),
        dataAdmissao: LocalDate = LocalDate.of(2020, 1, 1)
    ) = ContextoEstimativa(
        anoMes = anoMes,
        contrato = contrato.copy(dataAdmissao = dataAdmissao.toEpochDay()),
        parametrosCCT = parametros2026,
        rubricas = rubricas,
        diasReais = diasReais,
        projecao = projecao,
        feriados = emptySet(),
        escaloesIRS = tabelaI,
        ausencias = ausencias,
        dataAdmissao = dataAdmissao
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
    fun `T5 - rubrica manual de subsidio sem valor nao altera o D02`() {
        val agosto = YearMonth.of(2026, 8)
        val semFerias = estimarRecibo(contexto(agosto, dias(agosto, 22)))
        val comFerias = estimarRecibo(
            contexto(
                agosto,
                dias(agosto, 22),
                rubricas = catalogo + Rubrica(
                    97, "R97", "Subsídio de férias", true, true, false, "FIXO", false, 97
                )
            )
        )

        // A rubrica extra não é estimada nem tem valor no contexto (feriasMil = 0): fica a 0,00 e
        // não mexe no D02. O subsídio pago a sério entra por ContextoEstimativa.feriasMil — é o
        // T41 que prova a retenção autónoma.
        assertEquals(semFerias.valor("D02"), comFerias.valor("D02"))
        assertEquals(0L, comFerias.valor("R97"))
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

        // Férias marcadas: o subsídio de férias passa a ser estimado. De 1 de janeiro a 31 de
        // agosto vão 243 dias dos 365 do ano, e não há baixa nenhuma: 1 137,98 × 243 ÷ 365 =
        // 757,6141 EUR. As FÉRIAS não descontam — são tempo de serviço pago, como no VENC.
        assertEquals(7_576_141L, est.valor("FERIAS"))

        // Base de incidencia: 1 137,98 + 32,99 = 1 170,97 EUR (o cartao de refeicao nao incide),
        // mais o subsídio de férias, que paga SS no mês em que é pago.
        assertEquals(2_121_443L, est.valor("D01"))      // 11% de 1 928,58 EUR = 212,14 EUR
        // O IRS do subsídio é autónomo e 758,65 EUR fica abaixo do 1.º escalão da tabela I
        // (920 EUR): o D02 é o do mês, sem subsídio, como em T1.
        // 1 170,97 EUR cai no 4. escalao da tabela I: 21,20% - 158,18 - 2 x 21,43 = 47,21 EUR.
        assertEquals(472_057L, est.valor("D02"))
        assertEquals(113_798L, est.valor("D04"))        // 1% do VENC = 11,38 EUR
    }

    /**
     * T53 (2026-11-21, revisto duas vezes): em Portugal o subsídio de férias é um valor **fixo
     * anual** — paga-se todo de uma vez ou em duas metades (50 % + 50 %), e há empresas que o dão em
     * duodécimos. O motor estima **o que falta receber** (`direito até ao fim do mês − já pago nos
     * meses anteriores`, nunca abaixo de zero) enquanto o pago estiver **abaixo de metade do direito
     * do ano**; a partir de metade o subsídio considera-se liquidado e o estimado é 0,00
     * ([subsidioFeriasLiquidado]). Sem essa paragem, outubro de 2026 — subsídio pago todo em julho,
     * férias marcadas no mês — voltava a mostrar a diferença entre o direito acumulado até outubro e
     * o que já tinha sido pago: um remanescente que ninguém vai receber.
     *
     * O cenário é o mesmo das férias de agosto de 2026 (T8): vencimento base do CCT de 1 137,98 €,
     * admissão em 2020, férias de 10 a 24 marcadas. O **direito até ao fim de agosto** é o que a app
     * estima para o subsídio pago nesse mês — 757,61 € (`calcularSubsidioProporcional`, 243 de 365
     * dias) — e o direito do ano inteiro é o próprio vencimento base, 1 137,98 €, logo **metade são
     * 568,99 €**. O recibo real de outubro de 2026 trazia 827,62 €, valor que a app aceita como real
     * ([ContextoEstimativa.feriasMil]) em vez de o estimar.
     *
     * As férias continuam a cortar o subsídio de alimentação e o transporte em qualquer cenário —
     * o pagamento do subsídio não mexe nisso.
     */
    private fun contextoDoSubsidioDeFerias(
        jaPagoMil: Long,
        feriasMil: Long = 0L
    ): ContextoEstimativa {
        val agosto = YearMonth.of(2026, 8)
        val feriasDeAgosto = Ausencia(
            id = 2,
            tipoTurnoId = TIPO_FERIAS,
            dataInicio = agosto.atDay(10).toEpochDay(),
            dataFim = agosto.atDay(24).toEpochDay()
        )
        return contexto(
            agosto,
            diasReaisDeAgosto2026(),
            projecao = projecaoDe(agosto),
            ausencias = listOf(feriasDeAgosto)
        ).copy(
            tiposTurno = listOf(tipoTrabalho, tipoFerias),
            feriasMil = feriasMil,
            feriasJaPagoMil = jaPagoMil
        )
    }

    /** Direito ao subsídio de férias até ao fim de agosto de 2026, em 1/10000 €: 757,61 € (T8). */
    private val direitoFeriasAteAgosto = 7_576_141L

    /** Metade do direito ao subsídio de um ano inteiro (1 137,98 € ÷ 2), em 1/10000 €: 568,99 €. */
    private val metadeDoSubsidioAnual = 5_689_900L

    @Test
    fun `T53 - subsidio de ferias pago na totalidade nao tem nada a estimar`() {
        val est = estimarRecibo(contextoDoSubsidioDeFerias(jaPagoMil = direitoFeriasAteAgosto))

        // Direito 757,61 − já pago 757,61 = 0,00 — e 757,61 já passou de metade do subsídio anual,
        // por isso o subsídio está liquidado de qualquer forma. Sem subsídio não há SS nem IRS
        // autónomo dele.
        assertEquals(0L, est.valor("FERIAS"))
        // As férias continuam a cortar o cartão e o transporte.
        assertEquals(863_500L, est.valor("SUP_ALIM"))   // (22 − 11) × 7,85 €
        assertEquals(329_903L, est.valor("SUP_TRAN"))
    }

    @Test
    fun `T54 - subsidio de ferias pago abaixo de metade estima o que falta`() {
        val est = estimarRecibo(contextoDoSubsidioDeFerias(jaPagoMil = 4_000_000L))

        // Primeira prestação paga em julho (400,00 € = 35 % do subsídio anual): ainda abaixo de
        // metade, por isso o que falta é 757,61 − 400,00 = 357,61 €.
        assertEquals(3_576_141L, est.valor("FERIAS"))
        assertEquals(863_500L, est.valor("SUP_ALIM"))
    }

    @Test
    fun `T55 - subsidio de ferias sem nada pago estima o direito do ano`() {
        val est = estimarRecibo(contextoDoSubsidioDeFerias(jaPagoMil = 0L))

        // Nada pago: o direito até ao fim do mês, como antes desta regra (T8).
        assertEquals(direitoFeriasAteAgosto, est.valor("FERIAS"))
    }

    /**
     * T56: a metade do subsídio anual chega para o fechar. Pago 568,99 € (50 %) ou 800,00 € (acima
     * do direito até agosto), a estimativa é 0,00: em Portugal a segunda metade vem no mês seguinte e
     * não no fim do ano, e um valor a mais não pode dar negativo. O valor do recibo do mês prevalece
     * sempre: se o PDF traz o subsídio deste mês, é esse que se escreve, pague-se o que se pagar nos
     * meses anteriores (o estimado não reescreve o real).
     */
    @Test
    fun `T56 - metade ou mais do subsidio anual pago fica a zero e o valor do recibo prevalece`() {
        val metade = estimarRecibo(
            contextoDoSubsidioDeFerias(jaPagoMil = metadeDoSubsidioAnual)
        )
        assertEquals(0L, metade.valor("FERIAS"))

        val acima = estimarRecibo(contextoDoSubsidioDeFerias(jaPagoMil = 8_000_000L))
        assertEquals(0L, acima.valor("FERIAS"))

        val doRecibo = estimarRecibo(
            contextoDoSubsidioDeFerias(jaPagoMil = direitoFeriasAteAgosto, feriasMil = 320_000L)
        )
        assertEquals(320_000L, doRecibo.valor("FERIAS"))
    }

    /**
     * T57: a fronteira dos 50 %, ao cêntimo. Um cêntimo abaixo de metade do subsídio anual ainda é
     * uma prestação a meio e mostra o remanescente (757,61 − 568,98 = 188,63 €); em metade exata o
     * subsídio fecha (T56). É o caso muito hipotético de quem recebeu menos de metade — a norma é
     * metade-metade.
     */
    @Test
    fun `T57 - um centimo abaixo de metade ainda mostra o remanescente`() {
        val abaixo = estimarRecibo(contextoDoSubsidioDeFerias(jaPagoMil = 5_689_800L))
        assertEquals(1_886_341L, abaixo.valor("FERIAS"))
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
        // 8h x 65 700 x 2 = 525 600 x 2 = 1 051 200 (unidades de 1/10 000 EUR = 105,12 EUR)
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

    /**
     * T32 (2026-10-21, auditoria ponto 2): sem nada editado no telemóvel, um dia do PDF de
     * 08:00-20:00 (12h) não gera horas extra. O catálogo só tem o "Tarde 8h" (13h-21h), que é
     * o tipo derivado do PDF; até aqui a régua era a duração desse tipo — 8h — e o estimador
     * inventava 4h de HSUP_DN num mês em que o PDF é a própria escala.
     */
    @Test
    fun `T32 - turno de 12h do PDF sem tipo correspondente nao inventa horas extra`() {
        val setembro = YearMonth.of(2026, 9)
        val dia = DiaReal(
            data = setembro.atDay(1).toEpochDay(),
            tipoTurnoId = null,             // o importador do PDF grava sempre null
            inicioMin = 8 * 60,
            fimMin = 20 * 60,
            pausaMin = 0,
            origem = ORIGEM_PDF
        )
        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = listOf(dia),
            deEpochDay = dia.data,
            ateEpochDay = dia.data,
            aplicacoes = emptyList(),       // contrato PDF não tem rotação nenhuma
            tiposTurno = listOf(tipoTrabalho)
        )
        // A régua é o turno impresso no PDF, não os 8h do "Tarde 8h".
        assertEquals(12 * 60, projecao.single().minutosPrevistos)

        val est = estimarRecibo(
            contexto(setembro, listOf(dia), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        assertEquals(0L, est.valor("HSUP_DN"))
        assertEquals(0L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("HSUP_DN_FER"))
        assertEquals(0L, est.valor("HNOT"))
        // Sem registo MANUAL não há horas em falta nenhumas.
        assertEquals(0L, est.valor("ATRASO"))
    }

    /**
     * T33: o mesmo para um turno do PDF que atravessa a meia-noite (22:00-08:00). Não há horas
     * extra — o dia rendeu exatamente o previsto — e o HNOT só apanha a janela noturna do turno,
     * que aqui são as 8h das 22h às 6h (janela moderna, admissão em 2020).
     */
    @Test
    fun `T33 - turno noturno de 10h do PDF nao da horas extra e o HNOT so conta a janela`() {
        val setembro = YearMonth.of(2026, 9)
        val dia = DiaReal(
            data = setembro.atDay(1).toEpochDay(),
            tipoTurnoId = null,
            inicioMin = 22 * 60,
            fimMin = 8 * 60,
            pausaMin = 0,
            origem = ORIGEM_PDF
        )
        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = listOf(dia),
            deEpochDay = dia.data,
            ateEpochDay = dia.data,
            aplicacoes = emptyList(),
            tiposTurno = listOf(tipoTrabalho)
        )
        // 22:00 -> 08:00 são 600 min; a pausa não entra, mas não há pausa.
        assertEquals(10 * 60, projecao.single().minutosPrevistos)

        val est = estimarRecibo(
            contexto(setembro, listOf(dia), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        assertEquals(0L, est.valor("HSUP_DN"))
        assertEquals(0L, est.valor("HSUP_NT"))
        // 480 min (22h-06h) x 6,57 EUR x 1/4 / 60 = 13,14 EUR.
        assertEquals(131_400L, est.valor("HNOT"))
    }

    /**
     * T34 (fase 29): um dia do PDF que o utilizador editou passa a linha MANUAL com as horas
     * reais, mas a régua das horas extra e dos atrasos continua a ser o turno do PDF, agora nas
     * colunas `previsto*`. O PDF previa 12h, fez 10h: zero horas extra e 2h em falta — e não as
     * 8h do "Tarde 8h" que é o tipo derivado do dia.
     */
    @Test
    fun `T34 - dia do PDF editado mede extras e atrasos pelo previsto`() {
        val setembro = YearMonth.of(2026, 9)
        val editado = DiaReal(
            data = setembro.atDay(1).toEpochDay(),
            tipoTurnoId = tipoTrabalho.id,   // o utilizador registou o turno da tarde
            inicioMin = 8 * 60,
            fimMin = 18 * 60,                // 10h reais
            pausaMin = 0,
            origem = "MANUAL",
            previstoInicioMin = 8 * 60,
            previstoFimMin = 20 * 60,        // o PDF previa 12h
            previstoPausaMin = 0
        )
        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = listOf(editado),
            deEpochDay = editado.data,
            ateEpochDay = editado.data,
            aplicacoes = emptyList(),
            tiposTurno = listOf(tipoTrabalho)
        )
        assertEquals(12 * 60, projecao.single().minutosPrevistos)

        val est = estimarRecibo(
            contexto(setembro, listOf(editado), projecao = projecao)
                .copy(tiposTurno = listOf(tipoTrabalho))
        )

        assertEquals(0L, est.valor("HSUP_DN"))
        assertEquals(0L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("HNOT"))
        // 2h em falta × 65 700 / 60 = 131 400 (a rubrica é informativa; o corte faz-se no VENC).
        assertEquals(131_400L, est.valor("ATRASO"))
        assertEquals(11_379_800L - 131_400L, est.valor("VENC"))
    }

    /**
     * T35 (fase 29, auditoria ponto 2): folga do PDF trabalhada depois de editada. O registo diz
     * que se trabalhou 8h num turno de trabalho, mas o previsto do PDF é 0h — logo o dia é
     * descanso e paga DESC_DESC a 200% (8h × 65 700 × 2). A régua das HSUP_DESC não muda nesta
     * tarefa: sem horas previstas, o teto continua a ser as 8h do tipo do registo.
     */
    @Test
    fun `T35 - folga do PDF trabalhada e editada continua a pagar DESC_DESC`() {
        val setembro = YearMonth.of(2026, 9)
        val folgaTipo = TipoTurno(
            id = 99L,
            nome = "Folga",
            abreviatura = "F",
            cor = 0xFF7F8C8DL,
            inicioMin = 0,
            fimMin = 0,
            pausaMin = 0,
            categoria = CategoriaTurno.FOLGA,
            ativo = true
        )
        val trabalhadoNaFolga = DiaReal(
            data = setembro.atDay(5).toEpochDay(),
            tipoTurnoId = tipoTrabalho.id,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            origem = "MANUAL",
            previstoInicioMin = 0,
            previstoFimMin = 0,
            previstoPausaMin = 0
        )
        val tipos = listOf(tipoTrabalho, folgaTipo)
        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = listOf(trabalhadoNaFolga),
            deEpochDay = trabalhadoNaFolga.data,
            ateEpochDay = trabalhadoNaFolga.data,
            aplicacoes = emptyList(),
            tiposTurno = tipos
        )
        assertEquals(
            "previsto de 0h é folga, apesar do tipo de trabalho no registo",
            folgaTipo.id,
            projecao.single().tipoTurnoId
        )

        val est = estimarRecibo(
            contexto(setembro, listOf(trabalhadoNaFolga), projecao = projecao)
                .copy(tiposTurno = tipos)
        )

        // 8h × 65 700 × 2 = 1 051 200 unidades de 1/10 000 € — 105,12 €.
        assertEquals(1_051_200L, est.valor("DESC_DESC"))
        // Exatamente as 8h da jornada: nada passa para as HSUP de descanso.
        assertEquals(0L, est.valor("HSUP_DN_DESC"))
        assertEquals(0L, est.valor("HSUP_NT_DESC"))
        assertEquals(0L, est.valor("ATRASO"))
    }

    /**
     * T36 (fase 29, auditoria: as folgas do PDF não existem como linha no dia_real): mês do PDF
     * com os dias de trabalho importados — e só esses, como a importação os produz — mais uma
     * folga trabalhada registada de raiz no telemóvel, no sábado que o PDF não lista. Não há
     * linha com `previsto*`: o único caso real de folga trabalhada é este. É a projeção que tem
     * de dizer que o dia era folga, senão o registo não paga os 200% do DESC_DESC.
     */
    @Test
    fun `T36 - folga trabalhada num dia que o PDF nao lista paga DESC_DESC`() {
        val setembro = YearMonth.of(2026, 9)
        val folgaTipo = tipoFolga()
        val doPdf = turnosDoPdfNoMes(setembro)
        // 5 de setembro de 2026 é sábado: o PDF não tem linha nenhuma para este dia.
        val trabalhouNaFolga = DiaReal(
            data = LocalDate.of(2026, 9, 5).toEpochDay(),
            tipoTurnoId = null,              // registo de raiz: sem tipo, sem previsto
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            origem = "MANUAL"
        )
        val tipos = listOf(tipoTrabalho, folgaTipo)

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = doPdf + trabalhouNaFolga,
            deEpochDay = setembro.atDay(1).toEpochDay(),
            ateEpochDay = setembro.atEndOfMonth().toEpochDay(),
            aplicacoes = emptyList(),
            tiposTurno = tipos
        )
        val folgaProjetada = projecao.first { it.epochDay == trabalhouNaFolga.data }
        assertEquals("o dia sem linha do PDF é folga", folgaTipo.id, folgaProjetada.tipoTurnoId)
        assertEquals(0, folgaProjetada.minutosPrevistos)

        val est = estimarRecibo(
            contexto(setembro, doPdf + trabalhouNaFolga, projecao = projecao)
                .copy(tiposTurno = tipos)
        )

        // 8h × 65 700 × 2 = 1 051 200 unidades de 1/10 000 €, ou seja 105,12 €.
        assertEquals(1_051_200L, est.valor("DESC_DESC"))
        // A jornada é só as 8h da folga: nada passa para os suplementos de descanso.
        assertEquals(0L, est.valor("HSUP_DN_DESC"))
        assertEquals(0L, est.valor("HSUP_NT_DESC"))
        assertEquals(0L, est.valor("HSUP_DN"))
        assertEquals(0L, est.valor("HSUP_NT"))
        assertEquals(0L, est.valor("ATRASO"))
        // Os dias úteis continuam a ser os 22 do PDF: o subsídio não ganha um dia.
        assertEquals(1_727_000L, est.valor("SUP_ALIM"))
        assertEquals(11_379_800L, est.valor("VENC"))
    }

    /**
     * T37: as mesmas folgas implícitas, sem ninguém as ter trabalhado, não mexem em nada. A
     * projeção ganha dias (todos os que o PDF não lista), mas o recibo fica igual ao que saía
     * quando só os dias com linha estavam na projeção — é isso que torna a correção segura.
     */
    @Test
    fun `T37 - folgas implicitas do PDF nao mudam o recibo sem registo`() {
        val setembro = YearMonth.of(2026, 9)
        val folgaTipo = tipoFolga()
        val doPdf = turnosDoPdfNoMes(setembro)
        val tipos = listOf(tipoTrabalho, folgaTipo)

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = doPdf,
            deEpochDay = setembro.atDay(1).toEpochDay(),
            ateEpochDay = setembro.atEndOfMonth().toEpochDay(),
            aplicacoes = emptyList(),
            tiposTurno = tipos
        )
        // A projeção de antes desta correção: só os dias com linha do PDF.
        val diasComLinha = doPdf.map { it.data }.toSet()
        val projecaoAntiga = projecao.filter { it.epochDay in diasComLinha }

        val comFolgas = estimarRecibo(
            contexto(setembro, doPdf, projecao = projecao).copy(tiposTurno = tipos)
        )
        val antesDaCorrecao = estimarRecibo(
            contexto(setembro, doPdf, projecao = projecaoAntiga).copy(tiposTurno = tipos)
        )

        assertEquals(
            "nenhuma rubrica muda: as folgas não trabalham nem dão dias úteis",
            antesDaCorrecao.rubricas.map { it.codigo to it.valorMil },
            comFolgas.rubricas.map { it.codigo to it.valorMil }
        )
        assertEquals(1_727_000L, comFolgas.valor("SUP_ALIM"))
        assertEquals(11_379_800L, comFolgas.valor("VENC"))
        assertEquals(0L, comFolgas.valor("DESC_DESC"))
        assertEquals(0L, comFolgas.valor("ATRASO"))
    }

    /** T38: sem uma única linha do PDF no mês não há folgas implícitas — projeção vazia. */
    @Test
    fun `T38 - mes sem linhas do PDF continua sem projecao`() {
        val outubro = YearMonth.of(2026, 10)
        val manual = DiaReal(
            data = outubro.atDay(1).toEpochDay(),
            tipoTurnoId = tipoTrabalho.id,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            origem = "MANUAL"
        )

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = listOf(manual),
            deEpochDay = outubro.atDay(1).toEpochDay(),
            ateEpochDay = outubro.atEndOfMonth().toEpochDay(),
            aplicacoes = emptyList(),
            tiposTurno = listOf(tipoTrabalho, tipoFolga())
        )
        assertTrue("sem PDF não há escala para projetar", projecao.isEmpty())

        // E o dia não é descanso nenhum: sem projeção não há DESC_DESC.
        val est = estimarRecibo(contexto(outubro, listOf(manual), projecao = projecao))
        assertEquals(0L, est.valor("DESC_DESC"))
    }

    /**
     * T39 a T41: Despacho n.º 12523-A/2026 no estimador. Novembro de 2026 no Continente reduz o
     * D02 a metade; os subsídios de Natal e de férias pagos nesse mês incidem SS e têm retenção de
     * IRS autónoma (base própria, somada ao D02), com o abono espelhado para o líquido fechar.
     */
    @Test
    fun `T39 - novembro de 2026 aplica a reducao de 50 por cento ao D02`() {
        val novembro = YearMonth.of(2026, 11)
        val est = estimarRecibo(contexto(novembro, dias(novembro, 21)))

        // Mês completo (21 dias úteis de novembro): base 1 190,07 €.
        assertEquals(11_379_800L, est.valor("VENC"))
        assertEquals(1_648_500L, est.valor("SUP_ALIM"))   // 21 × 7,85 €
        assertEquals(520_900L, est.valor("SUP_TRAN"))
        assertEquals(1_309_077L, est.valor("D01"))        // 11 % de 1 190,07 €
        // Retenção normal 512 548 (T1), reduzida a metade pelo Despacho.
        assertEquals(256_274L, est.valor("D02"))
        assertEquals(113_798L, est.valor("D04"))
        assertEquals(13_549_200L, est.totalAbonos)
        assertEquals(1_679_149L, est.totalDescontos)
        assertEquals(11_870_051L, est.liquido)
    }

    @Test
    fun `T40 - Natal e ferias pagos em novembro de 2026 incidem SS e IRS autonomo`() {
        val novembro = YearMonth.of(2026, 11)
        val subsidio = 11_379_800L
        val est = estimarRecibo(
            contexto(novembro, dias(novembro, 21)).copy(
                natalMil = subsidio,
                feriasMil = subsidio
            )
        )

        // Os abonos aparecem na coluna Estimado: sem eles o líquido descontava um IRS e uma SS sem
        // os subsídios. O catálogo agora diz que ambos incidem — é o que os põe nas bases.
        assertEquals(subsidio, est.valor("NATAL"))
        assertEquals(subsidio, est.valor("FERIAS"))
        // SS do mês + SS dos dois subsídios: 11 % de (1 190,07 + 2 × 1 137,98).
        assertEquals(3_812_633L, est.valor("D01"))
        // D02 mensal 256 274 + IRS autónomo de cada subsídio 205 465
        // (410 929 × 50 %, com os 2 dependentes do contrato).
        assertEquals(667_204L, est.valor("D02"))
        assertEquals(113_798L, est.valor("D04"))
        assertEquals(36_308_800L, est.totalAbonos)
        assertEquals(4_593_635L, est.totalDescontos)
        assertEquals(31_715_165L, est.liquido)
    }

    @Test
    fun `T41 - ferias pagas em novembro de 2026 tem IRS autonomo reduzido a metade`() {
        val subsidio = 11_379_800L
        val novembro = YearMonth.of(2026, 11)
        val dezembro = YearMonth.of(2026, 12)

        // O mesmo mês sem o subsídio é a régua: em dezembro o Natal estimado entra dos dois lados
        // e desaparece na subtração.
        val novembroSemSubsidio = estimarRecibo(contexto(novembro, dias(novembro, 21)))
        val emNovembro = estimarRecibo(
            contexto(novembro, dias(novembro, 21)).copy(feriasMil = subsidio)
        )
        val dezembroSemFerias = estimarRecibo(contexto(dezembro, dias(dezembro, 22)))
        val emDezembro = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22)).copy(feriasMil = subsidio)
        )

        // O valor do recibo prevalece sobre a estimativa e aparece na coluna Estimado.
        assertEquals(subsidio, emNovembro.valor("FERIAS"))
        assertEquals(subsidio, emDezembro.valor("FERIAS"))

        // SS do subsídio: 11 % de 1 137,98 (125,1778 EUR) nos dois meses — paga SS no mês em que é
        // pago.
        assertEquals(1_251_778L, emNovembro.valor("D01") - novembroSemSubsidio.valor("D01"))
        assertEquals(1_251_778L, emDezembro.valor("D01") - dezembroSemFerias.valor("D01"))

        // IRS autónomo do subsídio, isolado subtraindo o D02 do mês sem ele: 410 929 pela
        // tabela normal e 205 465 com a redução de 50 % do Despacho n.º 12523-A/2026
        // (410 929 × 50 % = 205 464,5, arredondado ao cêntimo para cima).
        val irsFeriasNovembro = emNovembro.valor("D02") - novembroSemSubsidio.valor("D02")
        val irsFeriasDezembro = emDezembro.valor("D02") - dezembroSemFerias.valor("D02")
        assertEquals(410_929L, irsFeriasDezembro)
        assertEquals(205_465L, irsFeriasNovembro)
        assertTrue(
            "novembro é metade do normal, a menos do arredondamento ao cêntimo",
            abs(irsFeriasNovembro * 2 - irsFeriasDezembro) <= 1L
        )
    }

    /**
     * T42 a T48: o subsídio de Natal **estimado**. Regra do utilizador (2026-11-21): 1 mês de
     * vencimento base para quem trabalhou o ano civil inteiro; fora disso, proporcional aos DIAS de
     * serviço no ano (365 ou 366), com os dias de BAIXA descontados
     * (`calcularSubsidioProporcional`). Base = vencimento base do CCT, sem diuturnidades,
     * alimentação nem horas extra.
     */
    @Test
    fun `T42 - Natal de dezembro de quem trabalhou o ano todo e o vencimento base`() {
        val dezembro = YearMonth.of(2026, 12)
        val est = estimarRecibo(contexto(dezembro, dias(dezembro, 22)))

        // 365 dias de serviço em 365: 1 mês de vencimento base. Sem baixa, o ano inteiro não passa
        // pela divisão.
        assertEquals(11_379_800L, est.valor("NATAL"))
        // Paga SS no mês em que é pago: 11 % de (1 137,98 + 52,09 + 1 137,98).
        assertEquals(2_560_855L, est.valor("D01"))
        // IRS autónomo: 512 548 do mês + 410 929 do subsídio (tabela normal em dezembro).
        assertEquals(923_477L, est.valor("D02"))
        assertEquals(113_798L, est.valor("D04"))
        // O abono entra no total: 1 137,98 + 172,70 + 52,09 + 1 137,98.
        assertEquals(25_007_500L, est.totalAbonos)
    }

    @Test
    fun `T43 - admitido a 1 de julho recebe cerca de metade do Natal em dezembro`() {
        val dezembro = YearMonth.of(2026, 12)
        val est = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22), dataAdmissao = LocalDate.of(2026, 7, 1))
        )

        // 1 de julho a 31 de dezembro são 184 dias dos 365 do ano: 1 137,98 × 184 ÷ 365 = 573,6666
        // EUR — um pouco acima da metade, que seriam 182,5 dias.
        assertEquals(5_736_666L, est.valor("NATAL"))
        assertEquals(1_940_110L, est.valor("D01"))   // 11 % de (1 137,98 + 52,09 + 573,67)
        // 573,67 EUR fica abaixo do 1.º escalão da tabela I (920 EUR): sem retenção no subsídio.
        assertEquals(512_548L, est.valor("D02"))
        assertEquals(113_798L, est.valor("D04"))
        // 1 137,98 + 172,70 + 52,09 + 573,67.
        assertEquals(19_364_366L, est.totalAbonos)
    }

    @Test
    fun `T44 - admissao a meio de julho conta os dias a partir da admissao`() {
        val dezembro = YearMonth.of(2026, 12)
        val est = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22), dataAdmissao = LocalDate.of(2026, 7, 15))
        )

        // De 15 de julho a 31 de dezembro são 170 dias: 1 137,98 × 170 ÷ 365 = 530,0181 EUR.
        assertEquals(5_300_181L, est.valor("NATAL"))
        assertEquals(1_892_097L, est.valor("D01"))
        // Sem data de admissão não se inventa um subsídio (o mesmo mês, contexto sem data).
        val semData = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22)).copy(dataAdmissao = null)
        )
        assertEquals(0L, semData.valor("NATAL"))
        assertEquals(1_309_077L, semData.valor("D01"))   // só o mês: 11 % de 1 190,07
    }

    /**
     * O caso que motivou a regra: cinco meses de baixa cortam quase metade do Natal. De 1 de
     * janeiro a 31 de maio são 151 dias de calendário; sobram 214 dos 365 do ano.
     */
    @Test
    fun `T45 - baixa de janeiro a maio deixa o Natal em cerca de sete doze avos`() {
        val dezembro = YearMonth.of(2026, 12)
        val baixa = Ausencia(
            id = 45,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = LocalDate.of(2026, 1, 1).toEpochDay(),
            dataFim = LocalDate.of(2026, 5, 31).toEpochDay()
        )
        val est = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22), ausencias = listOf(baixa))
                .copy(tiposTurno = listOf(tipoTrabalho, tipoBaixa))
        )

        // 214 ÷ 365 = 58,63 % de 1 137,98 € = 667,1992 € (os 7/12 seriam 663,82 €: a contagem por
        // dias é a que a empresa usa).
        assertEquals(6_671_992L, est.valor("NATAL"))
        assertEquals(2_042_996L, est.valor("D01"))   // 11 % de (1 137,98 + 52,09 + 667,20)
        // 667,20 EUR abaixo do 1.º escalão: o D02 é só o do mês.
        assertEquals(512_548L, est.valor("D02"))
        assertEquals(20_299_692L, est.totalAbonos)
    }

    /**
     * O caso real do utilizador: baixa de 18/07/2025 a 19/05/2026. Em 2026 a baixa cobre 1 de
     * janeiro a 19 de maio (139 dias) e sobram 226 dos 365 — o resto do ano é serviço normal.
     */
    @Test
    fun `T46 - baixa a cavalo de dois anos so pesa no ano a que pertence`() {
        val dezembro = YearMonth.of(2026, 12)
        val baixa = Ausencia(
            id = 46,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = LocalDate.of(2025, 7, 18).toEpochDay(),
            dataFim = LocalDate.of(2026, 5, 19).toEpochDay()
        )
        val est = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22), ausencias = listOf(baixa))
                .copy(tiposTurno = listOf(tipoTrabalho, tipoBaixa))
        )

        // 226 ÷ 365 = 61,92 % de 1 137,98 € = 704,6123 € — e não os 1 137,98 € do ano inteiro.
        assertEquals(7_046_123L, est.valor("NATAL"))
        assertEquals(2_084_151L, est.valor("D01"))   // 11 % de (1 137,98 + 52,09 + 704,61)
        assertEquals(512_548L, est.valor("D02"))
    }

    /**
     * As férias do ano contam como serviço — é assim que o VENC as trata e é o que a lei manda —,
     * por isso não cortam o subsídio. E duas baixas sobrepostas contam os dias uma vez só.
     */
    @Test
    fun `T47 - ferias nao cortam o subsidio e baixas sobrepostas contam uma vez`() {
        val dezembro = YearMonth.of(2026, 12)
        val feriasDeAgosto = Ausencia(
            id = 47,
            tipoTurnoId = TIPO_FERIAS,
            dataInicio = LocalDate.of(2026, 8, 10).toEpochDay(),
            dataFim = LocalDate.of(2026, 8, 24).toEpochDay()
        )
        val soFerias = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22), ausencias = listOf(feriasDeAgosto))
                .copy(tiposTurno = listOf(tipoTrabalho, tipoFerias, tipoBaixa))
        )
        // 22 dias de férias não tiram um cêntimo ao Natal: continua o ano inteiro.
        assertEquals(11_379_800L, soFerias.valor("NATAL"))

        // Duas baixas que se sobrepõem de 15 a 31 de março contam como uma só: 151 dias, como no T45.
        val primeira = Ausencia(
            id = 48,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = LocalDate.of(2026, 1, 1).toEpochDay(),
            dataFim = LocalDate.of(2026, 3, 31).toEpochDay()
        )
        val segunda = Ausencia(
            id = 49,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = LocalDate.of(2026, 3, 15).toEpochDay(),
            dataFim = LocalDate.of(2026, 5, 31).toEpochDay()
        )
        val comBaixas = estimarRecibo(
            contexto(dezembro, dias(dezembro, 22), ausencias = listOf(primeira, segunda))
                .copy(tiposTurno = listOf(tipoTrabalho, tipoBaixa))
        )
        assertEquals(6_671_992L, comBaixas.valor("NATAL"))
    }

    /**
     * Ano bissexto: o divisor é 366. Sem baixa continua a ser o vencimento base; com os mesmos
     * cinco meses de baixa sobram 214 dias, mas em 2028 a baixa cobre 152 (fevereiro tem 29) e
     * 214/366 dá menos do que 214/365.
     */
    @Test
    fun `T48 - ano bissexto divide por 366 dias`() {
        val admissao = LocalDate.of(2020, 1, 1)
        val baixa = Ausencia(
            id = 50,
            tipoTurnoId = TIPO_BAIXA,
            dataInicio = LocalDate.of(2028, 1, 1).toEpochDay(),
            dataFim = LocalDate.of(2028, 5, 31).toEpochDay()
        )

        // 1 137,98 × 214 ÷ 366 = 665,3763 EUR (o mesmo caso do T45 daria 667,20 EUR em 2026).
        assertEquals(
            6_653_763L,
            calcularSubsidioProporcional(
                baseMil = 11_379_800L,
                dataAdmissao = admissao,
                ano = 2028,
                mesPagamento = 12,
                ausencias = listOf(baixa),
                tiposTurno = listOf(tipoBaixa)
            )
        )
        // Sem baixa e com o ano inteiro continua a ser o vencimento base, mesmo em ano bissexto.
        assertEquals(
            11_379_800L,
            calcularSubsidioProporcional(11_379_800L, admissao, 2028, 12)
        )
    }

    /**
     * v2.2.0: o detalhe do IRS viaja com a estimativa — é dele que a conferência do recibo tira o
     * "IRS (4,31%)" do título da rubrica. A base mensal de 1 190,07 € cai no escalão de 21,2 % da
     * tabela I (ordem 4 da semente, escalão 5 na numeração do XLSX).
     */
    @Test
    fun `T49 - a estimativa leva o escalao e a taxa do IRS`() {
        val dezembro = YearMonth.of(2026, 12)
        val detalhe = estimarRecibo(contexto(dezembro, dias(dezembro, 22))).detalheIrs!!

        assertEquals(5, detalhe.numeroEscalao)
        assertEquals(21_200, detalhe.taxaBasisPoints)
        // A taxa que a linha do D02 mostra: 51,2548 € sobre 1 190,07 € = 4,31 % (431 em 0,01 %).
        assertEquals(431, detalhe.taxaEfetivaBasisPoints)
        assertEquals(512_548L, detalhe.valorMil)
        // Os 410,93 € do IRS autónomo do Natal somam-se ao D02 mas não mexem na taxa: é o IRS da
        // base mensal, e é essa a taxa efetiva que o recibo mostra ao lado do vencimento. O valor
        // autónomo viaja no detalhe para a conferência poder dizer que a linha o inclui (v2.2.0).
        assertEquals(431, detalhe.taxaEfetivaBasisPoints)
        assertEquals(410_929L, detalhe.irsAutonomoSubsidiosMil)
        assertEquals(923_477L, detalhe.valorMil + detalhe.irsAutonomoSubsidiosMil)
        assertTrue("dezembro não tem a redução de novembro", !detalhe.reducaoNovembro2026)
        assertEquals(null, detalhe.percentagemIrsJovem)
    }

    /** Novembro de 2026: a mesma base, com a redução de 50 % assinalada no detalhe. */
    @Test
    fun `T50 - em novembro de 2026 o detalhe assinala a reducao`() {
        val novembro = YearMonth.of(2026, 11)
        val detalhe = estimarRecibo(contexto(novembro, dias(novembro, 22))).detalheIrs!!

        assertEquals(256_274L, detalhe.valorMil)
        assertEquals(5, detalhe.numeroEscalao)
        assertEquals(21_200, detalhe.taxaBasisPoints)
        // A efetiva acompanha o valor reduzido: 25,6274 € sobre 1 190,07 € = 2,15 %.
        assertEquals(215, detalhe.taxaEfetivaBasisPoints)
        // Novembro não paga subsídios: não há IRS autónomo somado ao D02.
        assertEquals(0L, detalhe.irsAutonomoSubsidiosMil)
        assertTrue("novembro de 2026 no Continente tem a redução de 50 %", detalhe.reducaoNovembro2026)
    }


    /** Tipo de folga como a app o semeia, para a projeção poder marcar dias de descanso. */
    private fun tipoFolga() = TipoTurno(
        id = 99L,
        nome = "Folga",
        abreviatura = "F",
        cor = 0xFF7F8C8DL,
        inicioMin = 0,
        fimMin = 0,
        pausaMin = 0,
        categoria = CategoriaTurno.FOLGA,
        ativo = true
    )

    /**
     * O que a importação do PDF produz num mês: uma linha por dia útil com horas (13h-21h, sem
     * tipo e com origem PDF). As folgas não geram linha nenhuma — daí a correção de T36.
     */
    private fun turnosDoPdfNoMes(mes: YearMonth): List<DiaReal> =
        (1..mes.lengthOfMonth()).mapNotNull { numero ->
            val data = mes.atDay(numero)
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

    private fun EstimativaRecibo.valor(codigo: String): Long =
        rubricas.first { it.codigo == codigo }.valorMil
}
