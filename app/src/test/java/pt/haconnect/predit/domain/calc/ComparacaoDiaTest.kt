package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.TipoTurno

class ComparacaoDiaTest {

    private val turnoTrabalho8h = TipoTurno(
        id = 1L,
        nome = "Tarde 8h",
        abreviatura = "T08H",
        cor = 0xFFC0392BL,
        inicioMin = 13 * 60,
        fimMin = 21 * 60,
        pausaMin = 0,
        categoria = CategoriaTurno.TRABALHO
    )

    private val turnoFolga = TipoTurno(
        id = 2L,
        nome = "Folga",
        abreviatura = "F",
        cor = 0xFF1ABC9CL,
        inicioMin = 0,
        fimMin = 0,
        pausaMin = 0,
        categoria = CategoriaTurno.FOLGA
    )

    private val turnoFerias = TipoTurno(
        id = 3L,
        nome = "Férias",
        abreviatura = "FER",
        cor = 0xFF2980B9L,
        inicioMin = 0,
        fimMin = 0,
        pausaMin = 0,
        categoria = CategoriaTurno.FERIAS
    )

    @Test
    fun `dia sem registo, com projecao de trabalho`() {
        val comp = compararProjetadoReal(projetado = turnoTrabalho8h, real = null)

        assertEquals(480, comp.minutosProjetados)
        assertEquals(0, comp.minutosReais)
        assertTrue(comp.temProjecao)
        assertFalse(comp.temRegisto)
        assertEquals(-480, comp.diferenca)
    }

    @Test
    fun `dia com registo igual ao projetado`() {
        val real = DiaReal(
            data = 100L,
            tipoTurnoId = 1L,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0
        )
        val comp = compararProjetadoReal(projetado = turnoTrabalho8h, real = real)

        assertEquals(480, comp.minutosProjetados)
        assertEquals(480, comp.minutosReais)
        assertTrue(comp.temProjecao)
        assertTrue(comp.temRegisto)
        assertEquals(0, comp.diferenca)
    }

    @Test
    fun `dia com registo diferente do projetado - 9h feitas em vez de 8h`() {
        val real9h = DiaReal(
            data = 100L,
            tipoTurnoId = 1L,
            inicioMin = 12 * 60, // 12:00
            fimMin = 21 * 60,    // 21:00 (9 horas)
            pausaMin = 0
        )
        val comp = compararProjetadoReal(projetado = turnoTrabalho8h, real = real9h)

        assertEquals(480, comp.minutosProjetados)
        assertEquals(540, comp.minutosReais)
        assertEquals(60, comp.diferenca)
    }

    @Test
    fun `dia com registo mas sem projecao - trabalhou em dia de folga`() {
        val real8h = DiaReal(
            data = 100L,
            tipoTurnoId = 1L,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0
        )
        val compFolga = compararProjetadoReal(projetado = turnoFolga, real = real8h)

        assertEquals(0, compFolga.minutosProjetados)
        assertEquals(480, compFolga.minutosReais)
        assertEquals(480, compFolga.diferenca)

        val compSemProjecao = compararProjetadoReal(projetado = null, real = real8h)
        assertEquals(0, compSemProjecao.minutosProjetados)
        assertEquals(480, compSemProjecao.minutosReais)
        assertEquals(480, compSemProjecao.diferenca)
    }

    @Test
    fun `dia de ferias com registo real - trabalhou nas ferias`() {
        val real8h = DiaReal(
            data = 100L,
            tipoTurnoId = 1L,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0
        )
        val comp = compararProjetadoReal(projetado = turnoFerias, real = real8h)

        assertEquals(0, comp.minutosProjetados)
        assertEquals(480, comp.minutosReais)
        assertTrue(comp.temRegisto)
        assertEquals(480, comp.diferenca)
    }
}
