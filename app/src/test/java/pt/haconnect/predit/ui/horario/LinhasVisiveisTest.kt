package pt.haconnect.predit.ui.horario

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import java.time.YearMonth

/**
 * O filtro do ecrã de conferência (v2.1.1). Regras, todas de desenho:
 * - as rubricas de recurso ("Outros" dos abonos e "Outros" dos descontos) só aparecem com valor;
 * - as rubricas de feriado (`HSUP_DN_FER`, `HSUP_NT_FER`, `DESC_FER`, `ACR_NOT_FER`) só aparecem
 *   nos meses com feriado, mesmo a 0,00 nesses meses;
 * - o subsídio de Natal (`NATAL`) só em dezembro;
 * - o subsídio de férias (`FERIAS`) só quando há férias no mês ou no seguinte;
 * - o subsídio de função (`SUP_FUNCAO`) só nas categorias que o recebem — o Team Leader;
 * - o desconto sindical (`D04`) só nos contratos sindicalizados;
 * - o complemento de baixa (`COMP_BAIXA`) só com valor: a zero não se desenha.
 *
 * Uma linha com valor nunca é escondida, seja qual for a regra — é a rede de segurança.
 *
 * O estado ([ReciboUiState.linhas]) e a BD ficam com o catálogo inteiro — é dele que o "Guardar"
 * escreve uma linha por rubrica e que o modal de mapeamento tira as escolhas.
 */
class LinhasVisiveisTest {

