package pt.haconnect.predit.domain.update

/** Resultado de comparar a versao instalada com o manifesto publicado. */
sealed class ResultadoVerificacao {
    data object SemAtualizacao : ResultadoVerificacao()

    data class AtualizacaoDisponivel(
        val manifest: VersionManifest,
        val versaoAtual: Int
    ) : ResultadoVerificacao()

    data class Erro(val mensagem: String) : ResultadoVerificacao()
}

/**
 * A release so interessa se for mais recente que o build instalado.
 *
 * Um build de desenvolvimento mais novo que a release (versionCode local maior) nao
 * gera atualizacao: nao se desce de versao.
 */
fun compararVersoes(localVersionCode: Int, manifest: VersionManifest): ResultadoVerificacao {
    return when {
        manifest.versionCode > localVersionCode ->
            ResultadoVerificacao.AtualizacaoDisponivel(manifest, localVersionCode)
        else -> ResultadoVerificacao.SemAtualizacao
    }
}
