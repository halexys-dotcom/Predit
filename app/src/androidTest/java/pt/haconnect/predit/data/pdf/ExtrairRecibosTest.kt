package pt.haconnect.predit.data.pdf

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import pt.haconnect.predit.domain.importacao.ReciboImportado
import pt.haconnect.predit.domain.importacao.parseReciboPdf
import java.io.File

/**
 * Extrai os recibos de origem para fixtures do parser (Fase 27, Bloco 0).
 *
 * **Teste de bancada, não corre na CI**: está `@Ignore` porque os PDFs são dados pessoais
 * e não estão no repositório — cada um copia os seus para
 * `app/src/androidTest/assets/recibos-origem/` (pasta no .gitignore) e corre o teste à
 * mão.
 *
 * Para correr:
 * ```
 * adb shell am instrument -w -e class pt.haconnect.predit.data.pdf.ExtrairRecibosTest \
 *     -e notAnnotation org.junit.Ignore pt.haconnect.predit.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 * (ou retirar o `@Ignore` e correr `connectedGithubDebugAndroidTest`).
 *
 * O teste escreve, para cada PDF, dois ficheiros na pasta da app:
 *  - `<nome>-blocos.tsv` — página, x, y, texto por bloco, no formato que
 *    [pt.haconnect.predit.domain.importacao.ReciboPdfParser] consome e que os fixtures de
 *    `app/src/test/resources/fixtures/` têm;
 *  - `<nome>-texto.txt`  — o texto corrido, para conferir à vista.
 *
 * Trazê-los para o repositório:
 * ```
 * adb pull /sdcard/Android/data/pt.haconnect.predit.debug/files/recibos-fixtures temp-recibos
 * ```
 * Depois copiar cada `<nome>-blocos.tsv` para
 * `app/src/test/resources/fixtures/recibo-<mes>-<ano>.txt`.
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Teste de bancada: precisa dos PDFs em androidTest/assets/recibos-origem (dados pessoais)")
class ExtrairRecibosTest {

    @Test
    fun extrairRecibosParaFixtures() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val pasta = File(contexto.getExternalFilesDir(null), "recibos-fixtures")
        pasta.mkdirs()

        val assets = contexto.assets.list("recibos-origem").orEmpty().sorted()
        check(assets.isNotEmpty()) {
            "sem PDFs em androidTest/assets/recibos-origem — copiar os recibos antes de correr"
        }

        for (nome in assets) {
            val blocos = contexto.assets.open("recibos-origem/$nome").use { extrairBlocosDe(it) }
            val texto = contexto.assets.open("recibos-origem/$nome").use { extrairTextoDe(it) }
            val base = nome.substringBeforeLast('.')

            val tsv = blocos.joinToString("\n") { "${it.pagina}\t${it.x}\t${it.y}\t${it.texto}" }
            File(pasta, "$base-blocos.tsv").writeText(tsv, Charsets.UTF_8)
            File(pasta, "$base-texto.txt").writeText(texto, Charsets.UTF_8)

            val recibos: List<ReciboImportado> = parseReciboPdf(blocos)
            val resumo = resumoDe(nome, texto, recibos)
            Log.i(TAG, resumo)
            println(resumo)
        }

        Log.i(TAG, "fixtures em ${pasta.absolutePath}")
        println("fixtures em ${pasta.absolutePath}")
    }

    /** Resumo legível: o que o parser tirou de cada página, para conferir de olho. */
    private fun resumoDe(nome: String, texto: String, recibos: List<ReciboImportado>): String =
        buildString {
            appendLine("==================== $nome ====================")
            appendLine("----- texto corrido -----")
            appendLine(texto)
            appendLine("----- recibos ----")
            for (r in recibos) {
                appendLine(
                    "pagina=${r.pagina} mes=${r.mesReferencia} fecho=${r.dataFecho} " +
                        "base=${r.vencimentoBase} hora=${r.vencimentoHora} " +
                        "dias=${r.numDiasUteis} irsAno=${r.irsRetidoAno} " +
                        "abonos=${r.totalAbonos} descontos=${r.totalDescontos} liq=${r.liquido}"
                )
                for (l in r.linhas) {
                    appendLine(
                        "   ${l.ordem}. ${l.codigoRecibo} -> ${l.codigoInterno} " +
                            "(${l.natureza}) valor=${l.valor} qtd=${l.quantidade} " +
                            "data=${l.data} :: ${l.descricao}${l.aviso?.let { "  [aviso: $it]" } ?: ""}"
                    )
                }
                if (r.avisos.isNotEmpty()) appendLine("   avisos: ${r.avisos}")
            }
        }

    private companion object {
        const val TAG = "ExtrairRecibos"
    }
}
