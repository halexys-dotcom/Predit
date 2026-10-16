package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.importacao.LinhaReciboImportada
import pt.haconnect.predit.domain.importacao.mapearCodigoRecibo
import pt.haconnect.predit.domain.model.Rubrica

/**
 * A aplicação de um recibo importado às edições do mês: dois códigos do PDF podem cair na mesma
 * rubrica do catálogo — `RCR` e `RCRA` são ambos o subsídio de alimentação em cartão (o segundo
 * é o acerto), `R51` e `R118` são ambos "Outros" — e o valor da rubrica tem de ser a **soma**
 * das linhas, não a última delas.
 *
 * Os valores são os dos recibos reais (ver ReciboPdfParserTest e os fixtures): Março 2026 e
 * Agosto 2025. Este caminho — importar para as edições — não é exercitado pelo parser nem pelo
 * ReciboConferenciaTest (que escreve à mão), daí este teste.
 */
class ReciboImportacaoTest {

    private val catalogo = listOf(
        rubrica(1L, "VENC"),
        rubrica(9L, "SUP_ALIM"),
        rubrica(10L, "SUP_TRAN"),
        rubrica(99L, "OUTROS")
    ).associateBy { it.codigo }

    /**
     * Página 1 de Março 2026: `RCR` +172,70 € e logo abaixo `RCRA` −172,70 € (o acerto de
     * Março, mês de baixa). As duas linhas existem no PDF e anulam-se — o recibo declara 0,00
     * no subsídio de alimentação. Substituir a última linha dava −172,70 €.
     */
    @Test
    fun `linhas do mesmo codigo ICTS somam - Marco 2026 anula o subsidio`() {
        val naoReconhecidas = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("RCR", 1_727_000L),
                linha("RCRA", -1_727_000L)
            ),
            catalogo,
            naoReconhecidas
        )

        assertEquals(0L, somados[9L])
        assertTrue(naoReconhecidas.isEmpty())
    }

    /**
     * Agosto 2025 (Cegid): `RCR` 163,24 € e `RCRA` 7,42 € — aqui as duas somam-se, e os 170,66 €
     * são o que falta para o total de abonos desse recibo bater certo (1 508,57 €).
     */
    @Test
    fun `linhas do mesmo codigo ICTS somam - Agosto 2025 da os 170,66 do total de abonos`() {
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("RCR", 1_632_400L),
                linha("RCRA", 74_200L)
            ),
            catalogo,
            mutableListOf()
        )

        assertEquals(1_706_600L, somados[9L])
    }

    /** `R51` (Março 2026) e `R118` (Julho 2026) são pagamentos reais e caem os dois em Outros. */
    @Test
    fun `R51 e R118 caem ambos em Outros e somam`() {
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("R51", 2_587_000L),
                linha("R118", 1_300L)
            ),
            catalogo,
            mutableListOf()
        )

        assertEquals(2_588_300L, somados[99L])
    }

    /** As informativas vêm do parser sem código interno (ver CODIGOS_INFORMATIVOS). */
    @Test
    fun `linha informativa nao escreve valor nenhum`() {
        val naoReconhecidas = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("R01", 11_379_800L),
                linha("F02", 396_700L)   // 39,67 €: baixa, informativa
            ),
            catalogo,
            naoReconhecidas
        )

        assertEquals(mapOf(1L to 11_379_800L), somados)
        assertTrue(naoReconhecidas.isEmpty())
    }

    /** Sem rubrica no catálogo a linha não escreve e o código é reportado à UI. */
    @Test
    fun `codigo sem rubrica no catalogo fica de fora e e reportado`() {
        val naoReconhecidas = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(linha("Z99", 100_000L, codigoInterno = "ZZZ")),
            catalogo,
            naoReconhecidas
        )

        assertTrue(somados.isEmpty())
        assertEquals(listOf("ZZZ"), naoReconhecidas)
    }

    /** Uma linha só (o caso comum) escreve o seu valor: a soma não pode regredir isto. */
    @Test
    fun `uma linha so escreve o seu valor`() {
        val somados = somarLinhasPorRubrica(
            listOf(linha("R01", 11_379_800L), linha("R14", 0L)),   // Março 2026: baixa, R14 = 0,00
            catalogo,
            mutableListOf()
        )

        assertEquals(mapOf(1L to 11_379_800L, 10L to 0L), somados)
    }

    private fun rubrica(id: Long, codigo: String) = Rubrica(
        id = id,
        codigo = codigo,
        nome = codigo,
        incideSS = true,
        incideIRS = true,
        incideSindicato = true,
        tipoCalculo = "FIXO",
        ativaConferencia = true,
        ordem = id.toInt()
    )

    /**
     * A linha como o parser a entrega: o código interno vem do mapeamento real, para o teste
     * falhar se o mapeamento mudar (é o que liga "RCR"/"RCRA" a SUP_ALIM e "F02" a nada).
     */
    private fun linha(
        codigoRecibo: String,
        valor: Long,
        codigoInterno: String? = mapearCodigoRecibo(codigoRecibo).codigoInterno
    ) = LinhaReciboImportada(
        codigoRecibo = codigoRecibo,
        descricao = codigoRecibo,
        codigoInterno = codigoInterno,
        natureza = NaturezaRubrica.ABONO,
        valor = valor,
        quantidade = null,
        data = null,
        ordem = 1,
        aviso = null
    )
}
