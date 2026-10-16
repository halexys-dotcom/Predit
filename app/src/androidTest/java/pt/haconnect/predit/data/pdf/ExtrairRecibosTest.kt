package pt.haconnect.predit.data.pdf

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import pt.haconnect.predit.domain.importacao.ReciboImportado
import pt.haconnect.predit.domain.importacao.parseReciboPdf
import java.io.File

/**
 * Extrai os recibos de origem para fixtures do parser (Fase 27, Bloco 0).
 *
 * **Teste de bancada**: os PDFs são dados pessoais e não estão no repositório — cada um
 * copia os seus para `app/src/androidTest/assets/recibos-origem/` (pasta no .gitignore).
 * Sem PDFs o teste **salta** (`Assume.assumeTrue`), em vez de falhar: não parte a CI nem
 * numa máquina limpa, e não obriga a repor `@Ignore` à mão.
 *
 * Para correr, com os PDFs em `app/src/androidTest/assets/recibos-origem/`:
 * ```
 * .\gradlew.bat :app:connectedGithubDebugAndroidTest `
 *     "-Pandroid.testInstrumentationRunnerArguments.class=pt.haconnect.predit.data.pdf.ExtrairRecibosTest"
 * ```
 * (aspas no argumento `-P` para o PowerShell não o manglar. O `--tests` do Gradle só filtra
 * testes de JVM — para testes instrumentados é esta propriedade.)
 *
 * O teste escreve, para cada PDF, dois ficheiros na pasta da app:
 *  - `<nome>-blocos.tsv` — página, x, y, texto por bloco, no formato que
 *    [pt.haconnect.predit.domain.importacao.ReciboPdfParser] consome e que os fixtures de
 *    `app/src/test/resources/fixtures/` têm;
 *  - `<nome>-texto.txt`  — o texto corrido, para conferir à vista.
 *
 * No Android 11+ esses ficheiros **não** são recuperáveis: `adb pull` e `adb shell ls` são
 * bloqueados pelo scoped storage de `Android/data/<app>` e o `run-as` não tem o namespace
 * de storage. O AGP ainda desinstala a app no fim do run (usar
 * `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` para a manter). Por isso
 * os blocos vão também para o logcat ([TAG_BLOCOS]), que o AGP guarda em
 * `app/build/outputs/androidTest-results/connected/.../logcat-*.txt` — é o canal fiável.
 *
 * Depois copiar cada `<nome>-blocos.tsv` para
 * `app/src/test/resources/fixtures/recibo-<mes>-<ano>.txt`.
 */
@RunWith(AndroidJUnit4::class)
class ExtrairRecibosTest {

    @Test
    fun extrairRecibosParaFixtures() {
        val instrumentacao = InstrumentationRegistry.getInstrumentation()
        // Os PDFs de origem viajam no APK de TESTE (androidTest/assets), por isso leem-se do
        // contexto da instrumentacao. O `targetContext` e o da app e nao os tem — so serve
        // para escrever a saida em /sdcard/Android/data/<app>/files/recibos-fixtures.
        val contexto = instrumentacao.targetContext
        val assetsDeOrigem = instrumentacao.context.assets
        val assets = assetsDeOrigem.list("recibos-origem").orEmpty().sorted()
        // Sem PDFs (CI, maquina limpa, clone novo) o teste SALTA em vez de falhar — os PDFs
        // sao dados pessoais e nao estao no repositorio. Antes de mexer em pastas/recursos.
        Assume.assumeTrue(
            "sem PDFs em androidTest/assets/recibos-origem — copiar os recibos antes de correr",
            assets.isNotEmpty()
        )
        // O PDFBox so encontra os recursos dele (glyphlist, cmaps...) se estes forem
        // carregados do APK da app antes da extracao. Em producao isso e feito pelos
        // involucros `extrairTextoPdf`/`extrairBlocosPdf`; aqui usam-se as funcoes cruas
        // `extrair*De`, portanto inicializa-se a mao. Tem de ser o contexto da APP.
        PDFBoxResourceLoader.init(contexto)
        val pasta = File(contexto.getExternalFilesDir(null), "recibos-fixtures")
        pasta.mkdirs()

        for (nome in assets) {
            val blocos = assetsDeOrigem.open("recibos-origem/$nome").use { extrairBlocosDe(it) }
            val texto = assetsDeOrigem.open("recibos-origem/$nome").use { extrairTextoDe(it) }
            val base = nome.substringBeforeLast('.')

            val tsv = blocos.joinToString("\n") { "${it.pagina}\t${it.x}\t${it.y}\t${it.texto}" }
            File(pasta, "$base-blocos.tsv").writeText(tsv, Charsets.UTF_8)
            File(pasta, "$base-texto.txt").writeText(texto, Charsets.UTF_8)
            // DIAGNOSTICO: e o unico canal para inspecionar os blocos em CI e no aparelho,
            // ja que os ficheiros de Android/data/<app> nao sao alcancaveis por `adb pull`
            // nem por `run-as` (Android 11+). Fica no logcat, guardado pelo AGP em
            // app/build/outputs/androidTest-results/.../logcat-*.txt.
            Log.i(TAG_BLOCOS, tsv)

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
        const val TAG_BLOCOS = "ExtrairRecibosTSV"
    }
}
