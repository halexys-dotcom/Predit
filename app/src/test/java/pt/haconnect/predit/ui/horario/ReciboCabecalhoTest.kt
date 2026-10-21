package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2026-10-21: o cabeçalho do recibo conta para o "Guardar".
 *
 * A nota, a data de fecho e o IRS retido no ano não são linhas de rubrica — antes desta regra,
 * escrevê-los num mês já gravado não acendia o Guardar (as linhas não tinham mudado) e a edição
 * perdia-se ao sair do ecrã.
 *
 * A referência é o que está gravado; sem nada gravado, a data de fecho vale o fim do mês (é o
 * que o ecrã mostra) e o IRS vale 0. Texto inválido não se confunde com alteração: o campo mostra
 * a mensagem de erro e o valor que não se conseguiu ler não é gravado.
 */
class ReciboCabecalhoTest {

    private val dataFecho = LocalDate.of(2026, 7, 31).toEpochDay()
    private val irsRetido = 1_070_000L    // 107,00 € acumulados no ano
    private val nota = "Recibo conferido"

    @Test
    fun `sem edicoes no cabecalho nao ha alteracoes`() {
        assertFalse(cabecalhoAlterado(null, null, null, dataFecho, irsRetido, nota))
    }

    @Test
    fun `mudar a nota conta como alteracao`() {
        assertTrue(cabecalhoAlterado(null, null, "Outra nota", dataFecho, irsRetido, nota))
    }

    @Test
    fun `escrever a mesma nota nao conta`() {
        assertFalse(cabecalhoAlterado(null, null, nota, dataFecho, irsRetido, nota))
    }

    /** Apagar a nota de um mês que a tinha também é uma alteração a guardar. */
    @Test
    fun `limpar a nota gravada conta como alteracao`() {
        assertTrue(cabecalhoAlterado(null, null, "", dataFecho, irsRetido, nota))
    }

    @Test
    fun `mudar a data de fecho conta como alteracao`() {
        assertTrue(cabecalhoAlterado("30/07/2026", null, null, dataFecho, irsRetido, nota))
        assertFalse(cabecalhoAlterado("31/07/2026", null, null, dataFecho, irsRetido, nota))
    }

    @Test
    fun `mudar o irs retido conta como alteracao`() {
        assertTrue(cabecalhoAlterado(null, "158,00", null, dataFecho, irsRetido, nota))
        assertFalse(cabecalhoAlterado(null, "107,00", null, dataFecho, irsRetido, nota))
    }

    @Test
    fun `texto invalido nao se confunde com alteracao`() {
        assertFalse(cabecalhoAlterado("xx/xx/xxxx", null, null, dataFecho, irsRetido, nota))
        assertFalse(cabecalhoAlterado(null, "abc", null, dataFecho, irsRetido, nota))
    }

    @Test
    fun `mes sem nada gravado usa o fim do mes e o zero como referencia`() {
        val fimDoMes = LocalDate.of(2026, 7, 31).toEpochDay()

        assertFalse(cabecalhoAlterado("31/07/2026", "0,00", "", fimDoMes, 0L, null))
        assertTrue(cabecalhoAlterado("30/07/2026", null, null, fimDoMes, 0L, null))
        assertTrue(cabecalhoAlterado(null, "10,00", null, fimDoMes, 0L, null))
    }
}
