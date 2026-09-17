package pt.haconnect.predit.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.haconnect.predit.data.local.PreditDatabase
import pt.haconnect.predit.domain.model.BackupInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nome do ficheiro da BD, o mesmo que o Room abre em PreditApplication. */
private const val NOME_BD = "predit.db"

/** Pasta, dentro do diretório da app, onde ficam as cópias. */
private const val PASTA_PADRAO = "backups"

private const val PREFIXO = "predit-"
private const val MARCA_AUTOMATICO = "-auto"
private const val EXTENSAO = ".db"

/** Primeiros 16 bytes de qualquer ficheiro SQLite: serve para recusar lixo no restauro. */
private const val CABECALHO_SQLITE = "SQLite format 3\u0000"

private const val TAG = "Predit"

/**
 * Backups internos da base de dados (Fase 11b).
 *
 * Copia a BD com `VACUUM INTO`, que escreve **um** ficheiro consistente mesmo com o WAL
 * aberto — ao contrário de copiar o `predit.db` à mão, que perde o que ainda está no WAL.
 * Onde o SQLite é antigo (Android 8, sem `VACUUM INTO`) faz o checkpoint do WAL e copia.
 *
 * Os ficheiros vivem em `Android/data/<pacote>/files/backups/`: fora do diretório privado
 * (o utilizador vê-os, e o `connectedAndroidTest` leva-os com a app quando desinstala).
 * Isto protege contra erros dentro da app — edições erradas, uma importação má, um restauro
 * arrependido. Não protege contra desinstalar, limpar dados, nem perder o telemóvel: para
 * isso existem as cópias de fora, em tools/db-backups.
 */
