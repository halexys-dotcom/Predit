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
 * é o acerto) — e o valor da rubrica tem de ser a **soma** das linhas, não a última delas. O `R51`
 * deixou de partilhar "Outros" com o `R118` na 28i: tem rubrica própria (`COMP_BAIXA`, que incide
 * SS e IRS).
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
        rubrica(24L, "COMP_BAIXA"),
        rubrica(98L, "OUTROS_DESC"),
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

    /**
     * `R51` (Março 2026) saiu de "Outros" na 28i: vai para `COMP_BAIXA`, que incide SS e IRS.
     * O `R118` (Julho 2026, média de horas noturnas) também é tributado e continua em "Outros" —
     * cada linha escreve só na sua rubrica.
     */
    @Test
    fun `R51 vai para COMP_BAIXA e R118 fica em Outros`() {
        val naoReconhecidas = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("R51", 2_587_000L),
                linha("R118", 1_300L)
            ),
            catalogo,
            naoReconhecidas
        )

        assertEquals(mapOf(24L to 2_587_000L, 99L to 1_300L), somados)
        assertTrue(naoReconhecidas.isEmpty())
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

    /**
     * 28i: num mês de baixa total o parser marca o `R01` como informativo — o pagamento do mês é
     * o complemento (`R51`). A rubrica é declarada pelo recibo e não paga, portanto escreve-se 0
     * (o que também limpa o VENC que tivesse ficado de uma importação anterior). É esta a conta
     * que faz os Totais Reais de Março fecharem nos 258,70 € que o recibo declara em Abonos.
     */
    @Test
    fun `Marco 2026 - R01 informativo escreve 0 no VENC e o mes fecha no R51`() {
        val naoReconhecidas = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("R01", 11_379_800L, codigoInterno = null),   // informativo: mês de baixa total
                linha("R14", 0L),
                linha("R51", 2_587_000L),
                linha("RCR", 1_727_000L),
                linha("RCRA", -1_727_000L)
            ),
            catalogo,
            naoReconhecidas
        )

        assertEquals(0L, somados[1L])                          // VENC: não é pago
        assertEquals(2_587_000L, somados[24L])                 // COMP_BAIXA: o pagamento do mês
        assertEquals(0L, somados[9L])                          // SUP_ALIM: cartão anulado
        assertEquals(0L, somados[10L])                         // SUP_TRAN: R14 = 0,00
        assertEquals(2_587_000L, somados.values.sum())         // Abonos = 258,70 €
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

    /**
     * C7: o D05 da Fundação ("Desconto Subsídio Alimentação", 105,00 €) é um desconto. Mapeado
     * para OUTROS — ou para SUP_ALIM, que era a sugestão da heurística — somava ao líquido.
     * Desde 2026-10-21 não bloqueia: vai para OUTROS_DESC (rubrica de desconto) e a UI avisa.
     */
    @Test
    fun `desconto mapeado para rubrica de abono vai para Outros Desconto`() {
        val descontosSemRubrica = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("R01", 9_666_700L),
                linha("D05", 1_050_000L, codigoInterno = "OUTROS", natureza = NaturezaRubrica.DESCONTO)
            ),
            catalogo,
            mutableListOf(),
            descontosSemRubrica,
            avisos
        )

        assertEquals(mapOf(1L to 9_666_700L, 98L to 1_050_000L), somados)
        assertTrue(descontosSemRubrica.isEmpty())
        assertEquals(listOf("Desconto desconhecido foi para Outros (Desconto)"), avisos)
    }

    /** C7: um desconto sem código interno nenhum também vai para Outros (Desconto), com aviso. */
    @Test
    fun `desconto sem codigo interno vai para Outros Desconto`() {
        val avisos = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(linha("D05", 1_050_000L, codigoInterno = null, natureza = NaturezaRubrica.DESCONTO)),
            catalogo,
            mutableListOf(),
            mutableListOf(),
            avisos
        )

        assertEquals(mapOf(98L to 1_050_000L), somados)
        assertEquals(listOf("Desconto desconhecido foi para Outros (Desconto)"), avisos)
    }

    /**
     * C7: sem OUTROS_DESC no catálogo (BD anterior à rubrica) não há onde escrever o valor a
     * subtrair — a importação volta a bloquear em vez de somar o desconto ao líquido.
     */
    @Test
    fun `desconto sem OUTROS_DESC no catalogo e reportado`() {
        val descontosSemRubrica = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(linha("D05", 1_050_000L, codigoInterno = null, natureza = NaturezaRubrica.DESCONTO)),
            catalogo - "OUTROS_DESC",
            mutableListOf(),
            descontosSemRubrica
        )

        assertTrue(somados.isEmpty())
        assertEquals(listOf("D05"), descontosSemRubrica)
    }

    /** C7: um desconto que cai numa rubrica de desconto do catálogo escreve normalmente. */
    @Test
    fun `desconto com rubrica de desconto escreve o valor`() {
        val descontosSemRubrica = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(linha("D02", 460_000L, natureza = NaturezaRubrica.DESCONTO)),
            catalogo + ("D02" to rubrica(21L, "D02")),
            mutableListOf(),
            descontosSemRubrica
        )

        assertEquals(mapOf(21L to 460_000L), somados)
        assertTrue(descontosSemRubrica.isEmpty())
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
        codigoInterno: String? = mapearCodigoRecibo(codigoRecibo).codigoInterno,
        natureza: NaturezaRubrica = NaturezaRubrica.ABONO
    ) = LinhaReciboImportada(
        codigoRecibo = codigoRecibo,
        descricao = codigoRecibo,
        codigoInterno = codigoInterno,
        natureza = natureza,
        valor = valor,
        quantidade = null,
        data = null,
        ordem = 1,
        aviso = null
    )
}