    /**
     * A regra do Natal tal como o ReciboViewModel a aplica ao mês: dezembro. Em novembro o
     * subsídio ainda não se desenha; em dezembro, sim.
     */
    @Test
    fun `em novembro o Natal nao aparece e em dezembro aparece`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("NATAL", 0L)
        )
        val novembro = YearMonth.of(2026, 11)
        val dezembro = YearMonth.of(2026, 12)

        assertEquals(
            listOf("VENC"),
            visiveis(linhas, mes = novembro, temFeriado = false).map { it.codigo }
        )
        assertEquals(
            listOf("VENC", "NATAL"),
            visiveis(linhas, mes = dezembro, temFeriado = true).map { it.codigo }
        )
    }

    /** Um mês sem férias marcadas nem no mês nem no seguinte não mostra o subsídio de férias. */
    @Test
    fun `sem ferias no mes nem no seguinte o subsidio de ferias nao aparece`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("FERIAS", 0L)
        )

        assertEquals(
            listOf("VENC"),
            linhasVisiveis(
                linhas,
                temFeriado = true,
                mostrarNatal = false,
                mostrarFerias = false,
                mostrarSubsidioFuncao = false,
                mostrarSindicato = true
            ).map { it.codigo }
        )
    }

    /**
     * Com férias no mês (ou no seguinte, que é o caso de julho quando as férias são em agosto) a
     * linha aparece, mesmo a 0,00: é a altura em que o utilizador a quer conferir e editar.
     */
    @Test
    fun `com ferias no mes o subsidio de ferias aparece`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("FERIAS", 0L)
        )

        assertEquals(
            listOf("VENC", "FERIAS"),
            linhasVisiveis(
                linhas,
                temFeriado = true,
                mostrarNatal = false,
                mostrarFerias = true,
                mostrarSubsidioFuncao = false,
                mostrarSindicato = true
            ).map { it.codigo }
        )
    }

    /**
     * Rede de segurança: um subsídio com valor — estimado ou importado do PDF — aparece sempre,
     * mesmo fora das regras. Sem isto, um subsídio pago em novembro ou importado num mês sem
     * férias desaparecia do ecrã e os totais deixavam de fechar com o que se vê.
     */
    @Test
    fun `subsidios com valor aparecem sempre mesmo fora das regras`() {
        val linhas = listOf(
            conferencia("NATAL", estimado = 0L, real = 5_689_900L),
            conferencia("FERIAS", estimado = 3_400_000L, real = 0L),
            conferencia("VENC", 11_379_800L)
        )

        assertEquals(
            listOf("NATAL", "FERIAS", "VENC"),
            linhasVisiveis(
                linhas,
                temFeriado = false,
                mostrarNatal = false,
                mostrarFerias = false,
                mostrarSubsidioFuncao = false,
                mostrarSindicato = false
            ).map { it.codigo }
        )
    }

    /**
     * Um mês com feriado mostra as quatro rubricas de feriado; as de recurso estão a zero e saem.
     * As restantes aparecem sempre, mesmo as que estão a zero (o D04 deste mês).
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
            visiveis(
                linhas,
                mes = YearMonth.of(2026, 10),
                temFeriado = true,
                mostrarSindicato = true
            ).map { it.codigo }
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
            visiveis(linhas, mes = YearMonth.of(2026, 9), temFeriado = false).map { it.codigo }
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
            visiveis(linhas, mes = YearMonth.of(2026, 9), temFeriado = false).map { it.codigo }
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
            visiveis(linhas, mes = YearMonth.of(2026, 9), temFeriado = false).map { it.codigo }
        )
    }

    /**
     * Rede de segurança das rubricas de feriado: uma linha com valor nunca é escondida. Com o
     * pagamento das variáveis desfasado, um valor de feriado de um mês anterior (ou escrito à mão)
     * pode cair num mês sem feriado nenhum — aí a linha tem de aparecer, senão o total não fechava
     * com o que se vê.
     */
    @Test
    fun `rubrica de feriado com valor aparece mesmo sem feriado no mes`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("HSUP_DN_FER", estimado = 960_000L, real = 0L)
        )

        assertEquals(
            listOf("VENC", "HSUP_DN_FER"),
            visiveis(linhas, mes = YearMonth.of(2026, 9), temFeriado = false).map { it.codigo }
        )
    }

    /**
     * O subsídio de função só existe no Team Leader. Num contrato de outra categoria (a APAA é o
     * default) a linha está a 0,00 e não se desenha.
     */
    @Test
    fun `SUP_FUNCAO fica oculto se a categoria nao for Team Leader`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("SUP_FUNCAO", 0L)
        )

        assertEquals(
            listOf("VENC"),
            visiveis(
                linhas,
                mes = YearMonth.of(2026, 9),
                temFeriado = false,
                mostrarSubsidioFuncao = false
            ).map { it.codigo }
        )
    }

    /** No Team Leader a linha desenha-se mesmo a 0,00: é a rubrica que ele recebe. */
    @Test
    fun `SUP_FUNCAO aparece no Team Leader mesmo a zero`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("SUP_FUNCAO", 0L)
        )

        assertEquals(
            listOf("VENC", "SUP_FUNCAO"),
            visiveis(
                linhas,
                mes = YearMonth.of(2026, 9),
                temFeriado = false,
                mostrarSubsidioFuncao = true
            ).map { it.codigo }
        )
    }

    /** Contrato não sindicalizado: o D04 está a 0 e não se desenha. */
    @Test
    fun `D04 fica oculto se o contrato nao for sindicalizado`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("D04", 0L)
        )

        assertEquals(
            listOf("VENC"),
            visiveis(
                linhas,
                mes = YearMonth.of(2026, 9),
                temFeriado = false,
                mostrarSindicato = false
            ).map { it.codigo }
        )
    }

    /** Contrato sindicalizado: o D04 desenha-se, mesmo a 0,00 num mês sem VENC. */
    @Test
    fun `D04 aparece no contrato sindicalizado mesmo a zero`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("D04", 0L)
        )

        assertEquals(
            listOf("VENC", "D04"),
            visiveis(
                linhas,
                mes = YearMonth.of(2026, 9),
                temFeriado = false,
                mostrarSindicato = true
            ).map { it.codigo }
        )
    }

    /**
     * A rede de segurança vale para todas as rubricas novas: o complemento de baixa (que a zero
     * nunca se desenha) e o D04 (com o switch do sindicato desligado) aparecem quando o recibo do
     * mês traz valor — importado do PDF, gravado de um mês anterior ou escrito à mão.
     */
    @Test
    fun `qualquer rubrica com valor prevalece sobre a regra que a esconderia`() {
        val linhas = listOf(
            conferencia("VENC", 11_379_800L),
            conferencia("COMP_BAIXA", estimado = 0L, real = 286_410L),
            conferencia("D04", estimado = 113_798L, real = 0L),
            conferencia("SUP_FUNCAO", estimado = 0L, real = 524_600L)
        )

        assertEquals(
            listOf("VENC", "COMP_BAIXA", "D04", "SUP_FUNCAO"),
            visiveis(
                linhas,
                mes = YearMonth.of(2026, 5),
                temFeriado = false,
                mostrarSubsidioFuncao = false,
                mostrarSindicato = false
            ).map { it.codigo }
        )
    }

    /**
     * Chama o filtro com as flags do ecrã, derivando a do Natal do mês como o ViewModel faz
     * (dezembro) e deixando as das férias explícitas — é o que as ausências decidem. As duas do
     * contrato (subsídio de função e sindicato) ficam a false por omissão: um contrato APAA não
     * sindicalizado, que é o caso mais comum.
     */
    private fun visiveis(
        linhas: List<LinhaConferencia>,
        mes: YearMonth,
        temFeriado: Boolean,
        mostrarFerias: Boolean = false,
        mostrarSubsidioFuncao: Boolean = false,
        mostrarSindicato: Boolean = false
    ) = linhasVisiveis(
        linhas,
        temFeriado = temFeriado,
        mostrarNatal = mes.monthValue == 12,
        mostrarFerias = mostrarFerias,
        mostrarSubsidioFuncao = mostrarSubsidioFuncao,
        mostrarSindicato = mostrarSindicato
    )

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
