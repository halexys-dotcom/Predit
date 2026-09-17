package pt.haconnect.predit.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.haconnect.predit.data.local.PreditDatabase
import java.io.File

/**
 * Backups internos (Fase 11b) exercitados contra ficheiros a sério.
 *
 * Usa uma BD Room própria (nasce na versão atual do esquema) e uma pasta de backups própria
 * em cacheDir: se usasse a pasta da app, as contagens misturavam-se com as cópias que o
 * auto-backup vai deixando a cada arranque no telefone.
 *
 * Nomes de método sem acentos nem espaços: o dexer com minSdk 26 recusa espaços.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val bdTeste = "teste-backup.db"

    private lateinit var db: PreditDatabase
    private lateinit var pasta: File
    private lateinit var manager: BackupManager

    @Before
    fun preparar() {
        limparRestauroPendente()
        context.deleteDatabase(bdTeste)
        db = Room.databaseBuilder(context, PreditDatabase::class.java, bdTeste).build()
        pasta = File(context.cacheDir, "backups-teste").apply {
            deleteRecursively()
            mkdirs()
        }
        manager = BackupManager(context, db, pasta)
    }

    @After
    fun limpar() {
        // A flag de restauro é estado real da app: se ficasse ligada, o próximo arranque
        // substituía a base de dados do utilizador pelo pendente deste teste.
        limparRestauroPendente()
        db.close()
        context.deleteDatabase(bdTeste)
        pasta.deleteRecursively()
    }

    @Test
    fun T1_criarDevolveCopiaComTamanhoEVersaoDaBd() {
        val info = manager.criar()

        assertTrue("o ficheiro tem de existir no disco", File(info.caminho).exists())
        assertTrue("tamanho > 0 (foi ${info.tamanhoBytes})", info.tamanhoBytes > 0L)
        assertEquals(PreditDatabase.VERSAO_BD, info.versionBd)
        assertFalse("um criar() sem argumento é manual", info.ehAutomatico)
        assertTrue("nome no formato da casa", info.nome.startsWith("predit-"))
        assertTrue(info.nome.endsWith(".db"))
    }

    @Test
    fun T2_listarDevolveTodasAsCopiasDaMaisRecenteParaATras() {
        val criadas = (1..3).map {
            Thread.sleep(20)   // separa os carimbos de tempo: a ordem depende deles
            manager.criar(ehAutomatico = it == 3)
        }

        val listadas = manager.listar()

        assertEquals("uma entrada por criar()", criadas.size, listadas.size)
        assertEquals(
            "a mais recente primeiro",
            criadas.reversed().map { it.nome },
            listadas.map { it.nome }
        )
        assertTrue("a última criada é a automática", listadas.first().ehAutomatico)
        assertTrue(
            "criadoEm por ordem decrescente",
            listadas.zipWithNext().all { (a, b) -> a.criadoEm >= b.criadoEm }
        )
    }

    @Test
    fun T3_apagarTiraOFicheiroEDaLista() {
        val info = manager.criar()
        assertTrue(File(info.caminho).exists())

        manager.apagar(info)

        assertFalse("sai do disco", File(info.caminho).exists())
        assertTrue("sai da lista", manager.listar().isEmpty())
    }

    @Test
    fun T4_limparAntigosCortaOsAutomaticosEMantemOsManuais() {
        repeat(5) {
            Thread.sleep(20)
            manager.criar(ehAutomatico = true)
        }
        Thread.sleep(20)
        val manual = manager.criar()

        manager.limparAntigos(manterUltimos = 3)

        val listadas = manager.listar()
        assertEquals("3 automáticos + o manual", 4, listadas.size)
        assertEquals(3, listadas.count { it.ehAutomatico })
        assertTrue("o manual nunca é cortado", listadas.any { it.nome == manual.nome })
    }

    @Test
    fun T5_prepararRestauroDeixaOCopiaPendenteEALinhaDeEspera() {
        val info = manager.criar()
        assertFalse("sem restauro marcado no início", manager.temRestauroPendente())

        runBlocking { manager.prepararRestauro(info) }

        val pendente = File(context.filesDir, BackupManager.NOME_PENDENTE)
        assertTrue("o ficheiro pendente tem de estar no filesDir", pendente.exists())
        assertEquals(File(info.caminho).length(), pendente.length())
        assertTrue("a flag fica ligada", manager.temRestauroPendente())
        assertTrue(
            context.getSharedPreferences(BackupManager.PREFS, Context.MODE_PRIVATE)
                .getBoolean(BackupManager.CHAVE_RESTAURO_PENDENTE, false)
        )
    }

    private fun limparRestauroPendente() {
        File(context.filesDir, BackupManager.NOME_PENDENTE).delete()
        context.getSharedPreferences(BackupManager.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BackupManager.CHAVE_RESTAURO_PENDENTE, false)
            .commit()
    }
}
