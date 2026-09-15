package pt.haconnect.predit.ui.horario

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.haconnect.predit.MainActivity
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.local.DiaRealEntity
import java.time.LocalDate

/**
 * Fase 7 — fecho manual de ciclo, exercitado ponta a ponta na app real.
 *
 * Usa o 2.º semestre de 2025 (julho a dezembro), fora de qualquer uso real,
 * para não mexer no histórico do utilizador. Os registos criados e a linha
 * de ciclo_jornada são apagados no fim (limparBdTeste).
 *
 * Dados: dois dias reais em julho de 2025, 08:00-16:00 (480 min cada):
 *   real total        = 960 min                (julho 16:00, resto 00:00)
 *   jornada do ciclo  = 6 x 10 399 = 62 394    (1039:54)
 *   extras pagos      = 0                      (960 < 62 394, tudo abate)
 *   saldo final       = 960 - 62 394 = -61 434 (-1023:54)
 */
@RunWith(AndroidJUnit4::class)
class FecharSemestreTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val idCiclo = "2025-S2"

    /** 2025-07-01 e 2025-07-02 — epochDay calculado, sem literais à mão. */
    private val diasDeTeste = listOf(
        LocalDate.of(2025, 7, 1).toEpochDay(),
        LocalDate.of(2025, 7, 2).toEpochDay()
    )

    private var dadosDeTesteCriados = false

    private val app: PreditApplication
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PreditApplication

    private fun limparBdTeste() {
        val bd = app.database.openHelper.writableDatabase
        bd.execSQL("DELETE FROM ciclo_jornada WHERE id = '$idCiclo'")
        bd.execSQL("DELETE FROM dia_real WHERE data IN (${diasDeTeste.joinToString(", ")})")
    }

    @Before
    fun prepararSemestreDeTeste() {
        // Nunca apagar dados do utilizador: se o semestre ou os dias de teste já
        // tiverem registos, o teste é ignorado (skipped) em vez de os destruir.
        val jaExistem = runBlocking {
            val ciclo = app.database.cicloJornadaDao().obterPorId(idCiclo)
            val dias = app.database.diaRealDao()
                .obterNoIntervalo(diasDeTeste.first(), diasDeTeste.last())
            ciclo != null || dias.isNotEmpty()
        }
        assumeTrue(
            "o semestre $idCiclo ou os dias $diasDeTeste já têm registos — teste ignorado",
            !jaExistem
        )

        limparBdTeste()
        runBlocking {
            diasDeTeste.forEach { dia ->
                app.database.diaRealDao().upsert(
                    DiaRealEntity(
                        data = dia,
                        tipoTurnoId = null,
                        inicioMin = 8 * 60,
                        fimMin = 16 * 60,
                        pausaMin = 0,
                        nota = "teste fase 7",
                        origem = "MANUAL"
                    )
                )
            }
        }
        dadosDeTesteCriados = true
    }

    @After
    fun apagarSemestreDeTeste() {
        if (dadosDeTesteCriados) limparBdTeste()
    }

    @Test
    fun fecharSemestre_2S2025_gravaCicloEMostraEstadoFechado() {
        // Horário -> Conferência
        composeTestRule.onNodeWithText("Horário").performClick()
        esperarPorTexto("Conferência")
        composeTestRule.onNodeWithText("Conferência").performClick()

        // O semestre inicial é o atual (2.º de 2026, data do dispositivo). Recuar dois.
        esperarPorTexto("2º Semestre 2026")
        repeat(2) {
            composeTestRule.onNodeWithContentDescription("Semestre anterior").performClick()
        }
        esperarPorTexto("2º Semestre 2025")

        // Julho de 2025 com os 960 min registados (16:00) e rodapé provisório
        composeTestRule.onNodeWithText("Julho 2025").assertExists()
        composeTestRule.onAllNodesWithText("real 16:00", substring = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText("(provisório)", substring = true).assertCountEquals(1)

        // Ciclo terminado e sem linha em ciclo_jornada -> botão de fecho visível
        composeTestRule.onNodeWithText("Fechar semestre").assertExists().performClick()

        // Snackbar do fecho, com os dois valores calculados (00:00 pagos, −1023:54 limpos).
        // O sinal é escrito como \u2212 (o mesmo que a app usa em formatarHoraMin).
        val menos = "\u2212"
        esperarPorPrefixo("Semestre fechado. Extras pagos: 00:00. Défice limpo: ${menos}1023:54.")

        // Estado FECHADO: o botão desaparece e surge o texto do fecho
        esperarPorPrefixo("Fechado em ")
        composeTestRule.onAllNodesWithText("(limpo em ", substring = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Fechar semestre").assertCountEquals(0)

        // Linha gravada em ciclo_jornada, com os valores do motor de cálculo
        val guardado = runBlocking { app.database.cicloJornadaDao().obterPorId(idCiclo) }
        assertNotNull("ciclo_jornada deve ter a linha $idCiclo", guardado)
        assertEquals(960, guardado!!.realTotalMinutos)
        assertEquals(0, guardado.extrasPagosMinutos)
        assertEquals(-61_434, guardado.saldoFinalMinutos)
        assertEquals(LocalDate.of(2025, 7, 1).toEpochDay(), guardado.inicio)
        assertEquals(LocalDate.of(2025, 12, 31).toEpochDay(), guardado.fim)
        assertTrue("dataFecho deve ficar preenchida", guardado.dataFecho > 0L)
    }

    private fun esperarPorTexto(texto: String, timeoutMs: Long = 10_000) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarPorPrefixo(prefixo: String, timeoutMs: Long = 10_000) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule
                .onAllNodesWithText(prefixo, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
