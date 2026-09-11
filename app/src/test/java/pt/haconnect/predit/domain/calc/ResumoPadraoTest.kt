package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.TipoTurno

class ResumoPadraoTest {

    private val tipoTarde = TipoTurno(id = 1, nome = "Tarde 8h", abreviatura = "T08H", cor = 0xFFC0392BL, categoria = CategoriaTurno.TRABALHO)
    private val tipoFolga = TipoTurno(id = 2, nome = "Folga", abreviatura = "F", cor = 0xFF1ABC9CL, categoria = CategoriaTurno.FOLGA)
    private val mapa = mapOf(1L to tipoTarde, 2L to tipoFolga)

    @Test
    fun `gerar resumo para ciclo 4 mais 2`() {
        val slots = listOf(1L, 1L, 1L, 1L, 2L, 2L, 1L, 1L, 1L, 1L, 2L, 2L, 1L, 1L, 1L, 1L, 2L)
        val resultado = gerarResumoPadrao(slots, mapa)
        assertEquals("4T08H · 2F · 4T08H · 2F · 4T08H · 1F", resultado)
    }

    @Test
    fun `gerar resumo para ciclo vazio`() {
        assertEquals("Ciclo vazio", gerarResumoPadrao(emptyList(), mapa))
    }
}
