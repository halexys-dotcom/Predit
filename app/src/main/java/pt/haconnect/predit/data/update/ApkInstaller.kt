package pt.haconnect.predit.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Entrega o APK ao instalador do sistema (Fase 11d.2).
 *
 * O `authority` e o mesmo que o manifesto declara (`${applicationId}.fileprovider`), por isso
 * usa-se `context.packageName`: resolve o caso de debug (`pt.haconnect.predit.debug`) e o de
 * release sem ter de saber qual esta a correr.
 */
object ApkInstaller {

    fun instalar(context: Context, apk: File): Boolean {
        if (!apk.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
