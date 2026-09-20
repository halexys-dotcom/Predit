package pt.haconnect.predit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.haconnect.predit.domain.model.ParametrosCCT
import java.time.LocalDate

/**
 * 13a: escolha da tabela salarial por (categoria, dia).
 *
 * É lógica pura — corre sem Room e sem DAO, e é por isso que [parametrosDaCategoria] é uma
 * função livre e não um método do repositório.
 */
class ParametrosCCTLookupTest {

    private fun linha(codigo: String, ano: Int, vencimento: Int) = ParametrosCCT(
        validoDe = LocalDate.of(ano, 1, 1).toEpochDay(),
        vencimentoBaseMil = vencimento,
        subAlimentacaoDiaMil = 74_200,
        subTransporteMesMil = 492_500,
        horarioSemanalReferencia = 40,
        codigoCategoria = codigo
    )

    /** Duas categorias × duas vigências, como a BD fica depois da 13a. */
    private val catalogo = listOf(
        linha("APAA", 2025, 10_760_000),
        linha("APAA", 2026, 11_379_800),
        linha("TEAM_LEADER", 2025, 10_760_000),
        linha("TEAM_LEADER", 2026, 11_379_800)
    )

    private fun dia(ano: Int, mes: Int, diaDoMes: Int): Long =
        LocalDate.of(ano, mes, diaDoMes).toEpochDay()

    @Test
    fun `L1 - devolve a vigencia da categoria do contrato`() {
        val escolhida = parametrosDaCategoria(catalogo, "TEAM_LEADER", dia(2026, 9, 1))
        assertEquals("TEAM_LEADER", escolhida?.codigoCategoria)
        assertEquals(11_379_800, escolhida?.vencimentoBaseMil)
    }

    @Test
    fun `L2 - categoria sem tabela nenhuma cai na APAA`() {
        // Caso real: contrato com uma categoria que não tem nenhuma linha em parametros_cct.
        val soApaa = listOf(linha("APAA", 2025, 10_760_000), linha("APAA", 2026, 11_379_800))
        val escolhida = parametrosDaCategoria(soApaa, "TEAM_LEADER", dia(2026, 9, 1))

        assertEquals("APAA", escolhida?.codigoCategoria)
        assertEquals(11_379_800, escolhida?.vencimentoBaseMil)
    }

    @Test
    fun `L3 - sem vigencia aplicavel devolve null`() {
        // Só há tabela de 2026 e o mês é de 2025: não há nada para aplicar (a UI mostra a carregar).
        assertNull(parametrosDaCategoria(listOf(linha("APAA", 2026, 11_379_800)), "APAA", dia(2025, 6, 1)))
        // Nem a categoria pedida nem a APAA existem no catálogo.
        assertNull(parametrosDaCategoria(listOf(linha("TEAM_LEADER", 2026, 11_379_800)), "APAA", dia(2026, 9, 1)))
    }
}
