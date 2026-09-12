package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import java.time.LocalDate

class EscalaTest {

    private val T = 100L // id do tipo Tarde (Trabalho)
    private val F = 200L // id do tipo Folga (Folga)
    private val FER = 300L // id do tipo Férias (Férias)
    private val FERIADO = 400L // id do tipo Feriado (Feriado)

    private val categorias = mapOf(
        T to CategoriaTurno.TRABALHO,
        F to CategoriaTurno.FOLGA,
        FER to CategoriaTurno.FERIAS,
        FERIADO to CategoriaTurno.FERIADO
    )

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

    // --- TESTES DE AUSÊNCIA ---

    @Test fun `ausencia de 3 dias cobre exatamente esses 3 dias`() {
        val inicio = LocalDate.of(2026, 7, 1).toEpochDay() // Tarde (Trabalho)
        val fim = LocalDate.of(2026, 7, 3).toEpochDay()   // Tarde (Trabalho)
        val ausencia = Ausencia(id = 1, tipoTurnoId = FER, dataInicio = inicio, dataFim = fim)

        val diasProjetados = projetarIntervalo(inicio, fim, listOf(aplicacao))
        val diasComEstado = aplicarAusencias(diasProjetados, listOf(ausencia), categorias)

        assertEquals(3, diasComEstado.size)
        diasComEstado.forEach {
            assertEquals(FER, it.tipoTurnoEfetivoId)
            assertEquals(T, it.tipoTurnoProjetadoId)
            assertNotNull(it.ausenciaBruta)
            assertNotNull(it.ausenciaEfetiva)
        }
    }

    @Test fun `o dia seguinte ao dataFim volta ao turno projetado`() {
        val inicio = LocalDate.of(2026, 7, 1).toEpochDay()
        val fim = LocalDate.of(2026, 7, 3).toEpochDay()
        val diaSeguinte = LocalDate.of(2026, 7, 4).toEpochDay()
        val ausencia = Ausencia(id = 1, tipoTurnoId = FER, dataInicio = inicio, dataFim = fim)

        val diasProjetados = projetarIntervalo(inicio, diaSeguinte, listOf(aplicacao))
        val diasComEstado = aplicarAusencias(diasProjetados, listOf(ausencia), categorias)

        val estadoDiaSeguinte = diasComEstado.last()
        assertEquals(T, estadoDiaSeguinte.tipoTurnoEfetivoId)
        assertNull(estadoDiaSeguinte.ausenciaBruta)
        assertNull(estadoDiaSeguinte.ausenciaEfetiva)
    }

    @Test fun `o turno projetado por baixo continua a ser legivel`() {
        val inicio = LocalDate.of(2026, 7, 1).toEpochDay()
        val ausencia = Ausencia(id = 1, tipoTurnoId = FER, dataInicio = inicio, dataFim = inicio)

        val diasProjetados = projetarIntervalo(inicio, inicio, listOf(aplicacao))
        val diasComEstado = aplicarAusencias(diasProjetados, listOf(ausencia), categorias)

        val estado = diasComEstado.first()
        assertEquals(T, estado.tipoTurnoProjetadoId)
        assertEquals(FER, estado.tipoTurnoEfetivoId)
    }

