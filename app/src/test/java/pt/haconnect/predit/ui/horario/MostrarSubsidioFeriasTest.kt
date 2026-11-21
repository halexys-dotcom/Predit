package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A regra de desenho do subsídio de férias ([mostrarSubsidioFerias]), 2026-11-21.
 *
 * O subsídio é um valor fixo anual: paga-se em julho, em duas metades ou em duodécimos, e a linha
 * não pode mostrar um remanescente que cresce com os meses a quem já o recebeu. O que decide é o
 * que já foi pago no ano ([feriasJaPagasMil]) e a estimativa do motor ([feriasEstimadaMil]), que
 * vem a 0,00 a partir de metade do subsídio anual
 * ([pt.haconnect.predit.domain.calc.subsidioFeriasLiquidado]).
 */
class MostrarSubsidioFeriasTest {

    /** 757,61 € — o direito até ao fim de agosto de 2026 (T8 do `EstimadorReciboTest`). */
    private val direitoAteAgosto = 7_576_141L

    /** Sem férias no mês nem no seguinte a linha não existe, haja ou não pagamento. */
    @Test
    fun `sem ferias no calendario a linha nao se desenha`() {
        assertFalse(
            mostrarSubsidioFerias(
                temFeriasNoCalendario = false,
                feriasMil = 0L,
                feriasJaPagasMil = 0L,
                feriasEstimadaMil = direitoAteAgosto
            )
        )
    }

    /** Nada pago no ano: mostra-se o direito, como antes desta regra. */
    @Test
    fun `sem subsidio pago no ano mostra o direito`() {
        assertTrue(
            mostrarSubsidioFerias(
                temFeriasNoCalendario = true,
                feriasMil = 0L,
                feriasJaPagasMil = 0L,
                feriasEstimadaMil = direitoAteAgosto
            )
        )
    }

    /** O recibo do mês traz o subsídio (PDF, gravado ou escrito à mão): mostra-se sempre. */
    @Test
    fun `subsidio que vem no recibo do mes mostra-se sempre`() {
        assertTrue(
            mostrarSubsidioFerias(
                temFeriasNoCalendario = true,
                feriasMil = 827_620L,
                feriasJaPagasMil = direitoAteAgosto,
                feriasEstimadaMil = 0L
            )
        )
    }

    /** Pago metade ou mais do subsídio anual: o estimado é 0,00 e a linha desaparece. */
    @Test
    fun `subsidio pago em julho esconde a linha em outubro`() {
        assertFalse(
            mostrarSubsidioFerias(
                temFeriasNoCalendario = true,
                feriasMil = 0L,
                feriasJaPagasMil = 8_276_200L,   // 827,62 € pagos em julho
                feriasEstimadaMil = 0L
            )
        )
    }

    /** Duodécimos a meio do ano: ainda há algo a receber e a linha mostra o remanescente. */
    @Test
    fun `pagamento parcial abaixo de metade mostra o que falta`() {
        assertTrue(
            mostrarSubsidioFerias(
                temFeriasNoCalendario = true,
                feriasMil = 0L,
                feriasJaPagasMil = 4_000_000L,   // 400,00 € — abaixo de metade dos 1 137,98 €
                feriasEstimadaMil = 3_576_141L   // 357,61 €
            )
        )
    }
}
