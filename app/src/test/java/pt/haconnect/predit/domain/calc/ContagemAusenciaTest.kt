package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.model.CategoriaTurno
import java.time.LocalDate

class ContagemAusenciaTest {

    private val T = 100L // id do tipo Trabalho
    private val F = 200L // id do tipo Folga

    private val categorias = mapOf(
        T to CategoriaTurno.TRABALHO,
        F to CategoriaTurno.FOLGA
    )

    private val slots17 = listOf(
        T, T, T, T, F, F,
        T, T, T, T, F, F,
        T, T, T, T, F
    )

    private val ancoradate = LocalDate.of(2026, 7, 1)

    private val aplicacao = AplicacaoVigente(
        validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
        validoAte = null,
        dataAncora = ancoradate.toEpochDay(),
        slots = slots17
    )

    private val feriados = setOf(
        LocalDate.of(2026, 6, 4).toEpochDay(),
        LocalDate.of(2026, 6, 10).toEpochDay()
    )

    @Test
    fun `teste 1 - diasConsumidos de 2026-06-03 a 2026-06-29`() {
        val inicio = LocalDate.of(2026, 6, 3).toEpochDay()
        val fim = LocalDate.of(2026, 6, 29).toEpochDay()

        val diasCalendario = (fim - inicio + 1).toInt()
        val consumidos = diasConsumidos(inicio, fim, feriados)

        assertEquals(27, diasCalendario)
        assertEquals(17, consumidos)
    }

    @Test
    fun `teste 2 - diasConsumidos de 2026-10-06 a 2026-10-12`() {
        val inicio = LocalDate.of(2026, 10, 6).toEpochDay()
        val fim = LocalDate.of(2026, 10, 12).toEpochDay()

        val diasCalendario = (fim - inicio + 1).toInt()
        val consumidos = diasConsumidos(inicio, fim, feriados)

        assertEquals(7, diasCalendario)
        assertEquals(5, consumidos)
    }

    @Test
    fun `teste 3 - dias com turno na escala de 2026-06-03 a 2026-06-29`() {
        val inicio = LocalDate.of(2026, 6, 3).toEpochDay()
        val fim = LocalDate.of(2026, 6, 29).toEpochDay()

        val diasProjetados = projetarIntervalo(inicio, fim, listOf(aplicacao))
        val diasCalendario = diasProjetados.size

        val diasComTurnoEscala = diasProjetados.count { dp ->
            val cat = dp.tipoTurnoId?.let { categorias[it] }
            cat == CategoriaTurno.TRABALHO && dp.epochDay !in feriados
        }

        assertEquals(27, diasCalendario)
        assertEquals(18, diasComTurnoEscala)
    }

    @Test
    fun `teste 4 - dias com turno na escala de 2026-10-06 a 2026-10-12`() {
        val inicio = LocalDate.of(2026, 10, 6).toEpochDay()
        val fim = LocalDate.of(2026, 10, 12).toEpochDay()

        val diasProjetados = projetarIntervalo(inicio, fim, listOf(aplicacao))
        val diasCalendario = diasProjetados.size

        val diasComTurnoEscala = diasProjetados.count { dp ->
            val cat = dp.tipoTurnoId?.let { categorias[it] }
            cat == CategoriaTurno.TRABALHO && dp.epochDay !in feriados
        }

        assertEquals(7, diasCalendario)
        assertEquals(6, diasComTurnoEscala)
    }
}
