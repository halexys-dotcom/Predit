package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.data.local.TABELAS_IRS_2026
import pt.haconnect.predit.data.repository.paraEscalao
import pt.haconnect.predit.domain.model.EstadoCivil
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
        val irs = calcularIRS(11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals("51,25 €", irs.milParaEuros())
        assertTrue("dentro da tolerância de 1 € face ao recibo (51,00 €)", irs in 500_000L..520_000L)
    }

    @Test
    fun `T2 - retencao de julho 2026 base 1 157,08 tabela I com 2 dependentes`() {
        // Escalão 4: 2 453 010 − 1 581 800 − 428 600 = 442 610 = 44,26 €
        val irs = calcularIRS(11_570_800, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(442_610L, irs)
        assertEquals("44,26 €", irs.milParaEuros())
    }

    @Test
    fun `T3 - base abaixo do limite de isencao devolve zero`() {
        // 900,00 € cai no escalão 0 (até 920,00 € · taxa 0)
        assertEquals(0L, calcularIRS(9_000_000, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas))
    }

    @Test
    fun `T4 - escalao superior da tabela I`() {
        // Escalão 8: 30 000 000 × 38 360 ÷ 100 000 = 11 508 000
        // 11 508 000 − 4 876 600 − 2 × 214 300 = 6 202 800 = 620,28 €
        val irs = calcularIRS(30_000_000, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(6_202_800L, irs)
        assertEquals("620,28 €", irs.milParaEuros())
    }

    @Test
    fun `T5 - sem dependentes nao abate a parcela adicional`() {
        // Escalão 4: 2 522 948 − 1 581 800 = 941 148 = 94,11 €
        val irs = calcularIRS(11_900_700, 1, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(941_148L, irs)
        assertEquals("94,11 €", irs.milParaEuros())
    }

    /**
     * T6 a T10: escolha da tabela a partir dos campos do contrato (8.4). Os números saem
     * dos subtítulos dos XLSX oficiais — I não casado sem dependentes OU casado 2 titulares;
     * II não casado com um ou mais dependentes; III casado único titular; IV a VII os mesmos
     * com pessoa com deficiência.
     *
     * A enum EstadoCivil só tem SOLTEIRO/CASADO/VIUVO/DIVORCIADO: o motor trata como "não
     * casado" tudo o que não seja CASADO, por isso os testes usam SOLTEIRO. Nos casos não
     * casados o número de titulares é indiferente (fica 1, o valor real do contrato).
     */
    @Test
    fun `T6 - casado 2 titulares com 2 dependentes fica na tabela I`() {
        val tabela = selecionarTabela(
            estadoCivil = EstadoCivil.CASADO.name,
            titulares = 2,
            numeroDependentes = 2,
            pessoaComDeficiencia = false
        )

        assertEquals(1, tabela)
    }

    @Test
    fun `T7 - casado 1 titular com 2 dependentes fica na tabela III`() {
        assertEquals(
            3,
            selecionarTabela(EstadoCivil.CASADO.name, titulares = 1, numeroDependentes = 2, pessoaComDeficiencia = false)
        )
    }

    @Test
    fun `T8 - nao casado sem dependentes fica na tabela I`() {
        assertEquals(
            1,
            selecionarTabela(EstadoCivil.SOLTEIRO.name, titulares = 1, numeroDependentes = 0, pessoaComDeficiencia = false)
        )
    }

    @Test
    fun `T9 - nao casado com 2 dependentes fica na tabela II`() {
        assertEquals(
            2,
            selecionarTabela(EstadoCivil.SOLTEIRO.name, titulares = 1, numeroDependentes = 2, pessoaComDeficiencia = false)
        )
    }

    @Test
    fun `T10 - casado 2 titulares com dependentes e deficiencia fica na tabela VI`() {
        assertEquals(
            6,
            selecionarTabela(EstadoCivil.CASADO.name, titulares = 2, numeroDependentes = 2, pessoaComDeficiencia = true)
        )
    }

    /**
     * T11 a T16: fórmula composta dos primeiros escalões de 2026 (2026-10-21).
     *
     * O XLSX oficial não traz uma parcela fixa nesses escalões — traz `taxa x k x (X - R)`:
     * k = 2,6 e X = 1 273,85 € no 1.º, k = 1,35 e X = 1 554,83 € no 2.º (tabela I do Continente).
     * O motor devolvia 0 neles, e por isso qualquer base entre 920 € e 1 108 € saía a 0,00 €.
     */
    @Test
    fun `T11 - base 1 105,35 cai no escalao composto e ja reteve`() {
        // Bruto: 11 053 500 × 15 700 ÷ 100 000 = 1 735 400 (173,54 €)
        // Parcela: 15 700 × 13 500 × (15 548 300 − 11 053 500) ÷ 10^9 = 952 673 (95,27 €)
        // 1 735 400 − 952 673 = 782 727 = 78,27 €
        val irs = calcularIRS(11_053_500, 1, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(782_727L, irs)
        assertEquals("78,27 €", irs.milParaEuros())
        assertTrue("o escalão composto deixou de devolver zero", irs > 0L)
    }

    @Test
    fun `T12 - base 1 105,35 com 2 dependentes abate a parcela adicional`() {
        // 782 727 − 2 × 214 300 = 354 127 = 35,41 €
        val irs = calcularIRS(11_053_500, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(354_127L, irs)
        assertEquals("35,41 €", irs.milParaEuros())
    }

    @Test
    fun `T13 - no limite do escalao composto a taxa efetiva e a da tabela`() {
        // Base 1 108,00 € (limite do 2.º escalão): 1 739 560 − 947 056 = 792 504 = 79,25 €
        val irs = calcularIRS(11_080_000, 1, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(792_504L, irs)
        // 79,25 ÷ 1 108 = 7,15 % — a coluna "taxa efetiva no limite" do XLSX diz 7,2 %.
        assertEquals(7.15, irs * 100.0 / 11_080_000, 0.01)
    }

    @Test
    fun `T14 - primeiro escalao composto usa o k e o X do primeiro escalao`() {
        // Base 1 042,00 € (limite do 1.º escalão):
        // Parcela: 12 500 × 26 000 × (12 738 500 − 10 420 000) ÷ 10^9 = 753 513 (75,35 €)
        // 1 302 500 − 753 513 = 548 987 = 54,90 € (5,27 %; o XLSX diz 5,3 % no limite)
        val irs = calcularIRS(10_420_000, 1, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(548_987L, irs)
        assertEquals("54,90 €", irs.milParaEuros())
    }

    @Test
    fun `T15 - cada tabela traz o seu X`() {
        // Tabela III, base 1 100,00 €: taxa 12,5 % (a mesma do 1.º escalão da tabela I), mas
        // X = 1 677,85 €. Parcela: 12 500 × 13 500 × (16 778 500 − 11 000 000) ÷ 10^9 = 975 122.
        // 1 375 000 − 975 122 = 399 878 = 39,99 €
        val irs = calcularIRS(11_000_000, 3, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas)

        assertEquals(399_878L, irs)
        assertEquals("39,99 €", irs.milParaEuros())
    }

    @Test
    fun `T16 - a semente traz k e X em todos os escaloes de formula composta`() {
        val compostos = tabelas.filter { it.formulaComposta }

        assertTrue("há escalões de fórmula composta nas tabelas de 2026", compostos.isNotEmpty())
        assertTrue(
            "todos trazem k e X (senão voltam a reter 0,00 €)",
            compostos.all { it.coeficienteK > 0L && it.limiteX > 0L }
        )
        assertTrue(
            "X tem de ficar acima do limite do escalão, senão a parcela saía negativa",
            compostos.all { it.limiteX >= it.limiteAte }
        )
    }
}
