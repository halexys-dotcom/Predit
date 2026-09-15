package pt.haconnect.predit.data.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun extrairTextoPdf(uri: Uri, context: Context): String = withContext(Dispatchers.IO) {
    try {
        PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                stripper.getText(document)
            }
        } ?: ""
    } catch (_: Exception) {
        ""
    }
}
