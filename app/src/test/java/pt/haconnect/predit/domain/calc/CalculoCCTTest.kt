package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.model.formatarMoeda
import java.time.LocalDate

/**
 * Motor do CCT. Todos os numeros esperados vem do comando da Fase 8.1 e do
 * handover do projeto (valor/hora 2026 = 6,57 EUR, sup. noturno = 14,37 EUR).
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
        // (11 379 800 x 12) / (52 x 40) = 65 652,69 arredonda ao centimo: 65 700
        assertEquals(65_700, valorHoraMil(contexto2026()))
    }

    @Test
    fun `T2 - valor por hora 2025 a 40h semanais`() {
        val ctx = contexto2026().copy(vencimentoBaseMil = 10_760_000)

        // (10 760 000 x 12) / 2080 = 62 076,92 arredonda ao centimo: 62 100
        assertEquals(62_100, valorHoraMil(ctx))
    }

    @Test
    fun `T3 - acrescimo noturno 2026`() {
        // 65 700 x 1 / 4 = 16 425 (1,6425 EUR/h): acrescimo de 25% sobre a hora normal,
        // nao o valor total da hora (a hora em si ja vem no VENC).
        assertEquals(16_425, valorHoraTipo(contexto2026(), TipoHora.NOTURNA_ACRESCIMO))
    }

    @Test
    fun `T4 - suplemento noturno em dia normal 2026 da 14,37 EUR`() {
        val valor = valorHoraTipo(contexto2026(), TipoHora.SUP_NOTURNO_NORMAL)

        // 65 700 x 35 / 16 = 143 718,75 arredonda 143 719 (multiplicar antes de dividir)
        assertEquals(143_719, valor)
        assertEquals("14,37 €", valor.formatarMoeda())
    }

    @Test
    fun `T5 - valor do dia 2026`() {
        // 65 700 x 8 = 525 600 (52,56 EUR)
        assertEquals(525_600, valorDiaMil(contexto2026()))
        assertEquals("52,56 €", valorDiaMil(contexto2026()).formatarMoeda())
    }

    @Test
    fun `T6 - valor do minuto 2026 e o que desconta o atraso`() {
        // 65 700 / 60 = 1 095 unidades (0,1095 EUR/min): e a base do desconto de atraso.
        assertEquals(1_095, valorMinutoMil(contexto2026()))
        // 2025: 62 100 / 60 = 1 035 (0,1035 EUR/min).
        val ctx2025 = contexto2026().copy(vencimentoBaseMil = 10_760_000)
        assertEquals(1_035, valorMinutoMil(ctx2025))
    }
}
