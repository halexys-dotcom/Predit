package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ProjecaoTest {

    // 4 trabalho + 2 folga, 4 trabalho + 2 folga, 4 trabalho + 1 folga
    private val ciclo = listOf(
        true, true, true, true, false, false,
        true, true, true, true, false, false,
        true, true, true, true, false
    )

    private val ancora = LocalDate.of(2026, 7, 1).toEpochDay()

    private fun eTrabalho(data: LocalDate): Boolean =
        ciclo[posicaoNoCiclo(data.toEpochDay(), ancora, 17)]

    private fun folgasDoMes(ano: Int, mes: Int): List<Int> =
        (1..YearMonth.of(ano, mes).lengthOfMonth())
            .filterNot { eTrabalho(LocalDate.of(ano, mes, it)) }

    @Test fun `ciclo tem 17 dias`() {
        assertEquals(17, ciclo.size)
    }

    @Test fun `julho 2026 corresponde ao PDF da empresa`() {
        assertEquals(listOf(5, 6, 11, 12, 17, 22, 23, 28, 29), folgasDoMes(2026, 7))
    }

    @Test fun `setembro 2026 corresponde ao calendario real`() {
        assertEquals(listOf(1, 6, 11, 12, 17, 18, 23, 28, 29), folgasDoMes(2026, 9))
    }

    @Test fun `projecao funciona antes da ancora`() {
        // 30 de junho de 2026 é a posição 16 — última folga do ciclo anterior
        assertEquals(16, posicaoNoCiclo(LocalDate.of(2026, 6, 30).toEpochDay(), ancora, 17))
    }

    @Test fun `a ancora e a posicao zero`() {
        assertEquals(0, posicaoNoCiclo(ancora, ancora, 17))
    }
}
