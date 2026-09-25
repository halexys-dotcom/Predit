package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 20 — IRS Jovem. Prova duas coisas:
 *
 * 1. O enquadramento: a percentagem de isenção por ano de obtenção (100/75/50/25) e os dois
 *    motivos de exclusão — mais de 35 anos no final do ano, e fora dos 10 primeiros anos.
 * 2. A fórmula da retenção mensal: a taxa efetiva do mês aplicada só à parte não isenta, com o
 *    teto de 55 × IAS ÷ 14 e o arredondamento apenas no fim.
 *
 * O ano de referência é 2026 — IAS 537,13 € = 5 371 300 unidades de 1/10000 € —, o mesmo das
 * tabelas de retenção semeadas na app.
 */
class IrsJovemTest {

    @Test
    fun `T1 - primeiro ano vale 100 por cento`() {
        assertEquals(100, percentagemIsencaoIrsJovem(2005, 2026, 2026))
    }

    @Test
    fun `T2 - quarto ano vale 75 por cento`() {
        assertEquals(75, percentagemIsencaoIrsJovem(1998, 2023, 2026))
    }

    @Test
    fun `T3 - setimo ano vale 50 por cento`() {
        assertEquals(50, percentagemIsencaoIrsJovem(1995, 2020, 2026))
    }

    @Test
    fun `T4 - decimo ano vale 25 por cento`() {
        // 2026 − 2017 + 1 = 10, o último ano do regime; 33 anos de idade, por isso passa a idade.
        assertEquals(25, percentagemIsencaoIrsJovem(1993, 2017, 2026))
    }

    @Test
    fun `T5 - fora dos 10 anos nao se enquadra`() {
        // Mesma idade do T4 (33 anos): o que exclui aqui é o ano de obtenção (12.º).
        assertNull(percentagemIsencaoIrsJovem(1993, 2015, 2026))
    }

    @Test
    fun `T6 - idade acima de 35 nao se enquadra`() {
        // 36 anos no fim de 2026 — o ano de obtenção (7.º) ainda dava 50 %, mas a idade corta.
        assertNull(percentagemIsencaoIrsJovem(1990, 2020, 2026))
    }

    @Test
    fun `T7 - formula completa do folheto AT`() {
        // Caso do folheto da AT: base 1 800,00 € e retenção normal 262,01 € (tabela I), com 75 %
        // de isenção (4.º ano). A parte isenta — 1 350,00 € — cabe no teto mensal de 2 110,1535 €
        // (55 × 537,13 ÷ 14 = 21 101 535 unidades), logo é a percentagem que manda.
        val resultado = aplicarIrsJovem(
            baseMil = 18_000_000,           // 1 800,00 €
            retencaoNormalMil = 2_620_100,  // 262,01 €
            percentagem = 75,
            anoAtual = 2026
        )

        // Parte tributável 4 500 000 (450,00 €) à taxa efetiva real do mês (2 620 100 / 18 000 000):
        // 655 025 unidades = 65,5025 €.
        //
        // O folheto da AT dá 65,52 € para este caso, porque arredonda a taxa efetiva a 4 casas
        // (14,56 %) antes de a aplicar. A nossa fórmula segue o princípio "arredondar só no fim"
        // do projeto (Dinheiro/CalculoCCT/Jornada/EstimadorRecibo): aplica a taxa real
        // (262,01 / 1 800 = 14,5561...%) e arredonda apenas o resultado final. A diferença é de
        // 0,0175 € num caso de 65 € — não é erro, é uma convenção diferente.
        assertEquals(655_025L, resultado)
    }

    @Test
    fun `T8 - limite mensal corta a isencao`() {
        // Base 3 000,00 € e retenção normal 696,57 € com 75 %: a isenção teórica (2 250,00 €)
        // passa o teto, por isso é o teto que decide.
        val resultado = aplicarIrsJovem(
            baseMil = 30_000_000,           // 3 000,00 €
            retencaoNormalMil = 6_965_700,  // 696,57 €
            percentagem = 75,
            anoAtual = 2026
        )

        // Teto mensal: 55 × 5 371 300 ÷ 14 = 21 101 535 = 2 110,1535 € (e não os 2 250,00 € teóricos).
        // Parte tributável: 30 000 000 − 21 101 535 = 8 898 465 = 889,8465 €, à taxa efetiva do mês
        // (6 965 700 / 30 000 000 = 23,219 %) → 2 066 134,588 → 2 066 135 unidades = 206,6135 €.
        // O folheto da AT arredonda o tributável ao cêntimo antes de aplicar (889,85 × 23,219 % =
        // 206,60 €); nós arredondamos só no fim — a diferença é de 0,01 €.
        assertEquals(2_066_135L, resultado)

        // Fail-safes: sem base, sem retenção, ou num ano sem IAS tabelado, o D02 fica exatamente
        // como estaria sem IRS Jovem (é para isso que a função devolve a retenção normal).
        assertEquals(2_620_100L, aplicarIrsJovem(0L, 2_620_100L, 75, 2026))
        assertEquals(0L, aplicarIrsJovem(18_000_000L, 0L, 75, 2026))
        assertEquals(2_620_100L, aplicarIrsJovem(18_000_000L, 2_620_100L, 75, 2027))
    }
}
