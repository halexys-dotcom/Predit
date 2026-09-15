package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JornadaTest {

    @Test
    fun `T1 - Mes isolado exatamente em 173h19m`() {
        val meses = listOf("2026-07")
        val reais = mapOf("2026-07" to 10_399)

        val res = calcularCiclo(reais, meses)

        val m1 = res.meses.first()
        assertEquals(0, m1.extraPagoMinutos)
        assertEquals(0, m1.saldoAcumuladoMinutos)
        assertEquals(0, res.saldoFinalMinutos)
    }

    @Test
    fun `T2 - Mes isolado em 180h`() {
        val meses = listOf("2026-07")
        val reais = mapOf("2026-07" to 10_800) // 180:00 → +401 min (06:41)

        val res = calcularCiclo(reais, meses)

        val m1 = res.meses.first()
        assertEquals(401, m1.extraPagoMinutos)
        assertEquals(0, m1.saldoAcumuladoMinutos)
        assertEquals(401, res.extrasPagosTotalMinutos)
        assertEquals(0, res.saldoFinalMinutos)
    }

    @Test
    fun `T3 - Defice 10h mais mes de mais 16h`() {
        val meses = listOf("2026-07", "2026-08")
        val reais = mapOf(
            "2026-07" to (JORNADA_MENSAL_MINUTOS - 600), // -10h
            "2026-08" to (JORNADA_MENSAL_MINUTOS + 960)  // +16h
        )

        val res = calcularCiclo(reais, meses)

        val m1 = res.meses[0]
        assertEquals(-600, m1.saldoAcumuladoMinutos)
        assertEquals(0, m1.extraPagoMinutos)

        val m2 = res.meses[1]
        assertEquals(600, m2.abateAoDeficeMinutos) // abatiu as 10h
        assertEquals(360, m2.extraPagoMinutos)    // sobra 6h pagas
        assertEquals(0, m2.saldoAcumuladoMinutos)

        assertEquals(360, res.extrasPagosTotalMinutos)
        assertEquals(0, res.saldoFinalMinutos)
    }

    @Test
    fun `T4 - Defice 10h mais mes de mais 8h`() {
        val meses = listOf("2026-07", "2026-08")
        val reais = mapOf(
            "2026-07" to (JORNADA_MENSAL_MINUTOS - 600), // -10h
            "2026-08" to (JORNADA_MENSAL_MINUTOS + 480)  // +8h
        )

        val res = calcularCiclo(reais, meses)

        val m2 = res.meses[1]
        assertEquals(480, m2.abateAoDeficeMinutos)
        assertEquals(0, m2.extraPagoMinutos)
        assertEquals(-120, m2.saldoAcumuladoMinutos) // -2h resta
        assertEquals(-120, res.saldoFinalMinutos)
    }

    @Test
    fun `T5 - Semestre com 5 meses em defice (-4h) e 1 mes a +10h`() {
        val meses = (7..12).map { "2026-%02d".format(it) }
        val reais = mutableMapOf<String, Int>()
        for (i in 7..11) {
            reais["2026-%02d".format(i)] = JORNADA_MENSAL_MINUTOS - 240 // -4h cada
        }
        reais["2026-12"] = JORNADA_MENSAL_MINUTOS + 600 // +10h em dezembro

        val res = calcularCiclo(reais, meses)

        assertEquals(0, res.extrasPagosTotalMinutos)
        assertEquals(-600, res.saldoFinalMinutos) // -10h resta limpo no fecho
    }

    @Test
    fun `T6 - Semestre com todos os meses em defice`() {
        val meses = (1..6).map { "2026-%02d".format(it) }
        val reais = meses.associateWith { JORNADA_MENSAL_MINUTOS - 120 } // -2h cada

        val res = calcularCiclo(reais, meses)

        assertEquals(0, res.extrasPagosTotalMinutos)
        assertTrue(res.saldoFinalMinutos < 0)
        assertEquals(-720, res.saldoFinalMinutos)
    }

    @Test
    fun `T7 - Semestre com todos os meses acima (+5h)`() {
        val meses = (1..6).map { "2026-%02d".format(it) }
        val reais = meses.associateWith { JORNADA_MENSAL_MINUTOS + 300 } // +5h (300 min) cada

        val res = calcularCiclo(reais, meses)

        assertEquals(1800, res.extrasPagosTotalMinutos) // 30:00 total pago
        assertEquals(0, res.saldoFinalMinutos)
    }

    @Test
    fun `T8 - cicloDoAno devolve datas corretas`() {
        val s1 = cicloDoAno(2026, 1)
        assertEquals(LocalDate.of(2026, 1, 1), s1.first)
        assertEquals(LocalDate.of(2026, 6, 30), s1.second)

        val s2 = cicloDoAno(2026, 2)
        assertEquals(LocalDate.of(2026, 7, 1), s2.first)
        assertEquals(LocalDate.of(2026, 12, 31), s2.second)
    }
}
