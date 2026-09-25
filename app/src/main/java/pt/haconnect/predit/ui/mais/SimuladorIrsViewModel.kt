package pt.haconnect.predit.ui.mais

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.data.repository.ReciboRepository
import pt.haconnect.predit.data.repository.RubricaRepository
import pt.haconnect.predit.domain.calc.ANO_IRS_ANUAL
import pt.haconnect.predit.domain.calc.ESCALOES_ANUAIS_CONTINENTE_2026
import pt.haconnect.predit.domain.calc.LIMITE_NAO_APLICACAO_MINIMO_2026_MIL
import pt.haconnect.predit.domain.calc.ResultadoIrsAnual
import pt.haconnect.predit.domain.calc.abatimentoMinimoExistencia
import pt.haconnect.predit.domain.calc.calcularIrsAnual
import pt.haconnect.predit.domain.calc.calcularIrsAnualConjunta
import pt.haconnect.predit.domain.calc.calcularIsencaoIrsJovem
import pt.haconnect.predit.domain.calc.coletaIndividualSobreAgregado
import pt.haconnect.predit.domain.calc.deducaoEspecificaCategoriaA
import pt.haconnect.predit.domain.calc.percentagemIsencaoIrsJovem
import pt.haconnect.predit.domain.model.CATEGORIA_CCT_PADRAO
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.EstadoCivil
import pt.haconnect.predit.domain.model.ParametrosCCT
import pt.haconnect.predit.domain.model.ReciboLinha
import pt.haconnect.predit.domain.model.ReciboMes
import pt.haconnect.predit.domain.model.Rubrica
import pt.haconnect.predit.domain.model.deTexto
import pt.haconnect.predit.domain.model.milParaEuros
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Rubrica do recibo que é a retenção mensal de IRS (catálogo semeado). */
private const val CODIGO_RUBRICA_IRS = "D02"

/**
 * 14 pagamentos (12 meses mais subsídios de férias e Natal) é o habitual do CCT. Já não
 * multiplica nada: o campo do simulador é o total anual. Serve só para estimar o ponto de
 * partida quando ainda não há recibos guardados — vencimento base da categoria × 14.
 */
private const val PAGAMENTOS_PADRAO = 14

private val LOCALE_PT: Locale = Locale.forLanguageTag("pt-PT")

/**
 * Estado do Simulador de IRS (Fase 21d). O rendimento do titular é uma projeção anual
 * ([rendimentoAnualTexto]) construída a partir do que está gravado: [rendimentoAcumuladoMil] é
 * a soma dos meses e [mesesRegistados] quantos são (os rótulos em [mesesRegistadosRotulos]).
 * [valorEditadoManualmente] marca que quem escreveu o valor foi o utilizador.
 *
 * [rendimentoConjugeTexto] só conta para casado com 2 titulares: escrito, o cálculo passa a ser
 * em tributação conjunta e [tributacaoConjunta] fica a verdadeiro (o ecrã mostra o modo).
 * [coletaSeIndividual] e [poupancaQuociente] servem só essa comparação: quanto o agregado
 * pagaria como um só titular e quanto o quociente conjugal poupa face a isso (0 no modo
 * individual, onde não se aplicam).
 *
 * [deducoesColetaTexto] são as deduções à coleta escritas pelo utilizador (vazio = 0,00 €) e
 * [aplicarMinimoExistencia] é o interruptor do art. 70.º, ligado por omissão — o abatimento
 * vem no [resultado], que é null enquanto não há dados suficientes, com o motivo em [aviso].
 *
 * [numeroPagamentos] fica como referência de leitura (o habitual são 14), mas o cálculo já não
 * o usa: o input do motor é a projeção anual que está no campo.
 */
data class SimuladorUiState(
    val rendimentoAnualTexto: String = "",
    val valorEditadoManualmente: Boolean = false,
    val rendimentoAcumuladoMil: Long = 0L,
    val mesesRegistados: Int = 0,
    val mesesRegistadosRotulos: List<String> = emptyList(),
    val rendimentoConjugeTexto: String = "",
    val deducoesColetaTexto: String = "",
    val aplicarMinimoExistencia: Boolean = true,
    val tributacaoConjunta: Boolean = false,
    val retencoesEfetuadas: Long = 0L,
    val percentagemIrsJovem: Int? = null,
    val regiao: String = "CONTINENTE",
    val estadoCivil: String = "",
    val titulares: Int = 1,
    val numeroDependentes: Int = 0,
    val numeroPagamentos: Int = PAGAMENTOS_PADRAO,
    val resultado: ResultadoIrsAnual? = null,
    val coletaSeIndividual: Long = 0L,
    val poupancaQuociente: Long = 0L,
    val anoSelecionado: Int = LocalDate.now().year,
    val carregando: Boolean = false,
    val aviso: String? = null
)

