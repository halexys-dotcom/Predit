package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.calc.NaturezaRubrica

/**
 * O filtro do ecrã de conferência (2026-10-21). Duas regras, ambas de desenho:
 * - as rubricas de recurso ("Outros" dos abonos e "Outros" dos descontos) só aparecem com valor;
 * - as rubricas de feriado (`HSUP_DN_FER`, `HSUP_NT_FER`, `DESC_FER`, `ACR_NOT_FER`) só aparecem
 *   nos meses com feriado, mesmo a 0,00 nesses meses.
 *
 * O estado ([ReciboUiState.linhas]) e a BD ficam com o catálogo inteiro — é dele que o "Guardar"
 * escreve uma linha por rubrica e que o modal de mapeamento tira as escolhas.
 */
class LinhasVisiveisTest {

    /**
     * Outubro de 2026 tem o 5 (Implantação da República) e por isso as linhas de feriado ficam;
     * as de recurso estão a zero e saem. As restantes aparecem sempre, mesmo as que estão a zero
     * (o D04 deste mês, por exemplo).
     */
    @Test
    fun `mes com feriado mostra as rubricas de feriado e esconde os Outros a zero`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("HSUP_DN_FER", 0L),
            conferencia("HSUP_NT_FER", 0L),
            conferencia("DESC_FER", 0L),
            conferencia("ACR_NOT_FER", 0L),
            conferencia("D04", 0L),
            conferencia("OUTROS", 0L),
            conferencia("OUTROS_DESC", 0L)
        )

        assertEquals(
            listOf("VENC", "HSUP_DN_FER", "HSUP_NT_FER", "DESC_FER", "ACR_NOT_FER", "D04"),
            linhasVisiveis(linhas, temFeriado = true).map { it.codigo }
        )
    }

    /**
     * Um mês sem feriados (setembro de 2026, por exemplo) não tem horas de feriado nem descanso
     * trabalhado em feriado: as quatro linhas estão a zero e desaparecem do ecrã.
     */
    @Test
    fun `mes sem feriado esconde as quatro rubricas de feriado`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("HSUP_DN_FER", 0L),
            conferencia("HSUP_NT_FER", 0L),
            conferencia("DESC_FER", 0L),
            conferencia("ACR_NOT_FER", 0L),
            conferencia("D01", 1_251_778L)
        )

        assertEquals(
            listOf("VENC", "D01"),
            linhasVisiveis(linhas, temFeriado = false).map { it.codigo }
        )
    }

    /**
     * As rubricas de recurso ("Outros" dos abonos e "Outros" dos descontos) só se desenham quando
     * têm valor. Num mês sem importação nem edição as duas estão a zero e desaparecem.
     */
    @Test
    fun `Outros a zero ficam fora da lista e as restantes linhas ficam`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("D01", 1_251_778L),
            conferencia("OUTROS", 0L),
            conferencia("OUTROS_DESC", 0L)
        )

        assertEquals(
            listOf("VENC", "D01"),
            linhasVisiveis(linhas, temFeriado = false).map { it.codigo }
        )
    }

    /**
     * Basta uma das colunas ter valor: o "Outros" do abono vem de uma importação (só no Real) e o
     * OUTROS_DESC pode ter valor estimado numa edição. Esconder a linha escondia o valor.
     */
    @Test
    fun `Outros com valor numa das colunas aparecem na lista`() {
        val linhas = listOf(
            conferencia("OUTROS", estimado = 0L, real = 2_050_000L),
            conferencia("OUTROS_DESC", estimado = 1_050_000L, real = 0L),
            conferencia("VENC", 11_379_800L)
        )

        assertEquals(
            listOf("OUTROS", "OUTROS_DESC", "VENC"),
            linhasVisiveis(linhas, temFeriado = false).map { it.codigo }
        )
    }

    /**
     * Rede de segurança: uma linha com valor nunca é escondida. Com o pagamento das variáveis
     * desfasado, um valor de feriado de um mês anterior (ou escrito à mão) pode cair num mês sem
     * feriado nenhum — aí a linha tem de aparecer, senão o total não fechava com o que se vê.
     */
    @Test
    fun `rubrica de feriado com valor aparece mesmo sem feriado no mes`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("HSUP_DN_FER", estimado = 960_000L, real = 0L)
        )

        assertEquals(
            listOf("VENC", "HSUP_DN_FER"),
            linhasVisiveis(linhas, temFeriado = false).map { it.codigo }
        )
    }

    private fun conferencia(
        codigo: String,
        estimado: Long,
        real: Long = estimado
    ) = LinhaConferencia(
        rubricaId = codigo.hashCode().toLong(),
        codigo = codigo,
        nome = codigo,
        natureza = if (codigo == RUBRICA_OUTROS_DESC || codigo.startsWith("D")) {
            NaturezaRubrica.DESCONTO
        } else {
            NaturezaRubrica.ABONO
        },
        ordem = 0,
        ativaConferencia = true,
        valorEstimado = estimado,
        valorReal = real,
        textoRealEditavel = ""
    )
}
