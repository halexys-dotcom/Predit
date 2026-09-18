package pt.haconnect.predit.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comparacao da versao instalada com o manifesto publicado (Fase 11d.2). */
class VersionComparatorTest {

    private fun manifesto(codigo: Int) = VersionManifest(
        versionCode = codigo,
        versionName = "0.12.0",
        minSdk = 26,
        apkUrl = "https://exemplo.invalid/app-release.apk",
        changelog = "- novidade"
    )

    @Test
    fun `T1 - manifesto igual a versao local nao e atualizacao`() {
        assertEquals(ResultadoVerificacao.SemAtualizacao, compararVersoes(14, manifesto(14)))
    }

    @Test
    fun `T2 - manifesto mais recente da atualizacao disponivel`() {
        val resultado = compararVersoes(14, manifesto(15))

        assertTrue(resultado is ResultadoVerificacao.AtualizacaoDisponivel)
        val atualizacao = resultado as ResultadoVerificacao.AtualizacaoDisponivel
        assertEquals(15, atualizacao.manifest.versionCode)
        assertEquals("0.12.0", atualizacao.manifest.versionName)
        assertEquals(14, atualizacao.versaoAtual)
    }

    @Test
    fun `T3 - build local mais recente que a release nao e atualizacao`() {
        assertEquals(ResultadoVerificacao.SemAtualizacao, compararVersoes(15, manifesto(14)))
    }
}
