package pt.haconnect.predit.data.local

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.haconnect.predit.data.repository.ReciboRepository
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.model.ReciboLinha
import pt.haconnect.predit.domain.model.ReciboMes
import java.time.LocalDate

/**
 * Recibo introduzido (Fase 8.2b.2a): cabeçalho, linhas, chave composta, CASCADE e RESTRICT.
 *
 * A base de dados é em memória, com as foreign keys LIGADAS pelo mesmo pragma que a app
 * usa — o Room 2.6.1 não as impõe sozinho. Sem isso o T3 (CASCADE) e o T4 (RESTRICT)
 * passavam sem provar nada, por isso o T4 confirma primeiro que o pragma está ligado.
 */
@RunWith(AndroidJUnit4::class)
class ReciboDaoTest {

    private lateinit var db: PreditDatabase
    private lateinit var repo: ReciboRepository

    private val mes = "2026-08"

    @Before
    fun abrirBaseDeDados() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(contexto, PreditDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
        repo = ReciboRepository(db)
    }

    @After
    fun fecharBaseDeDados() {
        db.close()
    }

    @Test
    fun T1_reciboDeAgostoComVinteLinhasLidoDeVolta() = runBlocking {
        val ids = semearCatalogo()
        val linhas = linhasDoCatalogo(ids)

        repo.guardar(reciboDeAgosto(), linhas)

        // Cabeçalho: o mesmo objecto de domínio, pela ponte do repositório...
        assertEquals(reciboDeAgosto(), repo.observarMes(mes).first())
        // ...e os valores como estão mesmo gravados, pelo DAO (sem conversão)
        val gravado = db.reciboMesDao().obterPorMes(mes)
        assertNotNull(gravado)
        assertEquals("2026-08", gravado!!.anoMes)
        assertEquals(11_379_800L, gravado.vencimentoBase)
        assertEquals(65_653L, gravado.vencimentoHora)
        assertEquals(1_935_423L, gravado.totalDescontos)
        // Linhas, pela ponte do domínio
        val lidas = repo.observarLinhas(mes).first()
        assertEquals(20, lidas.size)
        assertEquals(linhas, lidas)

        // Os 20 valores, um a um (1,00 €, 2,00 €, ... 20,00 €)
        assertEquals(10_000L, lidas.first().valorEstimado)
        assertEquals(10_000L, lidas.first().valorReal)
        assertEquals(200_000L, lidas.last().valorEstimado)
        // As três de desconto no fim do catálogo, e a de abono depois delas
        assertEquals(NaturezaRubrica.DESCONTO, lidas[16].natureza)   // D01
        assertEquals(NaturezaRubrica.DESCONTO, lidas[17].natureza)   // D02
        assertEquals(NaturezaRubrica.DESCONTO, lidas[18].natureza)   // D04
        assertEquals(NaturezaRubrica.ABONO, lidas[19].natureza)      // OUTROS
    }

    @Test
    fun T2_gravarOMesmoMesOutraVezSubstituiAsLinhas() = runBlocking {
        val ids = semearCatalogo()

        repo.guardar(reciboDeAgosto(), linhasDoCatalogo(ids, diferenca = 0L))
        repo.guardar(reciboDeAgosto().copy(nota = "corrigido"), linhasDoCatalogo(ids, diferenca = 500L))

        val lidas = repo.observarLinhas(mes).first()
        assertEquals(20, lidas.size)                                  // não duplicou
        assertEquals(linhasDoCatalogo(ids, diferenca = 500L), lidas)  // substituiu
        assertEquals(1, repo.observarTodosMeses().first().size)        // um só cabeçalho
        assertEquals("corrigido", repo.observarMes(mes).first()?.nota)
    }

    @Test
    fun T3_apagarOMesFazCascadeNasLinhas() = runBlocking {
        val ids = semearCatalogo()
        repo.guardar(reciboDeAgosto(), linhasDoCatalogo(ids))
        assertEquals(20, repo.observarLinhas(mes).first().size)

        repo.apagar(mes)

        assertNull(repo.observarMes(mes).first())
        assertTrue(repo.observarLinhas(mes).first().isEmpty())
        assertEquals(0, db.reciboLinhaDao().obterPorMes(mes).size)
    }

