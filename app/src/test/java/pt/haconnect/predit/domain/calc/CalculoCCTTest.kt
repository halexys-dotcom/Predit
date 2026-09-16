package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.model.formatarMoeda
import java.time.LocalDate

/**
 * Motor do CCT. Todos os números esperados vêm do comando da Fase 8.1 e do
 * handover do projeto (valor/hora 2026 = 6,5653 €, sup. noturno = 14,36 €).
 */
class CalculoCCTTest {

    /** 2026: vencimento base 1 137,98 €, 40 h/semana, admissão recente (21h–06h). */
    private fun contexto2026(): ContextoCalculo {
        val janela = janelaNoturna(LocalDate.of(2020, 1, 1).toEpochDay())
        return ContextoCalculo(
            vencimentoBaseMil = 11_379_800,
            horarioSemanalH = 40,
            janelaNoturna = janela,
            diasUteisMes = 22,
            numDependentes = 2
        )
    }

    @Test
    fun `T1 - valor por hora 2026 a 40h semanais`() {
        // (11 379 800 × 12) ÷ (52 × 40) = 136 557 600 ÷ 2080 = 65 652,6923… → 65 653
        assertEquals(65_653, valorHoraMil(contexto2026()))
    }

    @Test
    fun `T2 - valor por hora 2025 a 40h semanais`() {
        val ctx = contexto2026().copy(vencimentoBaseMil = 10_760_000)

        // (10 760 000 × 12) ÷ 2080 = 129 120 000 ÷ 2080 = 62 076,9230… → 62 077
        assertEquals(62_077, valorHoraMil(ctx))
    }

    @Test
    fun `T3 - hora noturna 2026`() {
        // 65 653 × 5 ÷ 4 = 328 265 ÷ 4 = 82 066,25 → 82 066
        assertEquals(82_066, valorHoraTipo(contexto2026(), TipoHora.NOTURNA))
    }

    @Test
    fun `T4 - suplemento noturno em dia normal 2026 da 14,36 EUR e nunca 14,37`() {
        val valor = valorHoraTipo(contexto2026(), TipoHora.SUP_NOTURNO_NORMAL)

        // 65 653 × 35 ÷ 16 = 2 297 855 ÷ 16 = 143 615,9375 → 143 616 (multiplicar antes de dividir)
        assertEquals(143_616, valor)
        assertEquals("14,36 €", valor.formatarMoeda())
    }

    @Test
    fun `T5 - valor do dia 2026`() {
        // 65 653 × 8 = 525 224 (52,52 €)
        assertEquals(525_224, valorDiaMil(contexto2026()))
        assertEquals("52,52 €", valorDiaMil(contexto2026()).formatarMoeda())
    }
}
