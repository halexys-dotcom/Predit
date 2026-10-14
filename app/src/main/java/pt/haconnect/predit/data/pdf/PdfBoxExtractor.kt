package pt.haconnect.predit.data.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.haconnect.predit.domain.importacao.BlocoTexto
import java.io.InputStream

/**
 * Texto corrido do PDF — uma linha por linha visual. É o que o parser dos planeamentos
 * consome (ver domain/importacao/PdfPlannerParser.kt).
 *
 * Devolve "" em caso de erro: um PDF estranho nunca pode rebentar a importação; quem
 * chama decide o que fazer com o vazio.
 */
fun extrairTextoDe(entrada: InputStream): String =
    PDDocument.load(entrada).use { documento -> PDFTextStripper().getText(documento) }

/**
 * O mesmo PDF, mas com a posição de cada pedaço de texto (ver [BlocoTexto]).
 *
 * Percorre as páginas uma a uma: um recibo pode ocupar duas páginas (é o caso do recibo
 * de Julho, que traz Julho e Março), e os blocos das duas páginas não podem ser
 * misturados — as duas trazem o mesmo cabeçalho, em cima uma da outra.
 *
 * É o que o parser de recibos consome (ver domain/importacao/ReciboPdfParser.kt).
 */
fun extrairBlocosDe(entrada: InputStream): List<BlocoTexto> {
    PDDocument.load(entrada).use { documento ->
        val blocos = mutableListOf<BlocoTexto>()
        for (pagina in 1..documento.numberOfPages) {
            val leitor = LeitorComPosicoes(pagina, blocos)
            leitor.startPage = pagina
            leitor.endPage = pagina
            leitor.getText(documento)
        }
        return blocos
    }
}

/**
 * Distância, em pontos, a partir da qual dois pedaços de texto são blocos diferentes.
 * Um espaço normal num corpo de 7 pt mede ~2 pt; o intervalo entre colunas da grelha do
 * recibo mede dezenas de pontos. 3 pt separa os dois casos sem partir valores como
 * "5 840,20", que traz um espaço no meio mas de espaço normal.
 */
private const val LIMIAR_ESPACO_PT = 3.0f

/** Dois caracteres a menos de meio ponto de distância em y estão na mesma linha. */
private const val LIMIAR_LINHA_PT = 0.5f

/**
 * `PDFTextStripper` que, em vez de despejar linhas, entrega cada pedaço de texto com o
 * x/y onde foi desenhado.
 *
 * A classe base chama [writeString] uma vez por linha visual, com a lista de
 * `TextPosition` (uma por carácter). Aqui o texto é reconstruído a partir dessas posições
 * — e não do parâmetro `text` — porque é nas posições que se vê o intervalo "a mais"
 * entre colunas, que é o que separa blocos.
 */
private class LeitorComPosicoes(
    private val pagina: Int,
    private val destino: MutableList<BlocoTexto>
) : PDFTextStripper() {

    override fun writeString(text: String, posicoes: List<TextPosition>) {
        if (posicoes.isEmpty()) return

        val acumulado = StringBuilder()
        var xInicio = posicoes.first().xDirAdj
        var xFim = xInicio
        var y = posicoes.first().yDirAdj

        fun fechar() {
            if (acumulado.isNotBlank()) {
                destino.add(BlocoTexto(pagina, xInicio, y, acumulado.toString()))
            }
            acumulado.setLength(0)
        }

        for (posicao in posicoes) {
            val x = posicao.xDirAdj
            val salto = x - xFim
            // Um salto para a frente abre bloco novo; um salto para trás também, porque
            // a grelha do cabeçalho do recibo é desenhada da direita para a esquerda (o
            // x recua entre valores). Um mudar de linha a meio da chamada idem.
            val blocoNovo = acumulado.isNotEmpty() &&
                (salto > LIMIAR_ESPACO_PT || salto < -0.1f ||
                    kotlin.math.abs(posicao.yDirAdj - y) > LIMIAR_LINHA_PT)

            if (blocoNovo) {
                fechar()
                xInicio = x
            }
            y = posicao.yDirAdj
            acumulado.append(posicao.unicode)
            xFim = x + posicao.widthDirAdj
        }
        fechar()
    }
}

/** Texto corrido de um PDF escolhido pelo utilizador. */
suspend fun extrairTextoPdf(uri: Uri, context: Context): String = withContext(Dispatchers.IO) {
    try {
        PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri)?.use { extrairTextoDe(it) } ?: ""
    } catch (_: Exception) {
        ""
    }
}

/** Blocos com posição de um PDF escolhido pelo utilizador. Vazio em caso de erro. */
suspend fun extrairBlocosPdf(uri: Uri, context: Context): List<BlocoTexto> =
    withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { extrairBlocosDe(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
