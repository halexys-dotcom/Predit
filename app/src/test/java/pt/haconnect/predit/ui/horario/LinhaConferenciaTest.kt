package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.calc.RubricaEstimada
import pt.haconnect.predit.domain.model.ReciboLinha
import pt.haconnect.predit.domain.model.Rubrica

/**
 * A precedência do ecrã de conferência (v2.2.0), rubrica a rubrica.
 *
 * O caso que a fixou é o D02 de setembro/2026: a base de 1 163,41 € com os dois dependentes dá
 * 45,60 € na tabela I de 2026, mas a linha gravada guardava 88,46 € — o snapshot de quando o
 * contrato tinha 0 dependentes. O ecrã tem de mostrar a conta de hoje (o Estimado é sempre o motor
 * fresco) e manter os 46,00 € do recibo como Real.
 */
class LinhaConferenciaTest {

    @Test
    fun `o estimado e o calculo de hoje e nao o snapshot gravado`() {
        val linha = linhaConferencia(
            rubrica = rubrica("D02"),
            estimada = estimada(456_000L),
            guardada = gravada(estimado = 884_600L, real = 460_000L),
            textoEditado = null,
            valorEditado = null
        )

        // 45,60 € do motor contra os 88,46 € do snapshot antigo: ganha o motor.
        assertEquals(456_000L, linha.valorEstimado)
        // 46,00 € do recibo de setembro: o histórico não se reescreve ao abrir o ecrã.
        assertEquals(460_000L, linha.valorReal)
        assertEquals("46,00", linha.textoRealEditavel)
        assertFalse(linha.textoInvalido)
    }

    @Test
    fun `sem recibo gravado o real e o estimado`() {
        val linha = linhaConferencia(
            rubrica = rubrica("D02"),
            estimada = estimada(456_000L),
            guardada = null,
            textoEditado = null,
            valorEditado = null
        )

        assertEquals(456_000L, linha.valorEstimado)
        assertEquals(456_000L, linha.valorReal)
        assertEquals("45,60", linha.textoRealEditavel)
    }

    @Test
    fun `a edicao do utilizador vence o recibo gravado`() {
        val linha = linhaConferencia(
            rubrica = rubrica("D02"),
            estimada = estimada(456_000L),
            guardada = gravada(estimado = 884_600L, real = 460_000L),
            textoEditado = "47,10",
            valorEditado = 471_000L
        )

        assertEquals(471_000L, linha.valorReal)
        assertEquals("47,10", linha.textoRealEditavel)
        assertFalse(linha.textoInvalido)
    }

    /**
     * Esvaziar o campo depois de um valor válido mantém o valor lido: é o que
     * [ReciboViewModel.atualizarValorReal] guarda na edição (`novo ?: anterior?.valor`), para o
     * utilizador não perder a conta por apagar o campo.
     */
    @Test
    fun `campo esvaziado mantem o ultimo valor lido`() {
        val linha = linhaConferencia(
            rubrica = rubrica("D02"),
            estimada = estimada(456_000L),
            guardada = gravada(estimado = 884_600L, real = 460_000L),
            textoEditado = "",
            valorEditado = 460_000L
        )

        assertEquals(460_000L, linha.valorReal)
        assertEquals("", linha.textoRealEditavel)
        assertFalse(linha.textoInvalido)
    }

    /** Um texto que não se lê marca o campo e deixa o real como estava. */
    @Test
    fun `texto invalido nao mexe no real`() {
        val linha = linhaConferencia(
            rubrica = rubrica("D02"),
            estimada = estimada(456_000L),
            guardada = gravada(estimado = 884_600L, real = 460_000L),
            textoEditado = "abc",
            valorEditado = null
        )

        assertEquals(460_000L, linha.valorReal)
        assertEquals("abc", linha.textoRealEditavel)
        assertTrue(linha.textoInvalido)
    }

    /** Sem estimativa (rubrica fora do motor, como "Outros") a linha nasce a zero e é abono. */
    @Test
    fun `sem estimativa a rubrica cai a zero e e abono`() {
        val linha = linhaConferencia(
            rubrica = rubrica("OUTROS"),
            estimada = null,
            guardada = null,
            textoEditado = null,
            valorEditado = null
        )

        assertEquals(0L, linha.valorEstimado)
        assertEquals(0L, linha.valorReal)
        assertEquals(NaturezaRubrica.ABONO, linha.natureza)
        assertEquals("0,00", linha.textoRealEditavel)
    }

    private fun rubrica(codigo: String) = Rubrica(
        id = ID,
        codigo = codigo,
        nome = "IRS",
        incideSS = false,
        incideIRS = false,
        incideSindicato = false,
        tipoCalculo = "DERIVADO",
        ativaConferencia = true,
        ordem = 5
    )

    private fun estimada(valorMil: Long) = RubricaEstimada(
        rubricaId = ID,
        codigo = "D02",
        nome = "IRS",
        valorMil = valorMil,
        natureza = NaturezaRubrica.DESCONTO
    )

    private fun gravada(estimado: Long, real: Long) = ReciboLinha(
        reciboMesId = "2026-09",
        rubricaId = ID,
        valorEstimado = estimado,
        valorReal = real,
        natureza = NaturezaRubrica.DESCONTO,
        ordem = 5
    )

    private companion object {
        /** O id da rubrica D02 na semente: as três fontes têm de apontar para a mesma. */
        const val ID = 7L
    }
}
