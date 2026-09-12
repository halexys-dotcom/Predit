package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ContratoTest {

    @Test
    fun `admissao anterior a 15 de julho de 2004 tem janela noturna das 20h as 07h`() {
        val data = LocalDate.of(2004, 7, 14).toEpochDay()
        val resultado = janelaNoturna(data)
        assertEquals(1200 to 420, resultado) // 20:00 (1200 min) to 07:00 (420 min)
    }

    @Test
    fun `admissao no proprio dia 15 de julho de 2004 tem janela noturna nova das 21h as 06h`() {
        val data = LocalDate.of(2004, 7, 15).toEpochDay()
        val resultado = janelaNoturna(data)
        assertEquals(1260 to 360, resultado) // 21:00 (1260 min) to 06:00 (360 min)
    }

    @Test
    fun `admissao recente em 2020 tem janela noturna nova das 21h as 06h`() {
        val data = LocalDate.of(2020, 1, 1).toEpochDay()
        val resultado = janelaNoturna(data)
        assertEquals(1260 to 360, resultado) // 21:00 (1260 min) to 06:00 (360 min)
    }
}