class BackupManager(
    private val context: Context,
    private val database: PreditDatabase,
    /**
     * Pasta alternativa, só para os testes: sem ela o `listar()` misturava os backups do
     * utilizador com os do teste e as contagens deixavam de ser determinísticas.
     */
    private val pastaInjectada: File? = null
) {

    val pastaBackups: File
        get() = (pastaInjectada ?: File(context.getExternalFilesDir(null) ?: context.filesDir, PASTA_PADRAO))
            .apply { mkdirs() }

    /** Cópias existentes, da mais recente para a mais antiga. */
    fun listar(): List<BackupInfo> = (pastaBackups.listFiles() ?: emptyArray())
        .filter { it.isFile && it.name.startsWith(PREFIXO) && it.name.endsWith(EXTENSAO) }
        .map { paraInfo(it) }
        .sortedByDescending { it.criadoEm }

    /** Cria uma cópia da BD atual e devolve-a. */
    fun criar(ehAutomatico: Boolean = false): BackupInfo {
        val pasta = pastaBackups
        val destino = File(pasta, nomeLivre(pasta, ehAutomatico))
        val db = database.openHelper.writableDatabase

        // 1) VACUUM INTO com o caminho ligado — SQLite 3.27+ (Android 10 em diante).
        if (tentarVacuum(db, "VACUUM INTO ?", arrayOf<Any?>(destino.absolutePath), destino)) {
            return paraInfo(destino)
        }
        // 2) O mesmo com o caminho embutido no SQL: há versões que recusam o parâmetro ligado.
        val literal = destino.absolutePath.replace("'", "''")
        if (tentarVacuum(db, "VACUUM INTO '$literal'", null, destino)) {
            return paraInfo(destino)
        }
        // 3) Sem VACUUM INTO (Android 8/SQLite 3.18): WAL para dentro do .db e cópia simples.
        destino.delete()
        db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        File(db.path ?: context.getDatabasePath(NOME_BD).absolutePath).copyTo(destino, overwrite = true)
        check(destino.length() > 0L) { "cópia vazia: ${destino.name}" }
        Log.i(TAG, "backup por checkpoint + cópia do .db (sem VACUUM INTO): ${destino.name}")
        return paraInfo(destino)
    }

    /** Remove o ficheiro da cópia do disco. Não falha se ele já não existir. */
    fun apagar(backup: BackupInfo) {
        val ficheiro = File(backup.caminho)
        if (ficheiro.exists() && !ficheiro.delete()) {
            Log.w(TAG, "não consegui apagar ${backup.nome}")
        }
        // O VACUUM INTO escreve um ficheiro só, mas a via do Android 8 pode deixar companhia.
        File(backup.caminho + "-wal").delete()
        File(backup.caminho + "-shm").delete()
    }

    /**
     * Corta os backups automáticos mais antigos, deixando [manterUltimos]. Os manuais são do
     * utilizador e ficam sempre: como o auto-backup corre a cada arranque, cortar por data
     * entre todos levava um backup manual à frente do primeiro dia de uso intenso.
     */
    fun limparAntigos(manterUltimos: Int = 10) {
        listar()
            .filter { it.ehAutomatico }
            .drop(manterUltimos)
            .forEach { apagar(it) }
    }

    /** Deixa a cópia escolhida pronta a entrar no próximo arranque. */
    suspend fun prepararRestauro(backup: BackupInfo) {
        withContext(Dispatchers.IO) {
            val origem = File(backup.caminho)
            require(origem.exists()) { "backup desapareceu: ${backup.nome}" }
            origem.copyTo(File(context.filesDir, NOME_PENDENTE), overwrite = true)
            prefs(context).edit().putBoolean(CHAVE_RESTAURO_PENDENTE, true).apply()
        }
    }

    /** Há um restauro marcado à espera do próximo arranque? */
    fun temRestauroPendente(): Boolean =
        File(context.filesDir, NOME_PENDENTE).exists() &&
            prefs(context).getBoolean(CHAVE_RESTAURO_PENDENTE, false)

    /** Um VACUUM falhado pode deixar um ficheiro a meio: a tentativa seguinte apaga-o antes. */
    private fun tentarVacuum(
        db: SupportSQLiteDatabase,
        sql: String,
        argumentos: Array<Any?>?,
        destino: File
    ): Boolean {
        destino.delete()
        return try {
            if (argumentos == null) db.execSQL(sql) else db.execSQL(sql, argumentos)
            destino.length() > 0L
        } catch (e: Exception) {
            Log.w(TAG, "VACUUM INTO falhou (${e.message}); a tentar a via seguinte")
            destino.delete()
            false
        }
    }

    /**
     * Nome com data e hora. Se já existir um backup do mesmo segundo (o automático logo a
     * seguir a um manual, os testes em série) acrescenta um contador: o `VACUUM INTO` recusa
     * escrever por cima de um ficheiro existente.
     */
    private fun nomeLivre(pasta: File, ehAutomatico: Boolean): String {
        val base = PREFIXO + seloDeAgora() + if (ehAutomatico) MARCA_AUTOMATICO else ""
        var nome = base + EXTENSAO
        var contador = 1
        while (File(pasta, nome).exists()) {
            nome = "$base-$contador$EXTENSAO"
            contador++
        }
        return nome
    }

    private fun paraInfo(ficheiro: File): BackupInfo = BackupInfo(
        nome = ficheiro.name,
        caminho = ficheiro.absolutePath,
        tamanhoBytes = ficheiro.length(),
        criadoEm = ficheiro.lastModified(),
        versionBd = lerUserVersion(ficheiro),
        ehAutomatico = ficheiro.name.contains(MARCA_AUTOMATICO)
    )

    companion object {
        /** SharedPreferences onde vive a flag do restauro marcado. */
        const val PREFS = "predit"
        const val CHAVE_RESTAURO_PENDENTE = "restauro_pendente"
        const val NOME_PENDENTE = "predit-restore-pendente.db"

        /**
         * Aplica o restauro marcado, se houver. Corre no arranque do PreditApplication,
         * **antes** de o Room abrir a BD: depois disso já haveria ligações ao ficheiro antigo.
         */
        fun aplicarRestauroPendente(context: Context) {
            val prefs = prefs(context)
            if (!prefs.getBoolean(CHAVE_RESTAURO_PENDENTE, false)) return

            val pendente = File(context.filesDir, NOME_PENDENTE)
            if (!pendente.exists() || !temCabecalhoSqlite(pendente)) {
                // Sem ficheiro válido não há restauro. Acontece a sério: o Auto Backup copia os
                // shared prefs (a flag) e não este ficheiro, logo um device-transfer pode trazer
                // a flag ligada sem a cópia atrás dela.
                Log.w(TAG, "restauro pendente sem ficheiro válido — flag limpa")
                pendente.delete()
                prefs.edit().putBoolean(CHAVE_RESTAURO_PENDENTE, false).apply()
                return
            }

            val versao = lerUserVersion(pendente)
            if (versao > PreditDatabase.VERSAO_BD) {
                Log.w(TAG, "restauro recusado: cópia da versão $versao, app na ${PreditDatabase.VERSAO_BD}")
                pendente.delete()
                prefs.edit().putBoolean(CHAVE_RESTAURO_PENDENTE, false).apply()
                return
            }

            val dbPath = context.getDatabasePath(NOME_BD)
            dbPath.parentFile?.mkdirs()
            // O -wal/-shm/-journal antigos descrevem a BD que vai sair: têm de sair primeiro.
            apagarFicheiro(dbPath)
            apagarFicheiro(File(dbPath.absolutePath + "-wal"))
            apagarFicheiro(File(dbPath.absolutePath + "-shm"))
            apagarFicheiro(File(dbPath.absolutePath + "-journal"))

            pendente.copyTo(dbPath, overwrite = true)
            pendente.delete()
            prefs.edit().putBoolean(CHAVE_RESTAURO_PENDENTE, false).apply()
            Log.i(TAG, "restauro aplicado (v$versao): ${dbPath.absolutePath}")
        }
    }
}

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(BackupManager.PREFS, Context.MODE_PRIVATE)

private fun apagarFicheiro(file: File) {
    if (file.exists() && !file.delete()) Log.w(TAG, "não consegui apagar ${file.absolutePath}")
}

private fun temCabecalhoSqlite(file: File): Boolean = try {
    val cabecalho = ByteArray(16)
    val lidos = file.inputStream().use { it.read(cabecalho) }
    lidos == 16 && String(cabecalho, Charsets.US_ASCII) == CABECALHO_SQLITE
} catch (_: Exception) {
    false
}

/**
 * `user_version` do ficheiro, ou -1 se não for legível. A variante com `File` só existe a
 * partir do API 27 e o minSdk é 26, por isso fica a de String — depreciada, mas funcional.
 */
@Suppress("DEPRECATION")
private fun lerUserVersion(file: File): Int = try {
    SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else -1
        }
    }
} catch (e: Exception) {
    Log.w(TAG, "não consegui ler o user_version de ${file.name}", e)
    -1
}

/** 20260917-140523, sempre em Locale.US para os nomes não mudarem com a região do telefone. */
private fun seloDeAgora(): String =
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

