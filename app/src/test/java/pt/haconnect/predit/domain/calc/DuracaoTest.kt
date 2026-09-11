package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class DuracaoTest {

    @Test fun `turno de tarde 13h-21h`() {
        assertEquals(480, duracaoMinutos(13 * 60, 21 * 60, 0))
    }

    @Test fun `turno noturno 21h-06h atravessa a meia-noite`() {
        assertEquals(540, duracaoMinutos(21 * 60, 6 * 60, 0))
    }

    @Test fun `turno noturno 22h-06h com 30 min de pausa`() {
        assertEquals(450, duracaoMinutos(22 * 60, 6 * 60, 30))
    }

    @Test fun `turno de manha 06h-14h`() {
        assertEquals(480, duracaoMinutos(6 * 60, 14 * 60, 0))
    }

    @Test fun `folga tem duracao zero`() {
        assertEquals(0, duracaoMinutos(0, 0, 0))
    }
}
