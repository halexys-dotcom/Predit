package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.LocalDate

/**
 * Total de horas do rodapé da Escala — [minutosDoDiaNaEscala].
 *
 * O bug que isto fecha: uma ausência (férias, ou baixa no modo PDF) marcada num dia que já era
 * folga somava 8h ao total. A regra antiga olhava para a categoria do tipo efetivo e, num dia de
 * folga com férias, o tipo efetivo passa a ser o da própria ausência — o dia contava sempre 8h.
 *
 * Os testes percorrem o caminho do EscalaViewModel: [diasProjetadosPara] (a fonte dos chips, que no
 * modo PDF só tem os dias com linha), [aplicarAusencias] e, dia a dia, a soma do que
 * [minutosDoDiaNaEscala] devolve. Um dia sem entrada na projeção — as folgas do PDF — conta 0,
 * como na grelha.
 *
 * Setembro de 2026 começa numa terça e não tem feriados, por isso os dias de semana e o fim de
 * semana são previsíveis: 5 e 6 são sábado e domingo.
 */
class EscalaTotalHorasTest {

    private val T8 = TipoTurno(
        id = 100L,
        nome = "Tarde 8h",
        abreviatura = "T8",
        cor = 0xFFC0392BL,
        inicioMin = 8 * 60,
        fimMin = 16 * 60,
        categoria = CategoriaTurno.TRABALHO
    )

    private val T12 = TipoTurno(
        id = 110L,
        nome = "Noite 12h",
        abreviatura = "T12",
        cor = 0xFF34495EL,
        inicioMin = 8 * 60,
        fimMin = 20 * 60,
        categoria = CategoriaTurno.TRABALHO
    )

    private val FOLGA = TipoTurno(
        id = 200L,
        nome = "Folga",
        abreviatura = "F",
        cor = 0xFF1ABC9CL,
        categoria = CategoriaTurno.FOLGA
    )

    private val FERIAS = TipoTurno(
        id = 300L,
        nome = "Férias",
        abreviatura = "FER",
        cor = 0xFFF1C40FL,
        categoria = CategoriaTurno.FERIAS
    )

    private val BAIXA = TipoTurno(
        id = 400L,
        nome = "Baixa",
        abreviatura = "BM",
        cor = 0xFFE74C3CL,
        categoria = CategoriaTurno.BAIXA
    )

    private val tipos = listOf(T8, T12, FOLGA, FERIAS, BAIXA)

    private fun setembro(dia: Int): Long = LocalDate.of(2026, 9, dia).toEpochDay()

    private fun ausencia(tipo: TipoTurno, de: Long, ate: Long = de) =
        Ausencia(tipoTurnoId = tipo.id, dataInicio = de, dataFim = ate)

    /** O que o importador do PDF grava: horas e origem, sem tipo. */
    private fun linhaDoPdf(epochDay: Long, inicioMin: Int, fimMin: Int, pausaMin: Int = 0) = DiaReal(
        data = epochDay,
        tipoTurnoId = null,
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = pausaMin,
        origem = ORIGEM_PDF
    )

    /** Rotação ancorada no primeiro dia da janela, com um tipo por posição do ciclo. */
    private fun rotacao(de: Long, vararg slots: Long) = AplicacaoVigente(
        validoDe = de,
        validoAte = null,
        dataAncora = de,
        slots = slots.toList()
    )

    /** O total do rodapé: projeção do mês, ausências efetivas e a soma de [minutosDoDiaNaEscala]. */
    private fun totalDoRodape(
        tipoEscala: String,
        de: Long,
        ate: Long,
        diasReais: List<DiaReal> = emptyList(),
        ausencias: List<Ausencia> = emptyList(),
        aplicacoes: List<AplicacaoVigente> = emptyList()
    ): Int {
        val categorias = tipos.associate { it.id to it.categoria }
        val mapaTipos = tipos.associateBy { it.id }
        val estadoPorDia = aplicarAusencias(
            diasProjetadosPara(tipoEscala, diasReais, de, ate, aplicacoes),
            ausencias,
            categorias,
            diasReais
        ).associateBy { it.epochDay }

        return (de..ate).sumOf { epochDay ->
            val dia = estadoPorDia[epochDay]
            minutosDoDiaNaEscala(
                categoriaAusenciaEfetiva = dia?.ausenciaEfetiva?.tipoTurnoId?.let { categorias[it] },
                tipoProjetado = dia?.tipoTurnoProjetadoId?.let { mapaTipos[it] },
                tipoEfetivo = dia?.tipoTurnoEfetivoId?.let { mapaTipos[it] },
                diaReal = dia?.diaReal
            )
        }
    }
    // --- rotação ---------------------------------------------------------------------------

    @Test
    fun `T1 - ferias marcadas numa folga de dia de semana nao somam 8h`() {
        val um = setembro(1)   // terça, turno de 8h
        val dois = setembro(2) // quarta, folga

        val total = totalDoRodape(
            TIPO_ESCALA_ROTACAO,
            um,
            dois,
            ausencias = listOf(ausencia(FERIAS, dois)),
            aplicacoes = listOf(rotacao(um, T8.id, FOLGA.id))
        )

        // Antes da correção eram 960: o dia de folga também contava uma jornada de 8h.
        assertEquals(480, total)
    }

    @Test
    fun `T2 - ferias num dia de trabalho de 12h contam 480 como sempre`() {
        val um = setembro(1)

        val total = totalDoRodape(
            TIPO_ESCALA_ROTACAO,
            um,
            um,
            ausencias = listOf(ausencia(FERIAS, um)),
            aplicacoes = listOf(rotacao(um, T12.id))
        )

        // 8h fixas: o dia não passa a valer a duração do turno previsto.
        assertEquals(480, total)
    }

