package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.importacao.LinhaReciboImportada
import pt.haconnect.predit.domain.importacao.mapearCodigoRecibo
import pt.haconnect.predit.domain.model.Rubrica

/**
 * A aplicaÃ§Ã£o de um recibo importado Ã s ediÃ§Ãµes do mÃªs: dois cÃ³digos do PDF podem cair na mesma
 * rubrica do catÃ¡logo â€” `RCR` e `RCRA` sÃ£o ambos o subsÃ­dio de alimentaÃ§Ã£o em cartÃ£o (o segundo
 * Ã© o acerto) â€” e o valor da rubrica tem de ser a **soma** das linhas, nÃ£o a Ãºltima delas. O `R51`
 * deixou de partilhar "Outros" com o `R118` na 28i: tem rubrica prÃ³pria (`COMP_BAIXA`, que incide
 * SS e IRS).
 *
 * Os valores sÃ£o os dos recibos reais (ver ReciboPdfParserTest e os fixtures): MarÃ§o 2026 e
 * Agosto 2025. Este caminho â€” importar para as ediÃ§Ãµes â€” nÃ£o Ã© exercitado pelo parser nem pelo
 * ReciboConferenciaTest (que escreve Ã  mÃ£o), daÃ­ este teste.
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
     * PÃ¡gina 1 de MarÃ§o 2026: `RCR` +172,70 â‚¬ e logo abaixo `RCRA` âˆ’172,70 â‚¬ (o acerto de
     * MarÃ§o, mÃªs de baixa). As duas linhas existem no PDF e anulam-se â€” o recibo declara 0,00
     * no subsÃ­dio de alimentaÃ§Ã£o. Substituir a Ãºltima linha dava âˆ’172,70 â‚¬.
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
     * Agosto 2025 (Cegid): `RCR` 163,24 â‚¬ e `RCRA` 7,42 â‚¬ â€” aqui as duas somam-se, e os 170,66 â‚¬
     * sÃ£o o que falta para o total de abonos desse recibo bater certo (1 508,57 â‚¬).
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
     * `R51` (MarÃ§o 2026) saiu de "Outros" na 28i: vai para `COMP_BAIXA`, que incide SS e IRS.
     * O `R118` (Julho 2026, mÃ©dia de horas noturnas) tambÃ©m Ã© tributado e continua em "Outros" â€”
     * cada linha escreve sÃ³ na sua rubrica.
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

    /** As informativas vÃªm do parser sem cÃ³digo interno (ver CODIGOS_INFORMATIVOS). */
    @Test
    fun `linha informativa nao escreve valor nenhum`() {
        val naoReconhecidas = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("R01", 11_379_800L),
                linha("F02", 396_700L)   // 39,67 â‚¬: baixa, informativa
            ),
            catalogo,
            naoReconhecidas
        )

        assertEquals(mapOf(1L to 11_379_800L), somados)
        assertTrue(naoReconhecidas.isEmpty())
    }

    /**
     * 28i: num mÃªs de baixa total o parser marca o `R01` como informativo â€” o pagamento do mÃªs Ã©
     * o complemento (`R51`). A rubrica Ã© declarada pelo recibo e nÃ£o paga, portanto escreve-se 0
     * (o que tambÃ©m limpa o VENC que tivesse ficado de uma importaÃ§Ã£o anterior). Ã‰ esta a conta
     * que faz os Totais Reais de MarÃ§o fecharem nos 258,70 â‚¬ que o recibo declara em Abonos.
     */
    @Test
    fun `Marco 2026 - R01 informativo escreve 0 no VENC e o mes fecha no R51`() {
        val naoReconhecidas = mutableListOf<String>()
        val somados = somarLinhasPorRubrica(
            listOf(
                linha("R01", 11_379_800L, codigoInterno = null),   // informativo: mÃªs de baixa total
                linha("R14", 0L),
                linha("R51", 2_587_000L),
                linha("RCR", 1_727_000L),
                linha("RCRA", -1_727_000L)
            ),
            catalogo,
            naoReconhecidas
        )

        assertEquals(0L, somados[1L])                          // VENC: nÃ£o Ã© pago
        assertEquals(2_587_000L, somados[24L])                 // COMP_BAIXA: o pagamento do mÃªs
        assertEquals(0L, somados[9L])                          // SUP_ALIM: cartÃ£o anulado
        assertEquals(0L, somados[10L])                         // SUP_TRAN: R14 = 0,00
        assertEquals(2_587_000L, somados.values.sum())         // Abonos = 258,70 â‚¬
        assertTrue(naoReconhecidas.isEmpty())
    }

    /** Sem rubrica no catÃ¡logo a linha nÃ£o escreve e o cÃ³digo Ã© reportado Ã  UI. */
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

    /** Uma linha sÃ³ (o caso comum) escreve o seu valor: a soma nÃ£o pode regredir isto. */
    @Test
    fun `uma linha so escreve o seu valor`() {
        val somados = somarLinhasPorRubrica(
            listOf(linha("R01", 11_379_800L), linha("R14", 0L)),   // MarÃ§o 2026: baixa, R14 = 0,00
            catalogo,
            mutableListOf()
        )

        assertEquals(mapOf(1L to 11_379_800L, 10L to 0L), somados)
    }

    /**
     * C7: o D05 da FundaÃ§Ã£o ("Desconto SubsÃ­dio AlimentaÃ§Ã£o", 105,00 â‚¬) Ã© um desconto. Mapeado
     * para OUTROS â€” ou para SUP_ALIM, que era a sugestÃ£o da heurÃ­stica â€” somava ao lÃ­quido.
     * Desde 2026-10-21 nÃ£o bloqueia: vai para OUTROS_DESC (rubrica de desconto) e a UI avisa.
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
        assertEquals(listOf("Desconto desconhecido foi para Outros (desconto)"), avisos)
    }

    /** C7: um desconto sem cÃ³digo interno nenhum tambÃ©m vai para OUTROS_DESC ("Outros"), com aviso. */
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
        assertEquals(listOf("Desconto desconhecido foi para Outros (desconto)"), avisos)
    }

    /**
     * C7: sem OUTROS_DESC no catÃ¡logo (BD anterior Ã  rubrica) nÃ£o hÃ¡ onde escrever o valor a
     * subtrair â€” a importaÃ§Ã£o volta a bloquear em vez de somar o desconto ao lÃ­quido.
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

    /** C7: um desconto que cai numa rubrica de desconto do catÃ¡logo escreve normalmente. */
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
     * A linha como o parser a entrega: o cÃ³digo interno vem do mapeamento real, para o teste
     * falhar se o mapeamento mudar (Ã© o que liga "RCR"/"RCRA" a SUP_ALIM e "F02" a nada).
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