    @Test fun `duas ausencias que nao se sobrepoem coexistem`() {
        val a1 = Ausencia(id = 1, tipoTurnoId = FER, dataInicio = LocalDate.of(2026, 7, 1).toEpochDay(), dataFim = LocalDate.of(2026, 7, 2).toEpochDay())
        val a2 = Ausencia(id = 2, tipoTurnoId = FER, dataInicio = LocalDate.of(2026, 7, 8).toEpochDay(), dataFim = LocalDate.of(2026, 7, 9).toEpochDay())

        val diasProjetados = projetarIntervalo(
            LocalDate.of(2026, 7, 1).toEpochDay(),
            LocalDate.of(2026, 7, 10).toEpochDay(),
            listOf(aplicacao)
        )
        val diasComEstado = aplicarAusencias(diasProjetados, listOf(a1, a2), categorias)

        val dia1 = diasComEstado.find { it.epochDay == LocalDate.of(2026, 7, 1).toEpochDay() }
        val dia8 = diasComEstado.find { it.epochDay == LocalDate.of(2026, 7, 8).toEpochDay() }
        val dia5 = diasComEstado.find { it.epochDay == LocalDate.of(2026, 7, 5).toEpochDay() }

        assertEquals(FER, dia1?.tipoTurnoEfetivoId)
        assertEquals(FER, dia8?.tipoTurnoEfetivoId)
        assertEquals(F, dia5?.tipoTurnoEfetivoId) // Folga mantida
    }

    // --- NOVOS TESTES OBRIGATÓRIOS ---

    @Test fun `ausencia de 7 dias sobre ciclo com turnos e folgas - folgas mantem F`() {
        // Julho 1 a 7 (dias 1..4 = Tarde, dias 5..6 = Folga, dia 7 = Tarde)
        val inicio = LocalDate.of(2026, 7, 1).toEpochDay()
        val fim = LocalDate.of(2026, 7, 7).toEpochDay()
        val ausenciaSemana = Ausencia(id = 1, tipoTurnoId = FER, dataInicio = inicio, dataFim = fim)

        val diasProjetados = projetarIntervalo(inicio, fim, listOf(aplicacao))
        val diasComEstado = aplicarAusencias(diasProjetados, listOf(ausenciaSemana), categorias)

        // Dias 1, 2, 3, 4 (Trabalho) -> FER
        // Dias 5, 6 (Folga) -> F (Folga mantida!)
        // Dia 7 (Trabalho) -> FER
        val efetivos = diasComEstado.map { it.tipoTurnoEfetivoId }
        assertEquals(listOf(FER, FER, FER, FER, F, F, FER), efetivos)

        // Em todos os 7 dias, a ausenciaBruta está presente para o botão Remover funcionar
        diasComEstado.forEach {
            assertNotNull(it.ausenciaBruta)
        }
        // Mas a ausenciaEfetiva está presente apenas nos 5 dias de trabalho
        val diasComEfetiva = diasComEstado.filter { it.ausenciaEfetiva != null }.map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }
        assertEquals(listOf(1, 2, 3, 4, 7), diasComEfetiva)
    }

    @Test fun `ausencia de 3 dias que cobre um feriado - o feriado mantem-se`() {
        val inicio = LocalDate.of(2026, 12, 24).toEpochDay()
        val feriadoDia = LocalDate.of(2026, 12, 25).toEpochDay()
        val fim = LocalDate.of(2026, 12, 26).toEpochDay()

        val aplicacaoFeriado = AplicacaoVigente(
            validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
            validoAte = null,
            dataAncora = LocalDate.of(2026, 1, 1).toEpochDay(),
            slots = listOf(T, FERIADO, T) // Dia 24 = Tarde, Dia 25 = Feriado, Dia 26 = Tarde
        )

        val ausenciaFérias = Ausencia(id = 1, tipoTurnoId = FER, dataInicio = inicio, dataFim = fim)

        val diasProjetados = projetarIntervalo(inicio, fim, listOf(aplicacaoFeriado))
        val diasComEstado = aplicarAusencias(diasProjetados, listOf(ausenciaFérias), categorias)

        val dia24 = diasComEstado.find { it.epochDay == inicio }
        val dia25 = diasComEstado.find { it.epochDay == feriadoDia }
        val dia26 = diasComEstado.find { it.epochDay == fim }

        assertEquals(FER, dia24?.tipoTurnoEfetivoId)
        assertEquals(FERIADO, dia25?.tipoTurnoEfetivoId) // Feriado mantido intacto!
        assertEquals(FER, dia26?.tipoTurnoEfetivoId)
    }
}
