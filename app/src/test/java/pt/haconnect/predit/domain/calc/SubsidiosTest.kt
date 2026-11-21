package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import java.time.LocalDate
import java.time.YearMonth

/**
 * A regra do subsídio de férias (v2.1.1): a linha mostra-se quando há férias no próprio mês ou no
 * mês seguinte. Exemplo do utilizador: férias de 10 a 24 de agosto mostram o subsídio em julho e
 * em agosto, e escondem-no em setembro.
 */
class SubsidiosTest {

    private val ferias = 1L
    private val baixa = 2L
    private val categorias = mapOf(
        ferias to CategoriaTurno.FERIAS,
        baixa to CategoriaTurno.BAIXA
    )

    /** Férias de 10 a 24 de agosto de 2026, a ausência do exemplo. */
    private val feriasDeAgosto = listOf(
        Ausencia(tipoTurnoId = ferias, dataInicio = dia(2026, 8, 10), dataFim = dia(2026, 8, 24))
    )

    @Test
    fun `ferias em agosto mostram o subsidio em julho e em agosto`() {
        assertTrue(temFeriasNoMesOuSeguinte(feriasDeAgosto, categorias, YearMonth.of(2026, 7)))
        assertTrue(temFeriasNoMesOuSeguinte(feriasDeAgosto, categorias, YearMonth.of(2026, 8)))
    }

    @Test
    fun `ferias em agosto nao mostram o subsidio em junho nem em setembro`() {
        assertFalse(temFeriasNoMesOuSeguinte(feriasDeAgosto, categorias, YearMonth.of(2026, 6)))
        assertFalse(temFeriasNoMesOuSeguinte(feriasDeAgosto, categorias, YearMonth.of(2026, 9)))
    }

    /**
     * Não é só o mês de início que conta: uma ausência que começa já no mês seguinte, no último
     * dia, também põe o subsídio à vista no mês corrente. E o mesmo do lado oposto: férias que
     * acabam no primeiro dia do mês contam nesse mês.
     */
    @Test
    fun `basta a ausencia tocar no mes ou no seguinte`() {
        val noUltimoDiaDeSetembro = listOf(
            Ausencia(tipoTurnoId = ferias, dataInicio = dia(2026, 9, 30), dataFim = dia(2026, 10, 9))
        )
        assertTrue(
            temFeriasNoMesOuSeguinte(noUltimoDiaDeSetembro, categorias, YearMonth.of(2026, 8))
        )

        val acabaNoPrimeiroDia = listOf(
            Ausencia(tipoTurnoId = ferias, dataInicio = dia(2026, 7, 25), dataFim = dia(2026, 8, 1))
        )
        assertTrue(temFeriasNoMesOuSeguinte(acabaNoPrimeiroDia, categorias, YearMonth.of(2026, 8)))
    }

    /**
     * Só as férias contam: uma baixa médica no mês não faz aparecer o subsídio de férias. A
     * categoria vem do tipo de turno da ausência, não do dia.
     */
    @Test
    fun `ausencia que nao e ferias nao mostra o subsidio`() {
        val baixaDeAgosto = listOf(
            Ausencia(tipoTurnoId = baixa, dataInicio = dia(2026, 8, 10), dataFim = dia(2026, 8, 24))
        )
        assertFalse(temFeriasNoMesOuSeguinte(baixaDeAgosto, categorias, YearMonth.of(2026, 8)))
    }

    /** Sem catálogo de tipos não se sabe o que é a ausência: não se mostra nada. */
    @Test
    fun `sem tipos conhecidos nao mostra o subsidio`() {
        assertFalse(temFeriasNoMesOuSeguinte(feriasDeAgosto, emptyMap(), YearMonth.of(2026, 8)))
    }

    /** Sem ausências nenhumas, o subsídio de férias não se desenha em nenhum mês. */
    @Test
    fun `sem ausencias nao mostra o subsidio`() {
        assertFalse(temFeriasNoMesOuSeguinte(emptyList(), categorias, YearMonth.of(2026, 8)))
    }

    /**
     * A regra dos 50 % (2026-11-21): em Portugal o subsídio de férias paga-se todo de uma vez ou em
     * duas metades (50 % + 50 %), por isso quem já recebeu metade ou mais não vai receber um
     * remanescente — o subsídio fecha e o estimado fica a 0,00. Abaixo de metade ainda é o caso das
     * prestações e dos duodécimos, e o estimador mostra o que falta.
     */
    @Test
    fun `o subsidio de ferias fecha a partir de metade do direito do ano`() {
        val direito = 11_379_800L    // 1 137,98 € — o vencimento base de 2026, um ano inteiro
        assertFalse(subsidioFeriasLiquidado(0L, direito))           // nada pago: ainda não fecha
        assertFalse(subsidioFeriasLiquidado(5_689_800L, direito))   // 568,98 € — um cêntimo abaixo
        assertTrue(subsidioFeriasLiquidado(5_689_900L, direito))    // 568,99 € — metade exata
        assertTrue(subsidioFeriasLiquidado(8_276_200L, direito))    // 827,62 € — o recibo de outubro
    }

    private fun dia(ano: Int, mes: Int, dia: Int): Long =
        LocalDate.of(ano, mes, dia).toEpochDay()
}
