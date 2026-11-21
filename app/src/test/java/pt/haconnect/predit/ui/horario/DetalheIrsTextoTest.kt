package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.haconnect.predit.domain.calc.ResultadoIRS

/**
 * O título e as notas do IRS na conferência do recibo (v2.2.1).
 *
 * O utilizador vê a taxa efetiva no próprio nome da rubrica — "IRS (4,31%)", como a "Segurança
 * Social (11%)" do catálogo — e só uma nota curta quando há algo a explicar. O escalão deixou de
 * aparecer: "Escalão 6 · Taxa Efetiva: 4,14%" não ajudava a conferir nada.
 */
class DetalheIrsTextoTest {

    private fun detalhe(
        valorMil: Long = 512_548L,
        numeroEscalao: Int = 5,
        taxaBasisPoints: Int = 21_200,
        taxaEfetivaBasisPoints: Int = 431,
        reducaoNovembro2026: Boolean = false,
        percentagemIrsJovem: Int? = null,
        irsAutonomoSubsidiosMil: Long = 0L
    ) = ResultadoIRS(
        valorMil = valorMil,
        numeroEscalao = numeroEscalao,
        taxaBasisPoints = taxaBasisPoints,
        taxaEfetivaBasisPoints = taxaEfetivaBasisPoints,
        reducaoNovembro2026 = reducaoNovembro2026,
        percentagemIrsJovem = percentagemIrsJovem,
        irsAutonomoSubsidiosMil = irsAutonomoSubsidiosMil
    )

    @Test
    fun `a taxa efetiva entra no titulo com virgula decimal`() {
        assertEquals("IRS (4,31%)", tituloRubricaIrs("IRS", detalhe()))
    }

    /** Duas casas fixas: 4,00 % e não "4 %" — o parêntese fica com a mesma largura em todos os meses. */
    @Test
    fun `a taxa leva sempre duas casas`() {
        assertEquals(
            "IRS (4,00%)",
            tituloRubricaIrs("IRS", detalhe(taxaEfetivaBasisPoints = 400))
        )
        assertEquals(
            "IRS (4,10%)",
            tituloRubricaIrs("IRS", detalhe(taxaEfetivaBasisPoints = 410))
        )
        assertEquals(
            "IRS (7,23%)",
            tituloRubricaIrs("IRS", detalhe(taxaEfetivaBasisPoints = 723))
        )
    }

    /** Escalão de taxa zero é isenção: "(Isento)" e não "(0,00%)", que parece uma retenção falhada. */
    @Test
    fun `escalao de taxa zero diz isento`() {
        assertEquals(
            "IRS (Isento)",
            tituloRubricaIrs(
                "IRS",
                detalhe(
                    valorMil = 0L,
                    numeroEscalao = 1,
                    taxaBasisPoints = 0,
                    taxaEfetivaBasisPoints = 0
                )
            )
        )
    }

    /** Sem detalhe (mês sem estimativa, ou rubrica que não é o IRS) o título é o nome do catálogo. */
    @Test
    fun `sem detalhe fica o nome do catalogo`() {
        assertEquals("IRS", tituloRubricaIrs("IRS", null))
        assertNull(notasIrs(null))
    }

    /** Sem escalão a base passou o último limite da tabela: não há taxa a anunciar, só a nota. */
    @Test
    fun `fora da tabela nao anuncia taxa`() {
        val fora =
            detalhe(valorMil = 0L, numeroEscalao = 0, taxaBasisPoints = 0, taxaEfetivaBasisPoints = 0)

        assertEquals("IRS", tituloRubricaIrs("IRS", fora))
        assertEquals("(Fora da tabela)", notasIrs(fora))
    }

    /** Num mês normal não há nada a explicar: a linha do IRS pesa o mesmo que as outras rubricas. */
    @Test
    fun `mes normal nao tem notas`() {
        assertNull(notasIrs(detalhe()))
    }