    @Test
    fun `T3 - ausencia do tipo folga num dia de trabalho vale 0`() {
        val um = setembro(1)

        val total = totalDoRodape(
            TIPO_ESCALA_ROTACAO,
            um,
            um,
            ausencias = listOf(ausencia(FOLGA, um)),
            aplicacoes = listOf(rotacao(um, T8.id))
        )

        assertEquals(0, total)
    }

    @Test
    fun `T4 - ferias num fim de semana continuam a valer 0`() {
        val sabado = setembro(5)
        val domingo = setembro(6)

        val total = totalDoRodape(
            TIPO_ESCALA_ROTACAO,
            sabado,
            domingo,
            ausencias = listOf(ausencia(FERIAS, sabado, domingo)),
            aplicacoes = listOf(rotacao(sabado, FOLGA.id))
        )

        assertEquals(0, total)
    }

    @Test
    fun `T5 - dia de trabalho sem ausencia continua a contar as horas do turno`() {
        val um = setembro(1)
        val dois = setembro(2)

        val total = totalDoRodape(
            TIPO_ESCALA_ROTACAO,
            um,
            dois,
            aplicacoes = listOf(rotacao(um, T12.id, T8.id))
        )

        assertEquals(720 + 480, total)
    }

    @Test
    fun `T6 - dia com registo real conta as horas registadas`() {
        val um = setembro(1)
        val registo = DiaReal(
            data = um,
            tipoTurnoId = null,
            inicioMin = 8 * 60,
            fimMin = 20 * 60,
            pausaMin = 60
        )

        val total = totalDoRodape(
            TIPO_ESCALA_ROTACAO,
            um,
            um,
            diasReais = listOf(registo),
            aplicacoes = listOf(rotacao(um, T8.id))
        )

        assertEquals(660, total)
    }

    // --- modo PDF --------------------------------------------------------------------------

    @Test
    fun `T7 - modo PDF ferias num dia sem linha do PDF valem 0`() {
        val um = setembro(1)
        val tres = setembro(3)

        val total = totalDoRodape(
            TIPO_ESCALA_PDF_MENSAL,
            um,
            tres,
            diasReais = listOf(linhaDoPdf(um, 8 * 60, 16 * 60)),
            ausencias = listOf(ausencia(FERIAS, tres))
        )

        // Só o dia 1 tem linha do PDF: o dia 3 não era dia de trabalho previsto.
        assertEquals(480, total)
    }

    @Test
    fun `T8 - modo PDF baixa num dia sem linha do PDF vale 0`() {
        val um = setembro(1)
        val tres = setembro(3)

        val total = totalDoRodape(
            TIPO_ESCALA_PDF_MENSAL,
            um,
            tres,
            diasReais = listOf(linhaDoPdf(um, 8 * 60, 16 * 60)),
            ausencias = listOf(ausencia(BAIXA, tres))
        )

        assertEquals(480, total)
    }

    @Test
    fun `T9 - modo PDF ferias num dia com linha de 8h contam 480`() {
        val um = setembro(1)

        val total = totalDoRodape(
            TIPO_ESCALA_PDF_MENSAL,
            um,
            um,
            diasReais = listOf(linhaDoPdf(um, 8 * 60, 16 * 60)),
            ausencias = listOf(ausencia(FERIAS, um))
        )

        assertEquals(480, total)
    }

    @Test
    fun `T10 - modo PDF ferias num dia do PDF de 12h contam 8h e nao as horas da linha`() {
        val um = setembro(1)

        val total = totalDoRodape(
            TIPO_ESCALA_PDF_MENSAL,
            um,
            um,
            diasReais = listOf(linhaDoPdf(um, 8 * 60, 20 * 60)),
            ausencias = listOf(ausencia(FERIAS, um))
        )

        // 720 seriam as horas da linha; a ausência vale sempre uma jornada de 8h.
        assertEquals(480, total)
    }

    @Test
    fun `T11 - modo PDF folga trabalhada com ferias continua a valer 0`() {
        val um = setembro(1)
        // O PDF dizia folga (previsto 0h) e o utilizador registou as 8h que fez: a linha passou a
        // MANUAL mas guarda o previsto, por isso o dia continua a não ser de trabalho previsto.
        val folgaTrabalhada = DiaReal(
            data = um,
            tipoTurnoId = T8.id,
            inicioMin = 8 * 60,
            fimMin = 16 * 60,
            pausaMin = 0,
            origem = "MANUAL",
            previstoInicioMin = 0,
            previstoFimMin = 0,
            previstoPausaMin = 0
        )

        val total = totalDoRodape(
            TIPO_ESCALA_PDF_MENSAL,
            um,
            um,
            diasReais = listOf(folgaTrabalhada),
            ausencias = listOf(ausencia(FERIAS, um))
        )

        assertEquals(0, total)
    }

    @Test
    fun `T12 - modo PDF dia editado com previsto de 12h conta 8h de ferias`() {
        val um = setembro(1)
        val editado = DiaReal(
            data = um,
            tipoTurnoId = T12.id,
            inicioMin = 8 * 60,
            fimMin = 20 * 60,
            pausaMin = 0,
            origem = "MANUAL",
            previstoInicioMin = 8 * 60,
            previstoFimMin = 20 * 60,
            previstoPausaMin = 0
        )

        val total = totalDoRodape(
            TIPO_ESCALA_PDF_MENSAL,
            um,
            um,
            diasReais = listOf(editado),
            ausencias = listOf(ausencia(FERIAS, um))
        )

        assertEquals(480, total)
    }

}