/**
 * Simulador de IRS anual (Fase 21c): projeção do ano a partir do histórico de recibos, com
 * tributação individual ou conjunta.
 *
 * Lê o contrato (situação familiar, região e IRS Jovem), o CCT da categoria, o catálogo de
 * rubricas (para saber qual é o D02) e o que está gravado no ano: os cabeçalhos dos meses
 * (para somar os abonos e contar quantos meses há) e as linhas (para saber quanto já foi
 * retido). Não escreve nada.
 *
 * O campo do titular é o rendimento bruto do ano e vem pré-preenchido com a projeção — a média
 * mensal dos abonos guardados × 12. Sem recibos nenhuns, o vencimento base da categoria × 14.
 * Quem escrever lá um valor passa a mandar; [reporRendimento] devolve o pré-preenchido.
 *
 * Com casado de 2 titulares e um valor no campo do cônjuge, o cálculo passa a
 * [calcularIrsAnualConjunta] (quociente conjugal); sem esse valor, continua a ser o
 * [calcularIrsAnual] individual de sempre.
 */
class SimuladorIrsViewModel(
    private val reciboRepository: ReciboRepository,
    private val contratoRepository: ContratoRepository,
    private val parametrosCCTRepository: ParametrosCCTRepository,
    private val rubricaRepository: RubricaRepository
) : ViewModel() {

    /** O que está escrito no campo; null quando o valor ainda é o pré-preenchido. */
    private val _rendimentoAnualTexto = MutableStateFlow<String?>(null)

    /** O rendimento do cônjuge; vazio é o normal (cálculo individual). */
    private val _rendimentoConjugeTexto = MutableStateFlow("")

    /** Deduções à coleta escritas pelo utilizador; vazio é 0,00 €. */
    private val _deducoesColetaTexto = MutableStateFlow("")

    /** Interruptor do art. 70.º; ligado por omissão, como no simulador da AT. */
    private val _aplicarMinimoExistencia = MutableStateFlow(true)

    private val ano: Int = LocalDate.now().year

    private data class Fontes(
        val contrato: ContratoUtilizador?,
        val parametros: List<ParametrosCCT>,
        val rubricas: List<Rubrica>,
        val mesesDoAno: List<ReciboMes>,
        val linhasDoAno: List<ReciboLinha>
    )

    private val fontes: StateFlow<Fontes> = combine(
        contratoRepository.observar(),
        parametrosCCTRepository.observarTodos(),
        rubricaRepository.observarTodas(),
        reciboRepository.observarMesesDoAno(ano),
        reciboRepository.observarLinhasDoAno(ano)
    ) { contrato, parametros, rubricas, meses, linhas ->
        Fontes(contrato, parametros, rubricas, meses, linhas)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Fontes(null, emptyList(), emptyList(), emptyList(), emptyList())
    )

    val estado: StateFlow<SimuladorUiState> = combine(
        _rendimentoAnualTexto,
        _rendimentoConjugeTexto,
        _deducoesColetaTexto,
        _aplicarMinimoExistencia,
        fontes
    ) { texto, textoConjuge, textoDeducoes, aplicarMinimo, f ->
        calcularEstado(texto, textoConjuge, textoDeducoes, aplicarMinimo, f)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SimuladorUiState(anoSelecionado = ano, carregando = true)
        )

    /**
     * O utilizador escreveu no campo: a partir daqui o pré-preenchimento não lhe mexe mais.
     * Apagar o campo (ou deixá-lo em branco) volta a ligá-lo.
     */
    fun atualizarRendimentoAnual(texto: String) {
        _rendimentoAnualTexto.value = texto.ifBlank { null }
    }

    /** «Repor»: limpa a edição e volta ao que os recibos (ou o CCT, sem recibos) dão. */
    fun reporRendimento() {
        _rendimentoAnualTexto.value = null
    }

    /** O rendimento do cônjuge. Vazio (ou em branco) volta ao cálculo individual. */
    fun atualizarRendimentoConjuge(texto: String) {
        _rendimentoConjugeTexto.value = texto
    }

    /** Deduções à coleta (saúde, educação, PPR, despesas gerais). Vazio é 0,00 €. */
    fun atualizarDeducoesColeta(texto: String) {
        _deducoesColetaTexto.value = texto
    }

    /** Liga ou desliga o abatimento por mínimo de existência (art. 70.º). */
    fun atualizarAplicarMinimoExistencia(aplicar: Boolean) {
        _aplicarMinimoExistencia.value = aplicar
    }

    private fun calcularEstado(
        texto: String?,
        textoConjuge: String,
        textoDeducoes: String,
        aplicarMinimo: Boolean,
        f: Fontes
    ): SimuladorUiState {
        val contrato = f.contrato
        if (contrato == null || f.rubricas.isEmpty()) {
            return SimuladorUiState(anoSelecionado = ano, carregando = true)
        }

        val vigente = parametrosCCTRepository.paraCategoria(
            lista = f.parametros,
            codigoCategoria = contrato.categoriaCodigo.ifEmpty { CATEGORIA_CCT_PADRAO },
            epochDay = LocalDate.now().toEpochDay()
        ) ?: return SimuladorUiState(anoSelecionado = ano, carregando = true)

        // O acumulado dos meses guardados e a projeção do ano: com 4 meses de recibos, o campo
        // mostra a média mensal × 12, não a soma dos 4. Sem recibos, o que um ano inteiro daria
        // ao vencimento base da categoria (× 14 pagamentos).
        val rendimentoAcumulado = f.mesesDoAno.sumOf { it.totalAbonos }
        val meses = f.mesesDoAno.size
        val mediaMensal = if (meses > 0) rendimentoAcumulado / meses else 0L
        val projecaoAnual = mediaMensal * 12L
        val baseCct = vigente.vencimentoBaseMil.toLong() * PAGAMENTOS_PADRAO
        val textoFinal = texto ?: textoDe(if (meses == 0) baseCct else projecaoAnual)

        val anual = textoParaMil(textoFinal)
        val percentagem = percentagemDoContrato(contrato, ano)
        val retencoes = somarRetencoes(f.rubricas, f.linhasDoAno)

        // Tributação conjunta: casado de 2 titulares com o rendimento do cônjuge escrito.
        // Sem isso é o cálculo individual de sempre.
        val conjunta = contrato.estadoCivil == EstadoCivil.CASADO &&
            contrato.titulares == 2 &&
            textoConjuge.isNotBlank()
        val conjuge = if (conjunta) textoParaMil(textoConjuge) else null

        // Deduções à coleta: campo vazio é zero; texto que não seja dinheiro invalida o cálculo.
        val deducoesColeta = if (textoDeducoes.isBlank()) 0L else textoParaMil(textoDeducoes)

        // n.º 4 a) do art. 70.º: a soma dos rendimentos do agregado não pode passar de
        // 2,2 × 14 × IAS por titular, senão não há abatimento para ninguém — nem para a isenção
        // do IRS Jovem, que incide sobre o coletável depois do abatimento.
        val numTitulares = if (conjunta) 2 else 1
        val somaRendimentos = (anual ?: 0L) + if (conjunta) (conjuge ?: 0L) else 0L
        val aplicarAbatimento = aplicarMinimo &&
            somaRendimentos <= LIMITE_NAO_APLICACAO_MINIMO_2026_MIL * numTitulares
        val abatimentoTitular1 = if (aplicarAbatimento && anual != null) {
            abatimentoMinimoExistencia(anual, deducaoEspecificaCategoriaA(ano), ano)
        } else 0L

        // Sem ano tabelado, o motor não sabe a dedução nem o IAS: nada de números errados.
        val resultado = if (
            anual != null &&
            ano == ANO_IRS_ANUAL &&
            (!conjunta || conjuge != null) &&
            (textoDeducoes.isBlank() || deducoesColeta != null)
        ) {
            if (conjunta && conjuge != null) {
                calcularIrsAnualConjunta(
                    rendimentoTitular1 = anual,
                    rendimentoTitular2 = conjuge,
                    retencoesEfetuadas = retencoes,
                    ano = ano,
                    escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
                    isencaoIrsJovemTitular1 = calcularIsencaoIrsJovem(
                        rendimentoBrutoAnual = anual,
                        percentagem = percentagem,
                        ano = ano,
                        abatimentoMil = abatimentoTitular1
                    ),
                    deducoesColeta = deducoesColeta ?: 0L,
                    aplicarMinimoExistencia = aplicarAbatimento
                )
            } else {
                calcularIrsAnual(
                    rendimentoBrutoAnual = anual,
                    retencoesEfetuadas = retencoes,
                    ano = ano,
                    escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
                    percentagemIrsJovem = percentagem,
                    deducoesColeta = deducoesColeta ?: 0L,
                    aplicarMinimoExistencia = aplicarAbatimento
                )
            }
        } else null

        // Comparação do modo conjunto (é só para o ecrã): quanto o agregado pagaria como um só
        // titular e quanto o quociente conjugal poupa face a isso. As deduções à coleta entram
        // nos dois lados, para a comparação não ficar desalinhada. Não altera nada do resto.
        val coletaSeIndividual = if (conjunta && anual != null && conjuge != null) {
            val coletaBruta = coletaIndividualSobreAgregado(
                rendimentoAgregado = anual + conjuge,
                ano = ano,
                escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
                aplicarMinimoExistencia = aplicarAbatimento
            )
            (coletaBruta - (deducoesColeta ?: 0L)).coerceAtLeast(0L)
        } else 0L
        val poupancaQuociente = if (conjunta && resultado != null) {
            coletaSeIndividual - resultado.coletaAposDeducoes
        } else 0L

        return SimuladorUiState(
            rendimentoAnualTexto = textoFinal,
            valorEditadoManualmente = texto != null,
            rendimentoAcumuladoMil = rendimentoAcumulado,
            mesesRegistados = meses,
            mesesRegistadosRotulos = f.mesesDoAno.sortedBy { it.anoMes }.map { rotuloMes(it.anoMes) },
            rendimentoConjugeTexto = textoConjuge,
            deducoesColetaTexto = textoDeducoes,
            aplicarMinimoExistencia = aplicarMinimo,
            tributacaoConjunta = conjunta,
            retencoesEfetuadas = retencoes,
            percentagemIrsJovem = percentagem,
            regiao = contrato.regiao.name,
            estadoCivil = contrato.estadoCivil.name,
            titulares = contrato.titulares,
            numeroDependentes = contrato.numeroDependentes,
            resultado = resultado,
            coletaSeIndividual = coletaSeIndividual,
            poupancaQuociente = poupancaQuociente,
            anoSelecionado = ano,
            aviso = when {
                ano != ANO_IRS_ANUAL -> "Simulador disponível para $ANO_IRS_ANUAL."
                anual == null -> "Escreve um rendimento anual válido."
                conjunta && conjuge == null -> "O rendimento do cônjuge não é um valor válido."
                textoDeducoes.isNotBlank() && deducoesColeta == null ->
                    "As deduções à coleta não são um valor válido."
                else -> null
            }
        )
    }

    /** Retenções do ano: a soma do valor real das linhas do D02 dos meses já registados. */
    private fun somarRetencoes(rubricas: List<Rubrica>, linhas: List<ReciboLinha>): Long {
        val idsIrs = rubricas.filter { it.codigo == CODIGO_RUBRICA_IRS }.map { it.id }.toSet()
        return linhas.filter { it.rubricaId in idsIrs }.sumOf { it.valorReal }
    }

    /** Percentagem de isenção do IRS Jovem do contrato, ou null se o regime não se aplica. */
    private fun percentagemDoContrato(contrato: ContratoUtilizador, ano: Int): Int? {
        if (!contrato.aplicarIrsJovem) return null
        val nascimento = contrato.anoNascimento ?: return null
        val primeiroRendimento = contrato.anoPrimeiroRendimento ?: return null
        return percentagemIsencaoIrsJovem(nascimento, primeiroRendimento, ano)
    }

    class Factory(
        private val reciboRepository: ReciboRepository,
        private val contratoRepository: ContratoRepository,
        private val parametrosCCTRepository: ParametrosCCTRepository,
        private val rubricaRepository: RubricaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SimuladorIrsViewModel(
                reciboRepository = reciboRepository,
                contratoRepository = contratoRepository,
                parametrosCCTRepository = parametrosCCTRepository,
                rubricaRepository = rubricaRepository
            ) as T
        }
    }
}

/** Unidades de 1/10000 € -> texto do campo ("1 137,98", sem o € que o rótulo já diz). */
private fun textoDe(valor: Long): String = valor.milParaEuros().removeSuffix(" €")

/** Texto do campo -> unidades de 1/10000 €; null se não for dinheiro válido. */
private fun textoParaMil(texto: String): Long? = try {
    deTexto(texto).numerador
} catch (_: Exception) {
    null
}

/** "2026-08" -> "Ago": o mês como aparece no resumo de onde veio o rendimento. */
private fun rotuloMes(anoMes: String): String {
    val numero = anoMes.substringAfter('-', "").toIntOrNull() ?: return anoMes
    val mes = runCatching { Month.of(numero) }.getOrNull() ?: return anoMes
    val curto = mes.getDisplayName(TextStyle.SHORT, LOCALE_PT).removeSuffix(".")
    return curto.replaceFirstChar { it.titlecase(LOCALE_PT) }
}