    /**
     * O IRS Jovem isenta parte da retenção e a taxa efetiva já o reflecte; sem a nota, um valor baixo
     * parecia um erro de conta.
     */
    @Test
    fun `IRS Jovem acrescenta a percentagem isenta`() {
        val jovem = detalhe(
            valorMil = 128_137L,
            taxaEfetivaBasisPoints = 108,
            percentagemIrsJovem = 75
        )

        assertEquals("IRS (1,08%)", tituloRubricaIrs("IRS", jovem))
        assertEquals("(IRS Jovem 75%)", notasIrs(jovem))
    }

    /** Novembro de 2026: a retenção foi a metade — a nota evita que se pense que a tabela mudou. */
    @Test
    fun `novembro de 2026 acrescenta a reducao`() {
        val novembro = detalhe(
            valorMil = 256_274L,
            taxaEfetivaBasisPoints = 215,
            reducaoNovembro2026 = true
        )

        assertEquals("IRS (2,15%)", tituloRubricaIrs("IRS", novembro))
        assertEquals("(Redução 50% Nov/2026)", notasIrs(novembro))
    }

    @Test
    fun `IRS Jovem e reducao de novembro aparecem os dois`() {
        val ambos = detalhe(
            valorMil = 50_605L,
            numeroEscalao = 6,
            taxaBasisPoints = 24_100,
            taxaEfetivaBasisPoints = 360,
            reducaoNovembro2026 = true,
            percentagemIrsJovem = 50
        )

        assertEquals("IRS (3,60%)", tituloRubricaIrs("IRS", ambos))
        assertEquals("(IRS Jovem 50%) (Redução 50% Nov/2026)", notasIrs(ambos))
    }

    /**
     * v2.2.0: em dezembro o D02 inclui o IRS autónomo do Natal (92,35 € contra os 51,25 € da base
     * mensal). O título continua com a taxa do mês — dividir 92,35 € por 1 190,07 € dava 7,8 %, que
     * não é a taxa anunciada — e a nota diz o que o valor leva a mais.
     */
    @Test
    fun `mes com subsidios nota o IRS autonomo`() {
        val comSubsidios = detalhe(irsAutonomoSubsidiosMil = 410_929L)

        assertEquals("IRS (4,31%)", tituloRubricaIrs("IRS", comSubsidios))
        assertEquals("(inclui IRS autónomo de subsídios)", notasIrs(comSubsidios))
    }

    /** A base mensal pode cair na isenção e o subsídio pagar IRS: o "(Isento)" é do mês. */
    @Test
    fun `isento com IRS do subsidio leva a nota`() {
        val isentoComSubsidio = detalhe(
            valorMil = 0L,
            numeroEscalao = 1,
            taxaBasisPoints = 0,
            taxaEfetivaBasisPoints = 0,
            irsAutonomoSubsidiosMil = 12_500L
        )

        assertEquals("IRS (Isento)", tituloRubricaIrs("IRS", isentoComSubsidio))
        assertEquals("(inclui IRS autónomo de subsídios)", notasIrs(isentoComSubsidio))
    }

    /** A taxa efetiva conta em 0,01 ponto percentual: 509 → 5,09 % · 400 → 4,00 % · 0 → 0,00 %. */
    @Test
    fun `taxa efetiva com duas casas fixas`() {
        assertEquals("5,09", textoDaTaxaEfetivaDuasCasas(509))
        assertEquals("4,31", textoDaTaxaEfetivaDuasCasas(431))
        assertEquals("4,00", textoDaTaxaEfetivaDuasCasas(400))
        assertEquals("4,10", textoDaTaxaEfetivaDuasCasas(410))
        assertEquals("7,23", textoDaTaxaEfetivaDuasCasas(723))
        assertEquals("1,08", textoDaTaxaEfetivaDuasCasas(108))
        assertEquals("0,00", textoDaTaxaEfetivaDuasCasas(0))
    }
}
