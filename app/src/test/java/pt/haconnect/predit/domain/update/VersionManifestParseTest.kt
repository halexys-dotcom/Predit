package pt.haconnect.predit.domain.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.data.update.manifestoDeJson

/** Leitura do version.json publicado (Fase 11d.2). */
class VersionManifestParseTest {

    @Test
    fun `T4 - manifesto completo le todos os campos`() {
        val json = JSONObject(
            """
            {
              "versionCode": 15,
              "versionName": "0.12.0",
              "minSdk": 26,
              "apkUrl": "https://github.com/halexys-dotcom/Predit/releases/download/v0.12.0/app-release.apk",
              "changelog": "- correcao de contagem\n- feriados municipais"
            }
            """.trimIndent()
        )

        val manifest = manifestoDeJson(json)

        assertEquals(15, manifest.versionCode)
        assertEquals("0.12.0", manifest.versionName)
        assertEquals(26, manifest.minSdk)
        assertEquals(
            "https://github.com/halexys-dotcom/Predit/releases/download/v0.12.0/app-release.apk",
            manifest.apkUrl
        )
        assertEquals("- correcao de contagem\n- feriados municipais", manifest.changelog)
    }

    @Test
    fun `T5 - changelog ausente nao rebenta e fica vazio`() {
        val texto = """{"versionCode":15,"versionName":"0.12.0","minSdk":26,"apkUrl":"https://x/app.apk"}"""

        val manifest = manifestoDeJson(texto)

        assertEquals("", manifest.changelog)
        assertEquals(15, manifest.versionCode)
        assertEquals("0.12.0", manifest.versionName)
    }
}
