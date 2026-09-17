package pt.haconnect.predit.ui.horario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.MunicipioRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.data.repository.ReciboRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.RubricaRepository
import pt.haconnect.predit.data.repository.TabelaIRSRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.AplicacaoVigente
import pt.haconnect.predit.domain.calc.ContextoCalculo
import pt.haconnect.predit.domain.calc.ContextoEstimativa
import pt.haconnect.predit.domain.calc.EscalaoIRS
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.calc.estimarRecibo
import pt.haconnect.predit.domain.calc.janelaNoturna
import pt.haconnect.predit.domain.calc.projetarIntervalo
import pt.haconnect.predit.domain.calc.valorHoraMil
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.LIMIAR_DIVERGENCIA_UNIDADES
import pt.haconnect.predit.domain.model.Municipio
import pt.haconnect.predit.domain.model.ParametrosCCT
import pt.haconnect.predit.domain.model.ReciboLinha
import pt.haconnect.predit.domain.model.ReciboMes
import pt.haconnect.predit.domain.model.Rubrica
import pt.haconnect.predit.domain.model.TipoTurno
import pt.haconnect.predit.domain.model.deTexto
import pt.haconnect.predit.domain.model.milParaEuros
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Uma rubrica no ecrã de conferência: estimado ao lado do real, mais o texto do campo.
 *
 * [textoInvalido] é um desvio ao enunciado, decidido nesta fase: o `isError` do campo é
 * por linha, e sem este sinal um valor mal escrito punha todas as rubricas a vermelho.
 */
data class LinhaConferencia(
    val rubricaId: Long,
    val codigo: String,
    val nome: String,
    val natureza: NaturezaRubrica,
    val ordem: Int,
    val ativaConferencia: Boolean,
    val valorEstimado: Long,
    val valorReal: Long,
    val textoRealEditavel: String,
    val textoInvalido: Boolean = false
) {
    val divergencia: Long get() = valorReal - valorEstimado
    val temDivergencia: Boolean get() = abs(divergencia) >= LIMIAR_DIVERGENCIA_UNIDADES
}

/**
 * Estado do ecrã. Os campos levam valores por omissão (o enunciado só os punha em
 * `mensagemErro`) porque o `stateIn` precisa de um estado inicial antes de haver dados.
 */
