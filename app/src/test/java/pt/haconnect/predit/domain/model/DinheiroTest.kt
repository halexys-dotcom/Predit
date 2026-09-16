package pt.haconnect.predit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dinheiro: unidades de 1/10000 €, cálculo exato por frações e arredondamento
 * apenas no fim. Os casos T7, T8 e T9 usam os números reais dos recibos do
 * utilizador (julho e agosto de 2026).
 */
class DinheiroTest {

    @Test
    fun `T1 - 6,5653 EUR sao 65653 unidades e o 4o decimal preserva-se`() {
        val valor = deTexto("6,5653")

        assertEquals(65_653L, valor.numerador)
        assertEquals(1L, valor.denominador)
        assertEquals("6,5653", formatarEurosExato(valor))
        assertEquals("6,57 €", valor.arredondarParaCentimo().formatarMoeda())
    }

    @Test
    fun `T2 - 6,5653 x 1,25 x 1,75 = 14,36 EUR arredondando so no fim`() {
        val hora = deTexto("6,5653")
        val bruto = hora * Fracao(5, 4) * Fracao(7, 4) // 1,25 e 1,75 exatos

        assertEquals("14,3616", formatarEurosExato(bruto))
        assertEquals(143_600, bruto.arredondarParaCentimo())
        assertEquals("14,36 €", bruto.arredondarParaCentimo().formatarMoeda())
    }

    @Test
    fun `T3 - com 6,57 arredondado a meio daria 14,37 EUR (o defeito evitado)`() {
        val certo = deTexto("6,5653") * Fracao(5, 4) * Fracao(7, 4)
        val cedo = deTexto("6,57") * Fracao(5, 4) * Fracao(7, 4)

        assertEquals("14,36 €", certo.arredondarParaCentimo().formatarMoeda())
        assertEquals("14,37 €", cedo.arredondarParaCentimo().formatarMoeda())
        assertTrue(cedo.arredondarParaCentimo() != certo.arredondarParaCentimo())
    }

    @Test
    fun `T4 - 1 centimo nao e divergencia, 2 centimos e`() {
        val estimado = deTexto("14,36")

        val umCentimo = compararComRecibo(estimado, deTexto("14,37").arredondarParaCentimo())
        assertEquals(-100, umCentimo.diferencaUnidades)
        assertTrue(umCentimo.dentroDoLimiar)

        val doisCentimos = compararComRecibo(estimado, deTexto("14,34").arredondarParaCentimo())
        assertEquals(200, doisCentimos.diferencaUnidades)
        assertFalse(doisCentimos.dentroDoLimiar)
    }

    @Test
    fun `T5 - apresentacao em euros, cêntimos e sinal`() {
        assertEquals("14,36 €", 143_600.formatarMoeda())
        assertEquals("6,57 €", 65_653.formatarMoeda())
        assertEquals("0,00 €", 0.formatarMoeda())
        assertEquals("-14,36 €", (-143_600).formatarMoeda())
        assertEquals("-14,3600", formatarEurosExato(deTexto("-14,36")))

        // API da 8.1: valores da tabela CCT e arredondamento para cêntimos
        assertEquals("1 137,98 €", 11_379_800L.milParaEuros())  // vencimento base 2026
        assertEquals("52,52 €", 525_224.formatarMoeda())        // valor/dia 2026
        assertEquals(1436L, 143_616L.milParaCentimos())         // 14,3616 € -> 1436 cêntimos
    }

    @Test
    fun `T6 - soma exata de rubricas sem arredondar cada parcela`() {
        val somaExata = deTexto("6,5653") + deTexto("6,5653")
        val somaCedo = deTexto("6,57").arredondarParaCentimo() + deTexto("6,57").arredondarParaCentimo()

        assertEquals("13,13 €", somaExata.arredondarParaCentimo().formatarMoeda())
        assertEquals("13,14 €", somaCedo.formatarMoeda())
    }

    @Test
    fun `T7 - recibo de julho 2026 a base do IRS era 1 118,88 EUR`() {
        val retencao = deTexto("48,00")
        val taxa = percentagem(429) // 4,29%

        val base = retencao / taxa

        assertEquals("1 118,88 €", base.arredondarParaCentimo().formatarMoeda())
    }

    @Test
    fun `T8 - recibo de agosto 2026 7,63 por cento da 104,00 na base errada e 90,80 na certa`() {
        val taxa = percentagem(763) // 7,63%
        val baseErrada = deTexto("1 363,04") // inclui o subsidio de alimentacao em cartao
        val baseCerta = deTexto("1 190,07")

        assertEquals("104,00 €", (baseErrada * taxa).arredondarParaCentimo().formatarMoeda())
        assertEquals("90,80 €", (baseCerta * taxa).arredondarParaCentimo().formatarMoeda())
        assertEquals("13,20 €", (deTexto("104,00") - deTexto("90,80")).arredondarParaCentimo().formatarMoeda())

        // A base do recibo e exatamente a base certa mais o subsidio de alimentacao
        assertEquals(baseErrada, baseCerta + deTexto("172,97"))
    }

    @Test
    fun `T9 - valores acima do Int no meio do calculo (Long)`() {
        // 5 000,00 EUR x 7,63%: 50 000 000 x 763 = 38 150 000 000, muito acima do Int
        val bruto = deTexto("5000,00") * percentagem(763)

        assertEquals("381,50 €", bruto.arredondarParaCentimo().formatarMoeda())
    }

    @Test
    fun `T10 - deTexto aceita o que vem escrito no recibo`() {
        assertEquals(deTexto("1190,07"), deTexto("1 190,07"))
        assertEquals(deTexto("14,36"), deTexto("14.36"))
        assertEquals(deTexto("6,57"), deTexto("6,5700"))
        assertEquals(ValorExato(60_000L), deTexto("6"))
        assertEquals(ValorExato(656_530L), deTexto("65,653"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `T11 - texto invalido falha em voz alta`() {
        deTexto("abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `T12 - mais de 4 casas decimais falha`() {
        deTexto("6,56531")
    }
}