    @Test
    fun T4_apagarRubricaUsadaNumaLinhaFalha() = runBlocking {
        assertTrue(
            "o PRAGMA foreign_keys tem de estar ligado, senão este teste não prova nada",
            foreignKeysLigadas()
        )

        val ids = semearCatalogo()
        repo.guardar(reciboDeAgosto(), linhasDoCatalogo(ids))

        // Controlo: uma rubrica sem linhas apaga-se. Prova que o erro abaixo vem do FK
        // RESTRICT, e não de um DELETE mal feito.
        val livre = db.rubricaDao().inserir(
            RubricaEntity(0L, "R99", "Rubrica livre", true, true, true, TipoCalculo.FIXO, false, 98)
        )
        db.rubricaDao().apagar(livre)
        assertNull(db.rubricaDao().obterPorCodigo("R99"))

        // A que está usada numa linha já não sai
        val erro = try {
            db.rubricaDao().apagar(ids.first())          // VENC
            null
        } catch (e: SQLiteException) {
            e
        }
        assertNotNull("apagar o VENC, usado numa linha, tem de falhar", erro)
        assertTrue(
            "tem de ser violação de constraint (RESTRICT), e não $erro",
            erro is SQLiteConstraintException
        )
        assertNotNull("a rubrica tem de continuar na tabela", db.rubricaDao().obterPorCodigo("VENC"))
        assertEquals(20, repo.observarLinhas(mes).first().size)
    }

    @Test
    fun T5_observarMesSemReciboDevolveNull() = runBlocking {
        assertNull(repo.observarMes("2026-08").first())
        assertTrue(repo.observarTodosMeses().first().isEmpty())
    }

    // --- helpers -------------------------------------------------------------

    /** O pragma é por ligação: sem ele ligado o CASCADE e o RESTRICT não acontecem. */
    private fun foreignKeysLigadas(): Boolean =
        db.openHelper.writableDatabase.query("PRAGMA foreign_keys").use { c ->
            c.moveToFirst() && c.getInt(0) == 1
        }

    /** Os 20 códigos do catálogo semeado, pela ordem do catálogo. */
    private val codigos = listOf(
        "VENC", "HNOT", "HSUP_DN", "HSUP_NT", "HSUP_DN_FER", "HSUP_NT_FER",
        "HSUP_DN_DESC", "HSUP_NT_DESC", "SUP_ALIM", "SUP_TRAN", "DESC_FER", "DESC_DESC",
        "ACR_NOT_FER", "ACR_NOT_DESC", "FERIAS", "NATAL", "D01", "D02", "D04", "OUTROS"
    )

    private val codigosDeDesconto = setOf("D01", "D02", "D04")

    /** D01/D02/D04 são descontos; o resto é abono — igual ao estimador. */
    private fun naturezaDe(codigo: String): NaturezaRubrica =
        if (codigo in codigosDeDesconto) NaturezaRubrica.DESCONTO else NaturezaRubrica.ABONO

    private fun tipoDe(codigo: String): String = when (codigo) {
        in codigosDeDesconto -> TipoCalculo.DERIVADO
        "OUTROS" -> TipoCalculo.MANUAL
        "VENC", "SUP_ALIM", "SUP_TRAN", "FERIAS", "NATAL" -> TipoCalculo.FIXO
        else -> TipoCalculo.HORAS
    }

    /** Insere o catálogo e devolve os ids gerados, pela ordem do catálogo. */
    private suspend fun semearCatalogo(): List<Long> = codigos.mapIndexed { indice, codigo ->
        db.rubricaDao().inserir(
            RubricaEntity(
                id = 0L,
                codigo = codigo,
                nome = codigo,
                incideSS = true,
                incideIRS = true,
                incideSindicato = true,
                tipoCalculo = tipoDe(codigo),
                ativaConferencia = true,
                ordem = indice + 1
            )
        )
    }

    /**
     * Uma linha por rubrica (1,00 €, 2,00 €, ...), com [diferenca] unidades a separar o
     * estimado do real. Com diferenca = 0, as 20 linhas ficam estimado == real.
     */
    private fun linhasDoCatalogo(ids: List<Long>, diferenca: Long = 0L): List<ReciboLinha> =
        codigos.mapIndexed { indice, codigo ->
            val estimado = (indice + 1) * 10_000L
            ReciboLinha(
                reciboMesId = mes,
                rubricaId = ids[indice],
                valorEstimado = estimado,
                valorReal = estimado + diferenca,
                natureza = naturezaDe(codigo),
                ordem = indice + 1
            )
        }

    private fun reciboDeAgosto(): ReciboMes = ReciboMes(
        anoMes = mes,
        dataFecho = LocalDate.of(2026, 8, 31).toEpochDay(),
        vencimentoBase = 11_379_800L,       // 1 137,98 €
        vencimentoHora = 65_653L,           // 6,5653 €/h
        numDiasUteis = 21,
        irsRetidoAno = 8_400_000L,          // 840,00 € acumulados no ano
        totalAbonos = 13_627_700L,          // 1 362,77 €
        totalDescontos = 1_935_423L,        // 193,54 €
        liquido = 11_692_277L,              // 1 169,23 €
        nota = "Recibo de agosto de 2026",
        dataCriacao = 1_767_000_000_000L
    )
}
