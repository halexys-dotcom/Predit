package pt.haconnect.predit.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pt.haconnect.predit.domain.update.VersionManifest
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vai buscar o `version.json` do ultimo GitHub Release (Fase 11d.2).
 *
 * Sem OkHttp, Retrofit, Gson ou Moshi: `HttpURLConnection` e `org.json` sao nativos.
 */
class UpdateRepository(
    private val urlManifesto: String = URL_MANIFESTO
) {

    suspend fun obterManifesto(): Result<VersionManifest> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(urlManifesto).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                // O github.com responde com um 302 para outro host: sem isto ficavamos com o HTML do redirect.
                instanceFollowRedirects = true
            }
            try {
                val codigo = conn.responseCode
                if (codigo == HTTP_NAO_ENCONTRADO) {
                    return@withContext Result.failure(
                        NoSuchElementException("Sem release publicada")
                    )
                }
                if (codigo !in 200..299) {
                    return@withContext Result.failure(RuntimeException("HTTP $codigo"))
                }
                val texto = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(manifestoDeJson(texto))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /** Onde a app consulta a ultima versao publicada. */
        const val URL_MANIFESTO =
            "https://github.com/halexys-dotcom/Predit/releases/latest/download/version.json"

        private const val TIMEOUT_MS = 10_000
        private const val HTTP_NAO_ENCONTRADO = 404
    }
}

/**
 * Le o manifesto. Esta separado do HTTP para poder ser testado sem rede (T4/T5).
 */
internal fun manifestoDeJson(texto: String): VersionManifest = manifestoDeJson(JSONObject(texto))

internal fun manifestoDeJson(json: JSONObject): VersionManifest = VersionManifest(
    versionCode = json.getInt("versionCode"),
    versionName = json.getString("versionName"),
    minSdk = json.getInt("minSdk"),
    apkUrl = json.getString("apkUrl"),
    changelog = json.optString("changelog", "")
)
