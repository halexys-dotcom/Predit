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

    /**
     * T17 a T21: redução excecional do Despacho n.º 12523-A/2026 — novembro de 2026, Continente,
     * 50 % da retenção normal até ao limite do penúltimo escalão da tabela do contrato. Fora
     * dessas condições (dezembro, Açores/Madeira, escalões acima) a retenção fica inteira.
     */
    @Test
    fun `T17 - novembro de 2026 no Continente reduz a retencao a metade`() {
        // Normal (T1): 512 548. Metade: 256 274.
        val normal = calcularIRS(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas
        )
        val novembro = calcularIRSMensal(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 11
        )

        assertEquals(512_548L, normal)
        assertEquals(256_274L, novembro)
        assertEquals("25,63 €", novembro.milParaEuros())
    }

    @Test
    fun `T18 - novembro de 2026 acima do penultimo escalao mantem a retencao inteira`() {
        // Tabela I do Continente: o penúltimo escalão acaba em 20 221,00 € (202 210 000).
        // No limite ainda há redução: 82 230 795 → 41 115 398 (metade, ao meio para cima).
        val noLimite = calcularIRSMensal(
            202_210_000, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 11
        )
        // 25 000,00 € já cai no último escalão: retenção inteira (104 773 300 = 10 477,33 €).
        val acima = calcularIRSMensal(
            250_000_000, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 11
        )

        assertEquals(41_115_398L, noLimite)
        assertEquals(104_773_300L, acima)
        assertEquals(
            "acima do penúltimo escalão não há redução",
            calcularIRS(250_000_000, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas),
            acima
        )
        assertTrue(
            "o limite é inclusivo (<= penúltimo)",
            temReducaoExcecionalNovembro2026(
                202_210_000, 1, CATEGORIA_TRABALHO, 2026, 11, RegiaoIRS.CONTINENTE, tabelas
            )
        )
        assertTrue(
            "um cêntimo acima já fica de fora",
            !temReducaoExcecionalNovembro2026(
                202_210_001, 1, CATEGORIA_TRABALHO, 2026, 11, RegiaoIRS.CONTINENTE, tabelas
            )
        )
    }

    @Test
    fun `T19 - Acores e Madeira ficam fora da reducao de novembro`() {
        // Açores, escalão 4: 1 766 064 − 1 252 200 − 428 600 = 85 264.
        // Madeira, escalão 3: 1 432 844 − 971 700 − 428 600 = 32 544.
        val acores = calcularIRSMensal(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.ACORES, tabelas, mes = 11
        )
        val madeira = calcularIRSMensal(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.MADEIRA, tabelas, mes = 11
        )

        assertEquals(85_264L, acores)
        assertEquals(32_544L, madeira)
        assertEquals(
            calcularIRS(11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.ACORES, tabelas),
            acores
        )
        assertEquals(
            calcularIRS(11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.MADEIRA, tabelas),
            madeira
        )
    }

    @Test
    fun `T20 - fora de novembro de 2026 a retencao mantem-se inteira`() {
        // Dezembro do mesmo ano: a redução é só de novembro.
        assertEquals(
            512_548L,
            calcularIRSMensal(
                11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 12
            )
        )
        assertTrue(
            "novembro de outro ano não tem redução",
            !temReducaoExcecionalNovembro2026(
                11_900_700, 1, CATEGORIA_TRABALHO, 2027, 11, RegiaoIRS.CONTINENTE, tabelas
            )
        )
    }

    @Test
    fun `T21 - IRS autonomo do subsidio de Natal pago em novembro de 2026 e metade`() {
        // Natal de 1 137,98 €, sem dependentes, escalão 3: 1 786 629 − 947 100 = 839 529.
        // Em novembro de 2026 no Continente: 419 765 (839 529 × 50 %, ao meio para cima).
        val normal = calcularIRS(
            11_379_800, 1, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas
        )
        val novembro = calcularIRSMensal(
            11_379_800, 1, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 11
        )
        // Em dezembro o mesmo subsídio volta a pagar a retenção inteira.
        val dezembro = calcularIRSMensal(
            11_379_800, 1, CATEGORIA_TRABALHO, 0, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 12
        )

        assertEquals(839_529L, normal)
        assertEquals(419_765L, novembro)
        assertEquals(839_529L, dezembro)
        assertEquals("41,98 €", novembro.milParaEuros())
    }

    /**
     * T22 a T27 (v2.2.0): o detalhe do IRS — escalão, taxa marginal e taxa efetiva — para a
     * conferência do recibo mostrar debaixo do D02. [ResultadoIRS.numeroEscalao] usa a numeração do
     * XLSX (1-based): a linha `ordemEscalao = 0` da semente (taxa 0, até 920,00 €) é o escalão 1, e
     * a `ordemEscalao = 5` (24,1 %) é o escalão 6.
     *
     * A taxa efetiva é a que o recibo mostra: `valorMil × 10000 ÷ base`, em 0,01 ponto percentual.
     */
    @Test
    fun `T22 - detalhe do IRS traz o escalao, a taxa marginal e a taxa efetiva`() {
        // T1: base 1 190,07 € no escalão de 21,2 % (ordem 4 da semente, escalão 5 na tabela).
        val detalhe = calcularIRSComDetalhe(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas
        )

        assertEquals(512_548L, detalhe.valorMil)
        assertEquals(5, detalhe.numeroEscalao)
        assertEquals(21_200, detalhe.taxaBasisPoints)
        // 51,2548 € sobre 1 190,07 € = 4,3068 % → 431 (4,31 %), não os 21,2 % do escalão.
        assertEquals(431, detalhe.taxaEfetivaBasisPoints)
        assertTrue("21,2 % não é isenção", !detalhe.isento)
        assertTrue("calcularIRS não passa pelo Despacho", !detalhe.reducaoNovembro2026)
        assertEquals(null, detalhe.percentagemIrsJovem)
    }

    @Test
    fun `T23 - detalhe no primeiro escalao da tabela e isento`() {
        // 900,00 € cai no 1.º escalão da tabela I (taxa 0): retenção 0 por isenção.
        val detalhe = calcularIRSComDetalhe(
            9_000_000, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas
        )

        assertEquals(0L, detalhe.valorMil)
        assertEquals(1, detalhe.numeroEscalao)
        assertEquals(0, detalhe.taxaBasisPoints)
        assertEquals(0, detalhe.taxaEfetivaBasisPoints)
        assertTrue("taxa zero é o escalão de isenção", detalhe.isento)
    }

    @Test
    fun `T24 - detalhe mensal marca a reducao de novembro de 2026`() {
        // T17: a mesma base, mas em novembro a retenção desce a metade; o escalão não muda.
        val novembro = calcularIRSMensalComDetalhe(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 11
        )
        val dezembro = calcularIRSMensalComDetalhe(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 12
        )

        assertEquals(256_274L, novembro.valorMil)
        assertTrue("novembro de 2026 tem a redução de 50 %", novembro.reducaoNovembro2026)
        assertEquals(5, novembro.numeroEscalao)
        assertEquals(21_200, novembro.taxaBasisPoints)
        // A efetiva acompanha o valor: 25,6274 € sobre 1 190,07 € = 2,1534 % → 215.
        assertEquals(215, novembro.taxaEfetivaBasisPoints)

        assertEquals(512_548L, dezembro.valorMil)
        assertTrue("dezembro não tem redução", !dezembro.reducaoNovembro2026)
        assertEquals(431, dezembro.taxaEfetivaBasisPoints)
    }

    @Test
    fun `T25 - detalhe mensal guarda a percentagem do IRS Jovem`() {
        // 100 % isento no 1.º ano de rendimentos: a retenção fica a 0, mas o escalão e a taxa não
        // mudam — o regime isenta parte da retenção, não move a base de escalão.
        val detalhe = calcularIRSMensalComDetalhe(
            11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas,
            mes = 8, percentagemIrsJovem = 100
        )

        assertEquals(0L, detalhe.valorMil)
        assertEquals(100, detalhe.percentagemIrsJovem)
        assertEquals(5, detalhe.numeroEscalao)
        assertEquals(21_200, detalhe.taxaBasisPoints)
        assertEquals(0, detalhe.taxaEfetivaBasisPoints)
        assertEquals(
            "a mesma conta de calcularIRSMensal",
            calcularIRSMensal(
                11_900_700, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas,
                mes = 8, percentagemIrsJovem = 100
            ),
            detalhe.valorMil
        )
    }

    /**
     * O caso que motivou esta correção: a base cai no escalão de 24,1 % (ordem 5), mas o IRS retido
     * vale 7,23 % da base. A UI mostrava os 24,1 % do escalão e o recibo da empresa mostra a efetiva.
     */
    @Test
    fun `T26 - taxa efetiva e bem menor do que a marginal do escalao`() {
        // 1 400,00 €: acima de 1 212,00 € (escalão 5) e até 1 819,00 € (escalão 6, ordem 5).
        val detalhe = calcularIRSComDetalhe(
            14_000_000, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas
        )

        assertEquals(6, detalhe.numeroEscalao)
        assertEquals(24_100, detalhe.taxaBasisPoints)
        // 24,1 % de 1 400,00 = 337,40 − 193,33 − 2 × 21,43 = 101,21 € de retenção.
        assertEquals(1_012_100L, detalhe.valorMil)
        // 101,21 € sobre 1 400,00 € = 7,2293 % → 723 (7,23 %), e não os 24,1 % do escalão.
        assertEquals(723, detalhe.taxaEfetivaBasisPoints)
    }

    @Test
    fun `T27 - sem base tributavel a taxa efetiva e zero`() {
        val detalhe = calcularIRSMensalComDetalhe(
            0L, 1, CATEGORIA_TRABALHO, 2, 2026, RegiaoIRS.CONTINENTE, tabelas, mes = 8
        )

        assertEquals(0L, detalhe.valorMil)
        assertEquals(0, detalhe.taxaEfetivaBasisPoints)
        assertEquals(1, detalhe.numeroEscalao)
        assertTrue("sem base a retenção é 0,00 €", detalhe.isento)
    }

}
