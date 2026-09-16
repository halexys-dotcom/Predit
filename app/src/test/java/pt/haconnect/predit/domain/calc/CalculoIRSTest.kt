package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.data.local.TABELAS_IRS_2026
import pt.haconnect.predit.data.repository.paraEscalao
import pt.haconnect.predit.domain.model.milParaEuros

/**
 * Motor do IRS contra as tabelas REAIS de 2026 (TABELAS_IRS_2026, geradas dos XLSX
 * oficiais do Continente). Escalões usados:
 *   tabela I, escalão 0: até 920,00 € · taxa 0
 *   tabela I, escalão 4: até 1 212,00 € · 21,2 % · parcela 158,18 € · 21,43 €/dependente
 *   tabela I, escalão 8: até 3 305,00 € · 38,36 % · parcela 487,66 €
 */
class CalculoIRSTest {

    /**
     * Escalões reais de 2026 (Continente) mapeados para o domínio — o motor já não
     * conhece TabelaIRSEntity; a ponte é o repositório.
     */
    private val tabelas: List<EscalaoIRS> = TABELAS_IRS_2026.map { it.paraEscalao() }

    @Test
    fun `T1 - retencao de agosto 2026 base 1 190,07 tabela I com 2 dependentes`() {
        // Escalão 4: 2 522 948 − 1 581 800 − 2 × 214 300 = 512 548 = 51,25 €
        val irs = calcularIRS(11_900_700, 1, 2, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals("51,25 €", irs.milParaEuros())
        assertTrue("dentro da tolerância de 1 € face ao recibo (51,00 €)", irs in 500_000L..520_000L)
    }

    @Test
    fun `T2 - retencao de julho 2026 base 1 157,08 tabela I com 2 dependentes`() {
        // Escalão 4: 2 453 010 − 1 581 800 − 428 600 = 442 610 = 44,26 €
        val irs = calcularIRS(11_570_800, 1, 2, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(442_610L, irs)
        assertEquals("44,26 €", irs.milParaEuros())
    }

    @Test
    fun `T3 - base abaixo do limite de isencao devolve zero`() {
        // 900,00 € cai no escalão 0 (até 920,00 € · taxa 0)
        assertEquals(0L, calcularIRS(9_000_000, 1, 2, 2026, RegiaoIRS.CONTINENTE, tabelas))
    }

    @Test
    fun `T4 - escalao superior da tabela I`() {
        // Escalão 8: 30 000 000 × 38 360 ÷ 100 000 = 11 508 000
        // 11 508 000 − 4 876 600 − 2 × 214 300 = 6 202 800 = 620,28 €
        val irs = calcularIRS(30_000_000, 1, 2, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(6_202_800L, irs)
        assertEquals("620,28 €", irs.milParaEuros())
    }

    @Test
    fun `T5 - sem dependentes nao abate a parcela adicional`() {
        // Escalão 4: 2 522 948 − 1 581 800 = 941 148 = 94,11 €
        val irs = calcularIRS(11_900_700, 1, 0, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(941_148L, irs)
        assertEquals("94,11 €", irs.milParaEuros())
    }
}
