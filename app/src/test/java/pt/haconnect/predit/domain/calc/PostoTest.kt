package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostoTest {

    @Test
    fun `1 - abrevia CC ES para CC`() {
        assertEquals("CC", abreviarPosto("CC ES"))
    }

    @Test
    fun `2 - abrevia CC Emb ES para CC`() {
        assertEquals("CC", abreviarPosto("CC Emb ES"))
    }

    @Test
    fun `3 - abrevia StaffCrPr ES para Staff`() {
        assertEquals("Staff", abreviarPosto("StaffCrPr ES"))
    }

    @Test
    fun `4 - abrevia Figo Maduro ES para FiGO M`() {
        assertEquals("FiGO M", abreviarPosto("Figo Maduro ES"))
    }

    @Test
    fun `5 - fallback para posto desconhecido`() {
        assertEquals("Posto", abreviarPosto("Posto Desconhecido ES"))
    }

    @Test
    fun `6 - posto nulo ou vazio retorna null`() {
        assertNull(abreviarPosto(null))
        assertNull(abreviarPosto(""))
        assertNull(abreviarPosto("   "))
    }
}
