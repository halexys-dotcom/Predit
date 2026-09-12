package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EscalaTest {

    private val T = 100L // id do tipo Tarde
    private val F = 200L // id do tipo Folga

    private val slots17 = listOf(
        T, T, T, T, F, F,
        T, T, T, T, F, F,
        T, T, T, T, F
    ) // T = id do tipo Tarde, F = id do tipo Folga

    private val aplicacao = AplicacaoVigente(
        validoDe = LocalDate.of(2026, 7, 1).toEpochDay(),
        validoAte = null,
        dataAncora = LocalDate.of(2026, 7, 1).toEpochDay(),
        slots = slots17
    )

    @Test fun `julho 2026 projeta as folgas do PDF da empresa`() {
        val folgas = projetarIntervalo(
            LocalDate.of(2026, 7, 1).toEpochDay(),
            LocalDate.of(2026, 7, 31).toEpochDay(),
            listOf(aplicacao)
        ).filter { it.tipoTurnoId == F }
         .map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }

        assertEquals(listOf(5, 6, 11, 12, 17, 22, 23, 28, 29), folgas)
    }

    @Test fun `setembro 2026 corresponde ao calendario real`() {
        val folgas = projetarIntervalo(
            LocalDate.of(2026, 9, 1).toEpochDay(),
            LocalDate.of(2026, 9, 30).toEpochDay(),
            listOf(aplicacao)
        ).filter { it.tipoTurnoId == F }
         .map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }

        assertEquals(listOf(1, 6, 11, 12, 17, 18, 23, 28, 29), folgas)
    }

    @Test fun `dia anterior ao validoDe nao tem turno`() {
        val d = projetarDia(LocalDate.of(2026, 6, 30).toEpochDay(), listOf(aplicacao))
        assertNull(d.tipoTurnoId)
    }

    @Test fun `com duas aplicacoes vence a mais recente`() {
        val N = 300L // id de outro tipo de turno
        val aplicacaoAntiga = AplicacaoVigente(
            validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
            validoAte = null,
            dataAncora = LocalDate.of(2026, 1, 1).toEpochDay(),
            slots = listOf(N)
        )
        val aplicacaoNova = AplicacaoVigente(
            validoDe = LocalDate.of(2026, 7, 1).toEpochDay(),
            validoAte = null,
            dataAncora = LocalDate.of(2026, 7, 1).toEpochDay(),
            slots = listOf(T)
        )

        val dia = LocalDate.of(2026, 7, 10).toEpochDay()
        val resultado = projetarDia(dia, listOf(aplicacaoAntiga, aplicacaoNova))
        assertEquals(T, resultado.tipoTurnoId)
    }

    @Test fun `no dia exato do validoAte a aplicacao ainda vigora`() {
        val aplicacaoComFim = AplicacaoVigente(
            validoDe = LocalDate.of(2026, 7, 1).toEpochDay(),
            validoAte = LocalDate.of(2026, 7, 31).toEpochDay(),
            dataAncora = LocalDate.of(2026, 7, 1).toEpochDay(),
            slots = listOf(T)
        )

        val dia31Julho = LocalDate.of(2026, 7, 31).toEpochDay()
        val dia1Agosto = LocalDate.of(2026, 8, 1).toEpochDay()

        assertEquals(T, projetarDia(dia31Julho, listOf(aplicacaoComFim)).tipoTurnoId)
        assertNull(projetarDia(dia1Agosto, listOf(aplicacaoComFim)).tipoTurnoId)
    }
}
