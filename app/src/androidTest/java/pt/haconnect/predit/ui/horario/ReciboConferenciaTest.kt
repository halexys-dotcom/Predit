package pt.haconnect.predit.ui.horario

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.haconnect.predit.MainActivity
import pt.haconnect.predit.PreditApplication
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Fase 8.2b.2b — separador Recibo, exercitado ponta a ponta na app real.
 *
 * Escreve no mês corrente do dispositivo e apaga no fim o recibo que gravou (limparReciboDeTeste),
 * para não deixar lixo no histórico. Como o connectedAndroidTest desinstala a app no fim de cada
 * corrida, o contrato é configurado aqui à imagem do FecharSemestreTest.
 *
 * Nomes sem acentos nem espaços: o dexer com minSdk 26 recusa espaços em nomes de método.
 */
@RunWith(AndroidJUnit4::class)
class ReciboConferenciaTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** O teste escreve no mês corrente do dispositivo — nada de datas fixas. */
    private val mes = YearMonth.now()
    private val chave = "%04d-%02d".format(mes.year, mes.monthValue)

    private var gravouRecibo = false

    private val app: PreditApplication
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PreditApplication

    /** Apaga o recibo do mês de teste; o CASCADE de recibo_mes leva as linhas atrás. */
    private fun limparReciboDeTeste() {
        app.database.openHelper.writableDatabase
            .execSQL("DELETE FROM recibo_mes WHERE anoMes = '$chave'")
        gravouRecibo = false
    }

    /**
     * Numa instalação limpa o contrato nasce por configurar e a app fica no primeiro arranque.
     * A linha do contrato é criada pela app no onOpen, de forma assíncrona — se aqui se
     * desistisse quando ela ainda não existe, o ecrã ficava no primeiro arranque e o teste
     * morria à espera do "Horário". Por isso espera-se pela linha.
     */
    private fun garantirContratoConfigurado() {
        val dao = app.database.contratoDao()
        var contrato = runBlocking { dao.observar().first() }
        var tentativas = 0
        while (contrato == null && tentativas < 40) {
            Thread.sleep(250)
            contrato = runBlocking { dao.observar().first() }
            tentativas++
        }

        val existente = contrato ?: return
        if (existente.dataAdmissao != null && existente.primeiroArranqueConcluido) return

        runBlocking {
            dao.guardar(
                existente.copy(
                    dataAdmissao = existente.dataAdmissao ?: LocalDate.of(2020, 1, 1).toEpochDay(),
                    primeiroArranqueConcluido = true
                )
            )
        }
    }

    /**
     * A semente (PreditApplication.onOpen) corre fora do ecrã e escreve o catálogo, os
     * parâmetros do CCT e as tabelas de IRS. O ecrã precisa das três: com a de IRS vazia o
     * motor não consegue estimar. Espera-se aqui, sem tocar no Compose (que bloquearia).
     */
    private fun esperarSemente() {
        var esperas = 0
        while (esperas < 120) {
            val pronta = runBlocking {
                app.database.rubricaDao().contar() >= 20 &&
                    app.database.parametrosCCTDao().contar() >= 2 &&
                    app.database.tabelaIRSDao().contarPorAno(2026) >= 308
            }
            if (pronta) return
            Thread.sleep(250)
            esperas++
        }
    }

    @Before
    fun prepararSe() {
        garantirContratoConfigurado()
        // Cada teste começa com o mês limpo: sem isto, um recibo que fique gravado de uma
        // corrida anterior (por exemplo quando um teste falha a meio) faz o T1 falhar.
        limparReciboDeTeste()
        esperarSemente()
    }

    @After
    fun apagarReciboDeTeste() {
        if (gravouRecibo) limparReciboDeTeste()
    }

    private fun esperarPorTexto(texto: String, timeoutMs: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Para ícones, que têm contentDescription em vez de texto. */
    private fun esperarPorDescricao(descricao: String, timeoutMs: Long = 10_000) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule
                .onAllNodesWithContentDescription(descricao)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** Mais -> Recibo de vencimento, já com a primeira rubrica no ecrã. */
    private fun abrirRecibo() {
        esperarPorTexto("Mais")
        composeTestRule.onNodeWithText("Mais").performClick()
        esperarPorTexto("Recibo de vencimento")

        // O toque no cartão às vezes cai a meio da transição e não navega: repete até o
        // ecrã abrir. O marcador é a action do topo do recibo ("Ir para hoje"): desde a Fase 12a
        // (commit 900f815) os botões da barra são ícones e o antigo texto "Hoje" já não existe
        // nesse ecrã — esperar por ele fazia o ciclo repetir depois de já ter navegado, e o toque
        // seguinte no cartão falhava contra o ecrã do recibo (falha intermitente).
        var tentativas = 0
        while (tentativas < 4) {
            composeTestRule.onNodeWithTag("entrada-recibo").performClick()
            try {
                esperarPorDescricao("Ir para hoje", timeoutMs = 8_000)
                break
            } catch (_: ComposeTimeoutException) {
                tentativas++
            }
        }

        try {
            esperarPorTexto("Vencimento", timeoutMs = 25_000)
        } catch (_: ComposeTimeoutException) {
            val noEcra = listOf(
                "A carregar catálogo e parâmetros...",
                "Sem recibo introduzido",
                "Ainda a carregar os parâmetros e as tabelas de retenção.",
                "Hoje",
                "ABONOS",
                "Recibo de vencimento"
            ).filter { composeTestRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            throw AssertionError("A lista nao apareceu. No ecra: $noEcra")
        }
    }

    private fun campo(codigo: String) = composeTestRule.onNodeWithTag("campo-$codigo")

    /** A lista tem scroll: o campo de uma rubrica mais abaixo precisa de ser trazido à vista. */
    private fun irParaRubrica(codigo: String) {
        campo(codigo).performScrollTo()
    }

    private fun nomeDoMes(): String {
        val locale = Locale("pt", "PT")
        val nome = mes.month.getDisplayName(TextStyle.FULL, locale)
        val capitalizado = nome.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
        return "$capitalizado ${mes.year}"
    }

    @Test
    fun T1_reciboMostraCatalogoEMesCorrente() {
        abrirRecibo()

        // Cabeçalho com o mês corrente por extenso
        composeTestRule.onNodeWithText(nomeDoMes()).assertExists()

        // A primeira rubrica, com o estimado do CCT de 2026 (1 137,98 €). Fase 18c: a lista
        // mostra só o nome da rubrica (o código VENC/HNOT/D01 deixou de aparecer).
        composeTestRule.onNodeWithText("Vencimento").assertExists()
        composeTestRule.onNodeWithText("Estimado: 1 137,98 €").assertExists()

        // O miolo e o fim do catálogo, depois de rolar até lá (a lista é lazy)
        irParaRubrica("D01")
        composeTestRule.onNodeWithText("Segurança Social (11%)").assertExists()

        irParaRubrica("D04")
        composeTestRule.onNodeWithText("Sindicato (1%)").assertExists()

        irParaRubrica("OUTROS")
        composeTestRule.onNodeWithText("Outros").assertExists()

        // Mês sem recibo: o cabeçalho di-lo e não há nada para apagar
        irParaRubrica("VENC")
        composeTestRule.onNodeWithText("Sem recibo introduzido").assertExists()
        // As acções da barra são ícones desde a Fase 12a: o apagar vive no menu de overflow.
        composeTestRule.onNodeWithContentDescription("Mais opções").performClick()
        composeTestRule.onNodeWithText("Apagar registo").assertIsNotEnabled()
    }

    @Test
    fun T2_editarValorReal_mostraADivergenciaComOSinal() {
        abrirRecibo()

        // 1 100,00 € contra os 1 137,98 € estimados: 37,98 € a menos
        campo("VENC").performTextReplacement("1100,00")

        composeTestRule.onNodeWithTag("divergencia-VENC")
            .assertExists()
            .assertTextContains("37,98", substring = true)
    }

    @Test
    fun T3_guardarPersisteEVoltaAParecerDepoisDeSairDoEcra() {
        abrirRecibo()

        campo("VENC").performTextReplacement("1137,98")
        composeTestRule.onNodeWithContentDescription("Guardar recibo").performClick()

        // Gravou: cabeçalho + uma linha por rubrica do catálogo. A escrita é assíncrona,
        // por isso espera-se aqui fora (dentro do waitUntil do Compose bloquearia).
        var gravado = runBlocking { app.database.reciboMesDao().obterPorMes(chave) }
        var tentativas = 0
        while (gravado == null && tentativas < 40) {
            Thread.sleep(250)
            gravado = runBlocking { app.database.reciboMesDao().obterPorMes(chave) }
            tentativas++
        }
        gravouRecibo = true
        assertNotNull("o recibo do mês tem de ficar gravado", gravado)

        val cabecalho = gravado!!
        assertEquals(chave, cabecalho.anoMes)

        val linhas = runBlocking { app.database.reciboLinhaDao().obterPorMes(chave) }
        // Uma linha por rubrica do catálogo: 21 desde a 13a (o SUP_FUNCAO entrou no catálogo).
        assertEquals("uma linha por rubrica do catálogo", 21, linhas.size)

        val idVenc = runBlocking { app.database.rubricaDao().obterPorCodigo("VENC") }!!.id
        val linhaVenc = linhas.first { it.rubricaId == idVenc }
        assertEquals(11_379_800L, linhaVenc.valorReal)
        assertEquals(11_379_800L, linhaVenc.valorEstimado)

        // Volta atrás e entra outra vez: o ecrã relê da base e os valores continuam lá
        composeTestRule.onNodeWithContentDescription("Voltar").performClick()
        esperarPorTexto("Recibo de vencimento")
        composeTestRule.onNodeWithTag("entrada-recibo").performClick()
        // Reabrir o ecrã volta a compor tudo do zero: o mesmo tempo generoso da 1.ª abertura.
        esperarPorTexto("Vencimento", timeoutMs = 25_000)

        campo("VENC").assertTextContains("1 137,98", substring = true)
        // Já gravado e sem alterações por guardar
        composeTestRule.onNodeWithContentDescription("Guardar recibo").assertIsNotEnabled()
    }
}
