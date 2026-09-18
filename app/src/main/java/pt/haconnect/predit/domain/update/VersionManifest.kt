package pt.haconnect.predit.domain.update

/**
 * Conteudo do `version.json` anexado a cada GitHub Release (Fase 11d.2).
 *
 * Este ficheiro e a unica fonte de verdade sobre a ultima versao publicada: a app
 * compara o [versionCode] com o seu e decide se ha algo a instalar.
 */
data class VersionManifest(
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val changelog: String
)
