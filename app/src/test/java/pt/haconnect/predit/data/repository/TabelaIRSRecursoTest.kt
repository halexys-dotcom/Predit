package pt.haconnect.predit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.haconnect.predit.data.local.TABELAS_IRS_2026
import pt.haconnect.predit.domain.calc.CATEGORIA_TRABALHO
import pt.haconnect.predit.domain.calc.RegiaoIRS
import pt.haconnect.predit.domain.calc.calcularIRS

/**
 * A1: sem tabelas de retenção para o ano do mês (janeiro de 2027 antes de a app as trazer, ou um
 * mês de 2025), o recibo usa as do ano carregado mais próximo em vez de ficar preso em
 * "a carregar". Lógica pura: [anoDeRecurso] é função livre para correr sem DAO.
 */
class TabelaIRSRecursoTest {

    @Test
    fun `o proprio ano quando existe`() {
        assertEquals(2026, anoDeRecurso(2026, listOf(2025, 2026)))
    }

    @Test
    fun `2027 sem tabelas cai em 2026`() {
        assertEquals(2026, anoDeRecurso(2027, listOf(2026)))
    }

    @Test
    fun `prefere o ultimo ano anterior ao seguinte`() {
        assertEquals(2026, anoDeRecurso(2027, listOf(2025, 2026, 2028)))
    }

    @Test
    fun `2025 sem anterior cai no primeiro seguinte`() {
        assertEquals(2026, anoDeRecurso(2025, listOf(2026)))
    }

    @Test
    fun `sem tabelas nenhumas nao ha recurso`() {
        assertNull(anoDeRecurso(2027, emptyList()))
    }

    /**
     * Os escalões de recurso saem com o ano trocado: o calcularIRS filtra pelo ano do mês, e sem a
     * troca 2027 continuava sem escalões. 1 190,07 € na tabela I com 2 dependentes = 51,25 €, como
     * em 2026 (ver EstimadorReciboTest T1).
     */
    @Test
    fun `escaloes de 2026 reetiquetados servem o IRS de 2027`() {
        val de2027 = TABELAS_IRS_2026
            .filter { it.regiao == "CONTINENTE" && it.categoria == "TRABALHO" && it.tabelaNumero == 1 }
            .map { it.paraEscalao().copy(ano = 2027) }

        val irs = calcularIRS(
            baseTributavel = 11_900_700L,
            tabelaNumero = 1,
            categoria = CATEGORIA_TRABALHO,
            numDependentes = 2,
            ano = 2027,
            regiao = RegiaoIRS.CONTINENTE,
            tabelas = de2027
        )

        assertEquals(512_548L, irs)
    }
}
