package pt.haconnect.predit.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Descarrega o APK da nova versao com o DownloadManager do sistema (Fase 11d.2).
 *
 * O DownloadManager corre noutro processo e nao escreve dentro da cache interna da app,
 * por isso o destino e o armazenamento externo proprio (`files/Download/`). No fim o
 * ficheiro e copiado para a cache, que e onde o FileProvider o sabe entregar ao instalador.
 */
class ApkDownloader(private val context: Context) {

    private val pastaCache: File get() = File(context.cacheDir, "updates")
    private val ficheiroCache: File get() = File(pastaCache, NOME_APK)
    private val ficheiroExterno: File?
        get() = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, NOME_APK) }

    fun descarregar(apkUrl: String): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        pastaCache.mkdirs()
        if (ficheiroCache.exists()) ficheiroCache.delete()
        ficheiroExterno?.takeIf { it.exists() }?.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Predit")
            .setDescription("A descarregar atualização")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        if (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) != null) {
            request.setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, NOME_APK
            )
        } else {
            // Sem armazenamento externo: ultimo recurso (no Android 10+ pode ser recusado).
            request.setDestinationUri(Uri.fromFile(ficheiroCache))
        }

        return dm.enqueue(request)
    }

    fun observar(id: Long): Flow<EstadoDownload> = callbackFlow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val idRecebido = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (idRecebido != id) return

                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                try {
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> trySend(EstadoDownload.Concluido)
                            DownloadManager.STATUS_FAILED -> trySend(EstadoDownload.Falhou)
                        }
                    }
                } finally {
                    cursor.close()
                }
                close()
            }
        }

        // Com targetSdk 34+ um receiver de runtime tem de dizer se e exportado; o
        // ACTION_DOWNLOAD_COMPLETE vem do sistema, logo nada aqui e para outras apps.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val progresso = launch {
            while (isActive) {
                delay(INTERVALO_PROGRESSO_MS)
                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                try {
                    if (cursor.moveToFirst()) {
                        val baixado = cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                            )
                        )
                        val total = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        if (total > 0) trySend(EstadoDownload.Progresso(baixado, total))
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // ja desregistado (processo a terminar): nada a fazer.
            }
            progresso.cancel()
        }
    }

    /** Passa o APK para a cache interna; devolve `null` se o download nao deixou nada. */
    fun prepararApkParaInstalar(): File? {
        val destino = ficheiroCache
        pastaCache.mkdirs()
        return try {
            val externo = ficheiroExterno
            when {
                externo != null && externo.exists() -> {
                    externo.copyTo(destino, overwrite = true)
                    externo.delete()
                    destino.takeIf { it.length() > 0 }
                }
                destino.exists() && destino.length() > 0 -> destino
                else -> null
            }
        } catch (e: Exception) {
            if (destino.exists()) destino.delete()
            null
        }
    }

    fun caminhoApk(): File = ficheiroCache

    private companion object {
        const val NOME_APK = "predit-update.apk"
        const val INTERVALO_PROGRESSO_MS = 500L
    }
}

sealed class EstadoDownload {
    data class Progresso(val baixado: Long, val total: Long) : EstadoDownload()
    data object Concluido : EstadoDownload()
    data object Falhou : EstadoDownload()
}
