package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fase 19 — modo de escala por PDF mensal. Prova duas coisas:
 *
 * 1. Com o contrato em PDF_MENSAL a grelha do calendário passa a vir dos dia_real importados do
 *    PDF (e a rotação deixa de contar), incluindo o estado vazio quando não há PDF no mês.
 * 2. O chip do dia vem de [tipoDerivadoDoPdf], porque o importador do PDF grava sempre
 *    `tipoTurnoId = null` (guarda só horas e posto — inputs, não valores calculados).
 *
 * A composição do chip é a que o EscalaViewModel faz: para cada dia devolvido por
 * [diasProjetadosPara], derivar o tipo a partir do dia_real correspondente.
 */
class EscalaModoPdfTest {

    private val T08H = 100L   // tipo de trabalho 13:00-21:00
    private val FOLGA = 200L  // tipo de folga

    private val tipos = listOf(
        TipoTurno(
            id = T08H,
            nome = "Tarde 8h",
            abreviatura = "T08H",
            cor = 0xFFC0392BL,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            categoria = CategoriaTurno.TRABALHO
        ),
        TipoTurno(
            id = FOLGA,
            nome = "Folga",
            abreviatura = "F",
            cor = 0xFF1ABC9CL,
            inicioMin = 0,
            fimMin = 0,
            pausaMin = 0,
            categoria = CategoriaTurno.FOLGA
        )
    )

    private val setembro = YearMonth.of(2026, 9)

    /** Rotação em vigor o ano todo: só serve para provar que no modo PDF é ignorada. */
    private val aplicacao = AplicacaoVigente(
        validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
        validoAte = null,
        dataAncora = LocalDate.of(2026, 1, 1).toEpochDay(),
        slots = listOf(T08H, FOLGA)
    )

    private fun diaPdf(dia: Int, inicioMin: Int, fimMin: Int, posto: String? = null) = DiaReal(
        data = LocalDate.of(2026, 9, dia).toEpochDay(),
        tipoTurnoId = null,   // o importador do PDF grava sempre null
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = 0,
        origem = ORIGEM_PDF,
        posto = posto
    )

    private fun dia(dia: Int, origem: String, inicioMin: Int, fimMin: Int) = DiaReal(
        data = LocalDate.of(2026, 9, dia).toEpochDay(),
        tipoTurnoId = null,
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = 0,
        origem = origem
    )

    private val de = LocalDate.of(2026, 9, 1).toEpochDay()
    private val ate = LocalDate.of(2026, 9, 30).toEpochDay()

    /** O que o ViewModel faz por dia: o chip sai do dia_real do PDF, com o tipo derivado. */
    private fun chips(dias: List<DiaProjetado>, diasReais: List<DiaReal>): List<TipoTurno?> =
        dias.map { dp ->
            tipoDerivadoDoPdf(diasReais.first { it.data == dp.epochDay }, tipos)
        }

    @Test
    fun `T1 - modo PDF mensal mostra os chips dos dias do PDF`() {
        val diasReais = listOf(
            diaPdf(1, 13 * 60, 21 * 60, "CC ES"),
            diaPdf(2, 13 * 60, 21 * 60, "CC ES"),
            diaPdf(3, 7 * 60, 15 * 60, "StaffCrPr ES"),
            // Fora da janela do mês: não pode entrar na grelha.
            DiaReal(
                data = LocalDate.of(2026, 8, 31).toEpochDay(),
                tipoTurnoId = null,
                inicioMin = 13 * 60,
                fimMin = 21 * 60,
                pausaMin = 0,
                origem = ORIGEM_PDF
            ),
            // Registo manual dentro do mês: no modo PDF a grelha é a do PDF, logo fica de fora.
            dia(4, "MANUAL", 9 * 60, 17 * 60)
        )

        val dias = diasProjetadosPara(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = emptyList()
        )

        assertEquals(
            "só os três dias do PDF dentro do mês",
            listOf(1, 2, 3),
            dias.map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }
        )
        assertTrue("o dia_real do PDF não traz tipo: vem null", dias.all { it.tipoTurnoId == null })
        assertEquals(
            "chip nesses três dias, com o tipo derivado das horas",
            listOf<TipoTurno?>(tipos[0], tipos[0], tipos[0]),
            chips(dias, diasReais)
        )

        assertTrue(
            "havendo dias PDF no mês, a escala existe",
            temEscalaAplicada(TIPO_ESCALA_PDF_MENSAL, diasReais, emptyList(), setembro)
        )
    }

    @Test
    fun `T2 - modo PDF mensal sem PDF importado nao tem escala aplicada`() {
        val diasReais = listOf(dia(4, "MANUAL", 9 * 60, 17 * 60))

        val dias = diasProjetadosPara(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = listOf(aplicacao)
        )

        assertTrue("a rotação em vigor é ignorada no modo PDF", dias.isEmpty())
        assertFalse(
            "sem dia PDF no mês, o estado vazio aparece",
            temEscalaAplicada(TIPO_ESCALA_PDF_MENSAL, diasReais, listOf(aplicacao), setembro)
        )

        // Contraste: com a mesma aplicação, o modo de sempre continua a projetar o mês.
        assertEquals(30, diasProjetadosPara(TIPO_ESCALA_ROTACAO, diasReais, de, ate, listOf(aplicacao)).size)
        assertTrue(temEscalaAplicada(TIPO_ESCALA_ROTACAO, diasReais, listOf(aplicacao), setembro))
    }

    @Test
    fun `T3 - tipo derivado do dia do PDF`() {
        // a) horas iguais às de um tipo de trabalho -> esse tipo
        assertEquals(T08H, tipoDerivadoDoPdf(diaPdf(1, 13 * 60, 21 * 60, "CC ES"), tipos)?.id)

        // b) horas sem tipo correspondente -> o primeiro de trabalho
        assertEquals(T08H, tipoDerivadoDoPdf(diaPdf(2, 13 * 60, 22 * 60, "CC ES"), tipos)?.id)

        // c) sem horas -> folga
        assertEquals(FOLGA, tipoDerivadoDoPdf(diaPdf(3, 0, 0), tipos)?.id)

        // Sem nenhum tipo de trabalho na lista fica null: o chip aparece vazio, como antes.
        assertNull(tipoDerivadoDoPdf(diaPdf(4, 13 * 60, 21 * 60), listOf(tipos[1])))
    }
}