data class ReciboUiState(
    val anoMes: String = "",
    val anoMesFormatado: String = "",
    val linhas: List<LinhaConferencia> = emptyList(),
    val totalAbonosEstimado: Long = 0L,
    val totalAbonosReal: Long = 0L,
    val totalDescontosEstimado: Long = 0L,
    val totalDescontosReal: Long = 0L,
    val liquidoEstimado: Long = 0L,
    val liquidoReal: Long = 0L,
    val guardado: Boolean = false,
    val temAlteracoes: Boolean = false,
    val mensagemErro: String? = null,
    /** O catálogo/parâmetros ainda não chegaram: o ecrã mostra "a carregar", não uma lista vazia. */
    val carregando: Boolean = false,
    val dataFechoTexto: String = "",
    val irsRetidoAnoTexto: String = "",
    val nota: String = ""
) {
    /** Sem recibo gravado o formulário vem pré-preenchido, portanto Guardar tem de estar ativo. */
    val podeGuardar: Boolean get() = !carregando && (!guardado || temAlteracoes)
    val podeApagar: Boolean get() = !carregando && guardado
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReciboViewModel(
    private val reciboRepository: ReciboRepository,
    private val contratoRepository: ContratoRepository,
    private val parametrosCCTRepository: ParametrosCCTRepository,
    private val rubricaRepository: RubricaRepository,
    private val diaRealRepository: DiaRealRepository,
    private val rotacaoRepository: RotacaoRepository,
    private val tipoTurnoRepository: TipoTurnoRepository,
    private val tabelaIRSRepository: TabelaIRSRepository,
    private val ausenciaRepository: AusenciaRepository,
    private val municipioRepository: MunicipioRepository
) : ViewModel() {

    /** O que está escrito num campo: o texto tal como o utilizador o deixou e o último
     *  valor que dele se conseguiu ler (null enquanto nunca foi válido). */
    private data class Edicao(val texto: String, val valor: Long?)

    /** Cabeçalho editado do mês (data de fecho, IRS retido no ano, nota). */
    private data class CabecalhoEditado(
        val dataFechoTexto: String? = null,
        val irsRetidoAnoTexto: String? = null,
        val nota: String? = null
    )

    private data class Cabecalho(
        val anoMes: YearMonth,
        val edicoes: Map<String, Map<Long, Edicao>>,
        val mensagemErro: String?,
        val cabecalhoEditado: CabecalhoEditado?
    )

    private data class Catalogo(
        val contrato: ContratoUtilizador?,
        val parametros: List<ParametrosCCT>,
        val rubricas: List<Rubrica>
    )

    private data class Agendas(
        val diasReais: List<DiaReal>,
        val aplicacoes: List<AplicacaoVigente>,
        val tiposTurno: List<TipoTurno>,
        val ausencias: List<Ausencia>,
        val municipios: List<Municipio>
    )

    private data class Fontes(
        val cabecalho: Cabecalho,
        val catalogo: Catalogo,
        val agendas: Agendas
    )

    private val _anoMesAtual = MutableStateFlow(YearMonth.now())
    val anoMesAtual: StateFlow<YearMonth> = _anoMesAtual.asStateFlow()

    private val _edicoes = MutableStateFlow<Map<String, Map<Long, Edicao>>>(emptyMap())
    private val _mensagemErro = MutableStateFlow<String?>(null)
    private val _cabecalhoEditado = MutableStateFlow<Map<String, CabecalhoEditado>>(emptyMap())

    private val contrato = contratoRepository.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val parametros = parametrosCCTRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rubricasCatalogo = rubricaRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val diasReais = diaRealRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val aplicacoes = rotacaoRepository.observarAplicacoesVigentes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val tiposTurno = tipoTurnoRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val ausencias = ausenciaRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val municipios = municipioRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ReciboUiState> = combine(
        combine(_anoMesAtual, _edicoes, _mensagemErro, _cabecalhoEditado) { anoMes, edicoes, erro, cabecalhos ->
            Cabecalho(anoMes, edicoes, erro, cabecalhos[chaveDe(anoMes)])
        },
        combine(contrato, parametros, rubricasCatalogo) { c, p, r -> Catalogo(c, p, r) },
        combine(diasReais, aplicacoes, tiposTurno, ausencias, municipios) { d, a, t, au, mu ->
            Agendas(d, a, t, au, mu)
        }
    ) { cabecalho, catalogo, agendas ->
        Fontes(cabecalho, catalogo, agendas)
    }
        .flatMapLatest { fontes -> estadoDoMes(fontes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReciboUiState())

    /**
     * Os fluxos do mês escolhido: o recibo gravado, as linhas gravadas e os escalões de IRS
     * do ano. Vão por dentro do `flatMapLatest` porque dependem do mês selecionado.
     */
    private fun estadoDoMes(fontes: Fontes): Flow<ReciboUiState> {
        val anoMes = chaveDe(fontes.cabecalho.anoMes)
        return combine(
            reciboRepository.observarMes(anoMes),
            reciboRepository.observarLinhas(anoMes),
            tabelaIRSRepository.observarPorAno(fontes.cabecalho.anoMes.year)
        ) { mes, linhas, escaloes -> calcularEstado(fontes, mes, linhas, escaloes) }
    }

    /**
     * Monta o estado do ecrã: estima o mês, cruza com o que está gravado e com o que o
     * utilizador escreveu, e soma os totais. Não toca na base de dados.
     */
    private fun calcularEstado(
        fontes: Fontes,
        mesGuardado: ReciboMes?,
        linhasGuardadas: List<ReciboLinha>,
        escaloes: List<EscalaoIRS>
    ): ReciboUiState {
        val anoMes = chaveDe(fontes.cabecalho.anoMes)
        val formatado = formatarAnoMes(fontes.cabecalho.anoMes)
        val inicio = fontes.cabecalho.anoMes.atDay(1).toEpochDay()
        val contrato = fontes.catalogo.contrato
        val vigente = parametrosCCTRepository.vigentePara(fontes.catalogo.parametros, inicio)

        // Enquanto o catálogo não chegou (primeira emissão do stateIn), não desenhar nada.
        // Sem isto, a primeira emissão da combine corre com rubricas=0 e devolve estado vazio
        // válido, que a UI mostra como "sem rubricas" em vez de "a carregar".
        if (fontes.catalogo.rubricas.isEmpty() || contrato == null || vigente == null) {
            return ReciboUiState(
                anoMes = anoMes,
                anoMesFormatado = formatado,
                carregando = true
            )
        }

        // Feriado municipal (Fase 10): o contrato guarda o id do município, a tabela municipio
        // tem o dia/mês. Só depois de o catálogo estar carregado, para o contrato não ser nulo.
        val municipio = fontes.agendas.municipios.firstOrNull { it.id == contrato.municipioId }

        // Se a semente ainda não chegou (tabelas de IRS vazias), o motor atira — e um fluxo
        // que morre deixava o ecrã vazio para sempre. Fica uma mensagem e o fluxo sobrevive,
        // que assim recupera sozinho quando as tabelas entram.
        val estimativa = runCatching {
            estimarRecibo(
                ContextoEstimativa(
                    anoMes = fontes.cabecalho.anoMes,
                    contrato = contrato,
                    parametrosCCT = vigente,
                    rubricas = fontes.catalogo.rubricas,
                    diasReais = fontes.agendas.diasReais,
                    projecao = projetarIntervalo(
                        inicio,
                        fontes.cabecalho.anoMes.atEndOfMonth().toEpochDay(),
                        fontes.agendas.aplicacoes
                    ),
                    // Feriados: o motor calcula sozinho os nacionais do ano (fixos e móveis) e
                    // o municipal do contrato. Aqui só vão os que o utilizador marcar à mão
                    // (o tipo de turno FERIADO ainda não escreve para aqui: fica para depois).
                    feriados = emptySet(),
                    municipioFeriadoDia = municipio?.feriadoDia ?: 0,
                    municipioFeriadoMes = municipio?.feriadoMes ?: 0,
                    escaloesIRS = escaloes,
                    tiposTurno = fontes.agendas.tiposTurno,
                    ausencias = fontes.agendas.ausencias
                )
            )
        }.getOrElse {
            return ReciboUiState(
                anoMes = anoMes,
                anoMesFormatado = formatado,
                guardado = mesGuardado != null,
                mensagemErro = "Ainda a carregar os parâmetros e as tabelas de retenção."
            )
        }

        val estimadasPorId = estimativa.rubricas.associateBy { it.rubricaId }
        val guardadasPorId = linhasGuardadas.associateBy { it.rubricaId }
        val edicoesDoMes = fontes.cabecalho.edicoes[anoMes].orEmpty()

        val linhas = fontes.catalogo.rubricas.sortedBy { it.ordem }.map { rubrica ->
            val estimada = estimadasPorId[rubrica.id]
            val estimado = estimada?.valorMil ?: 0L
            val guardada = guardadasPorId[rubrica.id]
            val edicao = edicoesDoMes[rubrica.id]

            // Precedência: o que o utilizador escreveu > o que está gravado > o estimado.
            val valorReal = when {
                edicao?.valor != null -> edicao.valor
                edicao != null -> guardada?.valorReal ?: estimado
                guardada != null -> guardada.valorReal
                else -> estimado
            }

            LinhaConferencia(
                rubricaId = rubrica.id,
                codigo = rubrica.codigo,
                nome = rubrica.nome,
                natureza = estimada?.natureza ?: NaturezaRubrica.ABONO,
                ordem = rubrica.ordem,
                ativaConferencia = rubrica.ativaConferencia,
                valorEstimado = estimado,
                valorReal = valorReal,
                textoRealEditavel = edicao?.texto ?: textoDe(valorReal),
                textoInvalido = edicao != null && edicao.valor == null
            )
        }

        val abonosReal = linhas.filter { it.natureza == NaturezaRubrica.ABONO }.sumOf { it.valorReal }
        val descontosReal = linhas.filter { it.natureza == NaturezaRubrica.DESCONTO }.sumOf { it.valorReal }
        val temAlteracoes = linhas.any { linha ->
            val guardada = guardadasPorId[linha.rubricaId]
            if (guardada != null) linha.valorReal != guardada.valorReal
            else linha.valorReal != linha.valorEstimado
        }

        return ReciboUiState(
            anoMes = anoMes,
            anoMesFormatado = formatado,
            linhas = linhas,
            totalAbonosEstimado = estimativa.totalAbonos,
            totalAbonosReal = abonosReal,
            totalDescontosEstimado = estimativa.totalDescontos,
            totalDescontosReal = descontosReal,
            liquidoEstimado = estimativa.liquido,
            liquidoReal = abonosReal - descontosReal,
            guardado = mesGuardado != null,
            temAlteracoes = temAlteracoes,
            mensagemErro = fontes.cabecalho.mensagemErro,
            dataFechoTexto = textoDataFecho(fontes, mesGuardado),
            irsRetidoAnoTexto = textoIrsRetido(fontes, mesGuardado),
            nota = fontes.cabecalho.cabecalhoEditado?.nota ?: mesGuardado?.nota ?: ""
        )
    }

    /** Data de fecho: o que está escrito, senão o que está gravado, senão o fim do mês. */
    private fun textoDataFecho(fontes: Fontes, mesGuardado: ReciboMes?): String {
        fontes.cabecalho.cabecalhoEditado?.dataFechoTexto?.let { return it }
        val dia = mesGuardado?.dataFecho ?: fontes.cabecalho.anoMes.atEndOfMonth().toEpochDay()
        return LocalDate.ofEpochDay(dia).format(FORMATO_DATA)
    }

    private fun textoIrsRetido(fontes: Fontes, mesGuardado: ReciboMes?): String {
        fontes.cabecalho.cabecalhoEditado?.irsRetidoAnoTexto?.let { return it }
        return textoDe(mesGuardado?.irsRetidoAno ?: 0L)
    }

    // --- comandos ------------------------------------------------------------

    fun moverMes(delta: Int) {
        _anoMesAtual.update { it.plusMonths(delta.toLong()) }
    }

    fun irParaHoje() {
        _anoMesAtual.value = YearMonth.now()
    }

    /**
     * O utilizador escreveu num campo. Texto inválido mantém o último valor legível e o
     * texto tal como ficou (para o campo não saltar) e levanta a mensagem de erro.
     */
    fun atualizarValorReal(rubricaId: Long, texto: String) {
        val anoMes = chaveDe(_anoMesAtual.value)
        val novo = parseTexto(texto)
        _edicoes.update { actual ->
            val doMes = actual[anoMes].orEmpty()
            val anterior = doMes[rubricaId]
            actual + (anoMes to (doMes + (rubricaId to Edicao(texto, novo ?: anterior?.valor))))
        }
        _mensagemErro.value = if (novo == null) "Valor inválido: $texto" else null
    }

    /** Data de fecho (dd/MM/aaaa). Data inválida mantém o texto e avisa. */
    fun atualizarDataFecho(texto: String) {
        val anoMes = chaveDe(_anoMesAtual.value)
        _cabecalhoEditado.update { atual ->
            val doMes = atual[anoMes] ?: CabecalhoEditado()
            atual + (anoMes to doMes.copy(dataFechoTexto = texto))
        }
        _mensagemErro.value = if (parseData(texto) == null) "Data inválida: $texto" else null
    }

    /** IRS retido acumulado no ano, tal como vem no cabeçalho do recibo. */
    fun atualizarIrsRetidoAno(texto: String) {
        val anoMes = chaveDe(_anoMesAtual.value)
        _cabecalhoEditado.update { atual ->
            val doMes = atual[anoMes] ?: CabecalhoEditado()
            atual + (anoMes to doMes.copy(irsRetidoAnoTexto = texto))
        }
        _mensagemErro.value = if (parseTexto(texto) == null) "Valor inválido: $texto" else null
    }

    fun atualizarNota(texto: String) {
        val anoMes = chaveDe(_anoMesAtual.value)
        _cabecalhoEditado.update { atual ->
            val doMes = atual[anoMes] ?: CabecalhoEditado()
            atual + (anoMes to doMes.copy(nota = texto))
        }
        _mensagemErro.value = null
    }

    /** Grava o mês: cabeçalho + uma linha por rubrica. */
    fun guardar() {
        val estado = uiState.value
        if (estado.anoMes.isEmpty()) return
        viewModelScope.launch {
            val linhas = estado.linhas.map { linha ->
                ReciboLinha(
                    reciboMesId = estado.anoMes,
                    rubricaId = linha.rubricaId,
                    valorEstimado = linha.valorEstimado,
                    valorReal = linha.valorReal,
                    natureza = linha.natureza,
                    ordem = linha.ordem
                )
            }
            val abonos = linhas.filter { it.natureza == NaturezaRubrica.ABONO }.sumOf { it.valorReal }
            val descontos = linhas.filter { it.natureza == NaturezaRubrica.DESCONTO }.sumOf { it.valorReal }

            reciboRepository.guardar(cabecalhoDoMes(estado, abonos, descontos), linhas)
            // O que está gravado passa a ser a referência: as edições em memória deixam de
            // contar como alterações por guardar.
            _edicoes.update { actual -> actual - estado.anoMes }
            _cabecalhoEditado.update { actual -> actual - estado.anoMes }
            _mensagemErro.value = null
        }
    }

    fun apagar() {
        val anoMes = chaveDe(_anoMesAtual.value)
        viewModelScope.launch {
            reciboRepository.apagar(anoMes)
            _edicoes.update { actual -> actual - anoMes }
            _mensagemErro.value = null
        }
    }

    /**
     * Cabeçalho do recibo. O ecrã não pede estes campos, por isso o que se sabe deriva-se
     * (vencimento base = VENC real, valor/hora do CCT, dias úteis da projeção) e o resto
     * fica por preencher: data de fecho = hoje, IRS acumulado do ano = 0, nota = null.
     */
    private suspend fun cabecalhoDoMes(
        estado: ReciboUiState,
        abonos: Long,
        descontos: Long
    ): ReciboMes {
        val anoMes = YearMonth.parse(estado.anoMes)
        val inicio = anoMes.atDay(1).toEpochDay()
        val contrato = contratoRepository.obter()
        val vigente = parametrosCCTRepository.vigentePara(
            parametrosCCTRepository.observarTodos().first(),
            inicio
        )
        val categorias = tipoTurnoRepository.observarTodos().first()
            .associate { it.id to it.categoria }
        val projetados = projetarIntervalo(
            inicio,
            anoMes.atEndOfMonth().toEpochDay(),
            rotacaoRepository.obterAplicacoesVigentes()
        )
        val diasUteis = projetados.count { dia ->
            dia.tipoTurnoId?.let { categorias[it] } == CategoriaTurno.TRABALHO
        }

        val vencimentoHora = if (contrato != null && vigente != null) {
            valorHoraMil(
                ContextoCalculo(
                    vencimentoBaseMil = vigente.vencimentoBaseMil,
                    horarioSemanalH = vigente.horarioSemanalReferencia,
                    janelaNoturna = janelaNoturna(contrato.dataAdmissao ?: LocalDate.now().toEpochDay()),
                    diasUteisMes = diasUteis,
                    numDependentes = contrato.numeroDependentes
                )
            ).toLong()
        } else 0L

        return ReciboMes(
            anoMes = estado.anoMes,
            dataFecho = parseData(estado.dataFechoTexto) ?: LocalDate.now().toEpochDay(),
            vencimentoBase = estado.linhas.firstOrNull { it.codigo == "VENC" }?.valorReal ?: 0L,
            vencimentoHora = vencimentoHora,
            numDiasUteis = diasUteis,
            irsRetidoAno = parseTexto(estado.irsRetidoAnoTexto) ?: 0L,
            totalAbonos = abonos,
            totalDescontos = descontos,
            liquido = abonos - descontos,
            nota = estado.nota.ifBlank { null },
            dataCriacao = System.currentTimeMillis()
        )
    }

    class Factory(
        private val reciboRepository: ReciboRepository,
        private val contratoRepository: ContratoRepository,
        private val parametrosCCTRepository: ParametrosCCTRepository,
        private val rubricaRepository: RubricaRepository,
        private val diaRealRepository: DiaRealRepository,
        private val rotacaoRepository: RotacaoRepository,
        private val tipoTurnoRepository: TipoTurnoRepository,
        private val tabelaIRSRepository: TabelaIRSRepository,
        private val ausenciaRepository: AusenciaRepository,
        private val municipioRepository: MunicipioRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReciboViewModel(
                reciboRepository,
                contratoRepository,
                parametrosCCTRepository,
                rubricaRepository,
                diaRealRepository,
                rotacaoRepository,
                tipoTurnoRepository,
                tabelaIRSRepository,
                ausenciaRepository,
                municipioRepository
            ) as T
        }
    }
}

/** "2026-08" a partir do mês. */
private fun chaveDe(anoMes: YearMonth): String =
    "%04d-%02d".format(anoMes.year, anoMes.monthValue)

/** "Agosto 2026". */
private fun formatarAnoMes(anoMes: YearMonth): String {
    val locale = Locale("pt", "PT")
    val nome = anoMes.month.getDisplayName(TextStyle.FULL, locale)
    val capitalizado = nome.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
    }
    return "$capitalizado ${anoMes.year}"
}

/** Unidades -> texto do campo: "1 137,98" (sem o "€", que o rótulo já diz). */
private fun textoDe(valor: Long): String = valor.milParaEuros().removeSuffix(" €")

/**
 * Texto do recibo -> unidades de 1/10000 €. null se não for dinheiro válido.
 *
 * Usa o `deTexto` do domínio (exato, sem Double): aceita "1 137,98", "1137,98" e "14.36";
 * recusa mais de 4 casas decimais ou lixo.
 */
private fun parseTexto(texto: String): Long? =
    try { deTexto(texto).numerador } catch (_: Exception) { null }

/** Data no formato do recibo: dd/MM/aaaa. */
private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "31/08/2026" -> epochDay. null se não for data válida. */
private fun parseData(texto: String): Long? =
    try { LocalDate.parse(texto.trim(), FORMATO_DATA).toEpochDay() } catch (_: Exception) { null }
