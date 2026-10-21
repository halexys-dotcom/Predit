package pt.haconnect.predit.ui.horario

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.pdf.extrairBlocosPdf
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.EscaloesIRSDoAno
import pt.haconnect.predit.data.repository.MapeamentoRubricaRepository
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
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.calc.estimarRecibo
import pt.haconnect.predit.domain.calc.janelaNoturna
import pt.haconnect.predit.domain.calc.projetarIntervalo
import pt.haconnect.predit.domain.calc.temFeriadoNoMes
import pt.haconnect.predit.domain.calc.temFeriasNoMesOuSeguinte
import pt.haconnect.predit.domain.calc.valorHoraMil
import pt.haconnect.predit.domain.importacao.LinhaReciboImportada
import pt.haconnect.predit.domain.importacao.ReciboImportado
import pt.haconnect.predit.domain.importacao.ehRubricaDeDesconto
import pt.haconnect.predit.domain.importacao.fundirRecibos
import pt.haconnect.predit.domain.importacao.mapearCodigoRecibo
import pt.haconnect.predit.domain.importacao.parseReciboPdf
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.CATEGORIA_CCT_PADRAO
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
 * Rubricas de recurso: "Outros" (abonos, onde cai o R118) e "Outros" dos descontos ([RUBRICA_OUTROS_DESC],
 * onde cai o D05 da Fundação). As duas nascem a zero — só ganham valor quando o recibo traz um
 * código que o mapa não conhece ou quando o utilizador escreve um valor.
 */
private val CODIGOS_OUTROS = setOf("OUTROS", RUBRICA_OUTROS_DESC)

/**
 * Rubricas de feriado: horas suplementares em feriado (diurnas e noturnas), o dia feriado
 * trabalhado e o acréscimo de noite de feriado. Num mês sem feriados nenhuns todas caem a 0,00.
 */
private val CODIGOS_FERIADO = setOf("HSUP_DN_FER", "HSUP_NT_FER", "DESC_FER", "ACR_NOT_FER")

/** Subsídios com regra de calendário própria: o de Natal em dezembro, o de férias junto às férias. */
private const val CODIGO_SUBSIDIO_NATAL = "NATAL"
private const val CODIGO_SUBSIDIO_FERIAS = "FERIAS"

/** Dezembro é o mês do subsídio de Natal. */
private const val MES_DO_SUBSIDIO_NATAL = 12

/**
 * Subsídio de função ([mostrarSubsidioFuncao]): só as categorias que o recebem (hoje o Team
 * Leader, 52,46 €/mês — ver [pt.haconnect.predit.data.local.CATEGORIAS_CCT]) o têm no ecrã. Nas
 * restantes a linha estava sempre a 0,00.
 */
private const val CODIGO_SUBSIDIO_FUNCAO = "SUP_FUNCAO"

/** Código da categoria CCT que recebe o subsídio de função. */
private const val CATEGORIA_TEAM_LEADER = "TEAM_LEADER"

/** Desconto sindical ([mostrarSindicato]): o D04 só existe nos contratos sindicalizados. */
private const val CODIGO_SINDICATO = "D04"

/**
 * Ausências ([CODIGO_AUSENCIAS]): as horas em falta apuradas no Horário. A rubrica é INFORMATIVA —
 * o corte já foi feito dentro do VENC — e a 0,00 não justifica uma linha no ecrã.
 */
private const val CODIGO_AUSENCIAS = "ATRASO"

/**
 * 2026-10-21: as linhas que o ecrã de conferência desenha.
 *
 * - Rubricas de recurso ([CODIGOS_OUTROS]) só aparecem quando têm valor: a zero não dizem nada e
 *   só faziam crescer a lista.
 * - Rubricas de feriado ([CODIGOS_FERIADO]) só aparecem nos meses com feriado ([temFeriado], ver
 *   [temFeriadoNoMes]), a zero ou não: é o que o utilizador pediu para limpar o ecrã.
 * - O subsídio de Natal (v2.1.1) só aparece em dezembro ([mostrarNatal]).
 * - O subsídio de férias (v2.1.1) só aparece quando há férias no mês ou no seguinte
 *   ([mostrarFerias], ver [temFeriasNoMesOuSeguinte]).
 * - O subsídio de função (v2.1.1) só aparece nas categorias que o recebem — o Team Leader
 *   ([mostrarSubsidioFuncao]).
 * - O desconto sindical (v2.1.1, `D04`) só aparece nos contratos sindicalizados ([mostrarSindicato]).
 * - O complemento de baixa (v2.1.1, `COMP_BAIXA`) nunca se desenha a zero: não se estima, só existe
 *   quando o recibo do mês o traz — e aí quem o mostra é a rede de segurança.
 * - As ausências (v2.1.1, `ATRASO`) só aparecem com valor: são informativas — o corte já está feito
 *   dentro do VENC — e a 0,00 não dizem nada.
 * - Todas as outras aparecem sempre, mesmo a zero: são as linhas do recibo e é preciso vê-las
 *   para as conferir.
 *
 * Uma linha com valor — no Estimado ou no Real — nunca é escondida, seja de que rubrica for. É a
 * rede de segurança, e vem sempre primeiro: com o pagamento das variáveis desfasado (M paga as
 * variáveis de M-1) os valores de um mês podem aparecer noutro, o subsídio pode ter sido pago
 * fora do mês (ou importado do PDF), o D04 de um mês sindicalizado fica gravado depois de o
 * switch ser desligado, e uma edição à mão também pode pôr lá um valor.
 *
 * É um filtro de desenho, não de estado: [ReciboUiState.linhas] continua com o catálogo inteiro,
 * porque é dele que o "Guardar" escreve uma linha por rubrica e o modal de mapeamento tira as
 * escolhas. Consequência assumida: a zero a linha não tem campo onde escrever — um valor em
 * "Outros" (ou no complemento de baixa) entra pela importação do recibo, que escreve na mesma
 * rubrica, e um valor nas ausências vem do Horário ou da importação.
 */
internal fun linhasVisiveis(
    linhas: List<LinhaConferencia>,
    temFeriado: Boolean,
    mostrarNatal: Boolean,
    mostrarFerias: Boolean,
    mostrarSubsidioFuncao: Boolean,
    mostrarSindicato: Boolean
): List<LinhaConferencia> = linhas.filter { linha ->
    when {
        // 1. Rede de segurança, antes de qualquer regra.
        linha.valorEstimado != 0L || linha.valorReal != 0L -> true
        // 2. Rubricas de recurso: a zero não dizem nada.
        linha.codigo in CODIGOS_OUTROS -> false
        // 3. Feriados: só nos meses com feriado.
        linha.codigo in CODIGOS_FERIADO -> temFeriado
        // 4. Natal: só em dezembro.
        linha.codigo == CODIGO_SUBSIDIO_NATAL -> mostrarNatal
        // 5. Férias: só com férias no mês ou no seguinte.
        linha.codigo == CODIGO_SUBSIDIO_FERIAS -> mostrarFerias
        // 6. Subsídio de função: só nas categorias que o recebem.
        linha.codigo == CODIGO_SUBSIDIO_FUNCAO -> mostrarSubsidioFuncao
        // 7. Sindicato: só nos contratos sindicalizados.
        linha.codigo == CODIGO_SINDICATO -> mostrarSindicato
        // 8. Complemento de baixa: a zero não se desenha; com valor, a regra 1 já o mostrou.
        linha.codigo == CODIGO_COMPLEMENTO_BAIXA -> false
        // 9. Ausências: informativas e a zero não dizem nada; com valor, a regra 1 já as mostrou.
        linha.codigo == CODIGO_AUSENCIAS -> false
        else -> true
    }
}

/**
 * Um recibo detetado no PDF, para o seletor (Fase 27d). Sem códigos nem valores — só o mês
 * de referência e a data de fecho, quando o recibo a traz.
 */
data class InfoRecibo(
    val indice: Int,
    val anoMesFormatado: String,
    val dataFecho: Long?
)

/**
 * 28c: uma linha do recibo cujo código ainda não está mapeado, tal como o modal a mostra.
 *
 * [sugestao] é a heurística por descrição ([ReciboViewModel.sugerirRubrica]) — é uma ajuda,
 * e vem já como escolha inicial no modal (28g); o utilizador pode escolher outra rubrica.
 */
data class ItemMapeamento(
    val codigoOriginal: String,
    val descricao: String,
    val valor: Long,
    val linha: Int,
    val sugestao: String?  // codigo interno sugerido
)

/**
 * 28c: o recibo que ficou à espera de mapeamento. Enquanto isto existir, o recibo não foi
 * aplicado às edições do mês: as escolhas do utilizador têm de entrar antes.
 *
 * [reciboOriginal] é guardado inteiro (e não só os códigos) para a re-aplicação ser feita em
 * memória, sem voltar a ler o PDF.
 */
data class MapeamentoPendente(
    val reciboOriginal: ReciboImportado,
    val empresaEmissora: String?,
    val itens: List<ItemMapeamento>,
    /**
     * codigoOriginal -> codigoInterno. Arranca pré-preenchido com a heurística (28g) e cresce
     * com os cliques do utilizador; é o mapa que o "Confirmar" grava e re-aplica.
     */
    val escolhas: Map<String, String> = emptyMap()
)

/**
 * Estado do ecrã. Os campos levam valores por omissão (o enunciado só os punha em
 * `mensagemErro`) porque o `stateIn` precisa de um estado inicial antes de haver dados.
 */
data class ReciboUiState(
    val anoMes: String = "",
    val anoMesFormatado: String = "",
    val linhas: List<LinhaConferencia> = emptyList(),
    /**
     * 2026-10-21: o mês visível tem pelo menos um feriado (nacional ou o municipal do contrato).
     * Decide se as rubricas de feriado se desenham — ver [linhasVisiveis].
     */
    val temFeriado: Boolean = false,
    /**
     * v2.1.1: o mês é dezembro — mês do subsídio de Natal. Decide se a linha se desenha.
     */
    val mostrarSubsidioNatal: Boolean = false,
    /**
     * v2.1.1: há férias marcadas no mês visível ou no seguinte — [temFeriasNoMesOuSeguinte].
     * Decide se a linha do subsídio de férias se desenha.
     */
    val mostrarSubsidioFerias: Boolean = false,
    /**
     * v2.1.1: a categoria do contrato recebe subsídio de função — hoje só o Team Leader o tem
     * ([CATEGORIA_TEAM_LEADER]). Decide se a linha `SUP_FUNCAO` se desenha.
     */
    val mostrarSubsidioFuncao: Boolean = false,
    /**
     * v2.1.1: o contrato é sindicalizado (`ContratoUtilizador.sindicalizado`). Decide se o D04 se
     * desenha — desligado, o estimador põe-no a 0 e a linha só fazia crescer a lista.
     */
    val mostrarSindicato: Boolean = false,
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
    val nota: String = "",
    /** 27d: o seletor de recibos está aberto (o PDF trazia mais do que um recibo). */
    val seletorDeReciboAberto: Boolean = false,
    /** 27d: os recibos a escolher, quando o seletor está aberto. */
    val recibosParaEscolher: List<InfoRecibo> = emptyList(),
    /** 27d: aviso curto depois de importar (ex.: rubricas sem correspondência na app). */
    val mensagemImportacao: String? = null,
    /**
     * 28j: os avisos que o parser produziu ao ler o PDF (baixa total, cabeçalho ilegível, linha
     * "Total" partida...). Ficam em lista própria, e não colados à [mensagemImportacao], porque
     * são vários e a UI mostra-os em bloco; sem eles o utilizador não sabia que um mês de baixa
     * tinha sido importado com o vencimento posto a zero por regra.
     */
    val avisosImportacao: List<String> = emptyList(),
    /**
     * 28c: códigos do recibo importado que nenhum mapa conhece. Enquanto não for nulo, o
     * recibo ainda não foi aplicado — está à espera das escolhas do utilizador.
     */
    val mapeamentoPendente: MapeamentoPendente? = null,
    /**
     * A1: o ano do mês não tem tabelas de retenção e o IRS foi estimado com as do último ano
     * carregado. É aviso, não erro: o recibo estima-se na mesma, mas o D02 pode não bater.
     */
    val avisoTabelasIRS: String? = null,
    /**
     * A3: o recibo importado não traz mês reconhecível. Enquanto for verdade, o ecrã pergunta se
     * é para aplicar ao mês visível — o recibo ainda não foi aplicado.
     */
    val confirmarReciboSemMes: Boolean = false
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
    private val municipioRepository: MunicipioRepository,
    private val mapeamentoRepository: MapeamentoRubricaRepository
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

    /**
     * 28j: os sinais da importação que não dependem do mês, agrupados. O `combine` só tem
     * sobrecargas tipadas até 5 fluxos: agrupados, o `uiState` continua a ter uma lambda de
     * tipos explícitos em vez do `Array<Any?>` da versão vararg.
     */
    private data class ImportacaoUi(
        val recibosParaEscolher: List<InfoRecibo>,
        val mensagemImportacao: String?,
        val avisosImportacao: List<String>
    )

    private val _anoMesAtual = MutableStateFlow(YearMonth.now())
    val anoMesAtual: StateFlow<YearMonth> = _anoMesAtual.asStateFlow()

    private val _edicoes = MutableStateFlow<Map<String, Map<Long, Edicao>>>(emptyMap())
    private val _mensagemErro = MutableStateFlow<String?>(null)
    private val _cabecalhoEditado = MutableStateFlow<Map<String, CabecalhoEditado>>(emptyMap())

    // Fase 27d: estado só de UI da importação do recibo. Fica fora do `estadoDoMes` porque
    // não depende do mês nem da base de dados, e assim não há um `_uiState` mutável paralelo.
    private val _seletorDeReciboAberto = MutableStateFlow(false)
    private val _recibosParaEscolher = MutableStateFlow<List<InfoRecibo>>(emptyList())
    private val _mensagemImportacao = MutableStateFlow<String?>(null)
    // 28j: avisos do parser sobre o recibo lido (baixa total, cabeçalho ilegível...). Vivem ao
    // lado da mensagem porque são vários e a UI mostra-os por baixo dela.
    private val _avisosImportacao = MutableStateFlow<List<String>>(emptyList())
    private val _recibosImportados = MutableStateFlow<List<ReciboImportado>>(emptyList())

    // 28c: enquanto não for nulo, o recibo importado tem códigos por mapear e ainda não foi
    // aplicado — primeiro escolhem-se os destinos, só depois `aplicarReciboImportado` corre.
    private val _mapeamentoPendente = MutableStateFlow<MapeamentoPendente?>(null)

    // A3: o recibo que chegou sem mês reconhecível, à espera de o utilizador confirmar que é do
    // mês visível. Enquanto não for nulo, ainda não foi aplicado.
    private val _reciboSemMesPendente = MutableStateFlow<ReciboImportado?>(null)

    private val contrato = contratoRepository.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val parametros = parametrosCCTRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rubricasCatalogo = rubricaRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val diasReais = diaRealRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val aplicacoes = rotacaoRepository.observarTodasAplicacoes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val tiposTurno = tipoTurnoRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val ausencias = ausenciaRepository.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val municipios = municipioRepository.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** O estado do mês: estimado vs real e totais. É a base de tudo o que a UI mostra. */
    private val estadoDoEcra: Flow<ReciboUiState> = combine(
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

    /**
     * 27d: o estado calculado com os campos só de UI da importação sobrepostos. Ficam fora do
     * `calcularEstado` porque não dependem do mês — e assim não há um `_uiState` mutável
     * paralelo a competir com o fluxo derivado.
     */
    val uiState: StateFlow<ReciboUiState> = combine(
        estadoDoEcra,
        _seletorDeReciboAberto,
        combine(_recibosParaEscolher, _mensagemImportacao, _avisosImportacao) { recibos, mensagem, avisos ->
            ImportacaoUi(recibos, mensagem, avisos)
        },
        _mapeamentoPendente,
        _reciboSemMesPendente
    ) { base, seletorAberto, importacao, mapeamentoPendente, reciboSemMes ->
        base.copy(
            seletorDeReciboAberto = seletorAberto,
            recibosParaEscolher = importacao.recibosParaEscolher,
            mensagemImportacao = importacao.mensagemImportacao,
            avisosImportacao = importacao.avisosImportacao,
            mapeamentoPendente = mapeamentoPendente,
            confirmarReciboSemMes = reciboSemMes != null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReciboUiState())

    /**
     * Os fluxos do mês escolhido: o recibo gravado, as linhas gravadas e os escalões de IRS
     * do ano. Vão por dentro do `flatMapLatest` porque dependem do mês selecionado.
     *
     * A1: os escalões vêm com recurso — sem tabelas para o ano do mês (2027 antes de a app as
     * trazer), chegam os do último ano carregado, e o estado leva o aviso.
     */
    private fun estadoDoMes(fontes: Fontes): Flow<ReciboUiState> {
        val anoMes = chaveDe(fontes.cabecalho.anoMes)
        return combine(
            reciboRepository.observarMes(anoMes),
            reciboRepository.observarLinhas(anoMes),
            tabelaIRSRepository.observarComRecurso(fontes.cabecalho.anoMes.year)
        ) { mes, linhas, tabelas -> calcularEstado(fontes, mes, linhas, tabelas) }
    }

    /**
     * Monta o estado do ecrã: estima o mês, cruza com o que está gravado e com o que o
     * utilizador escreveu, e soma os totais. Não toca na base de dados.
     */
    private fun calcularEstado(
        fontes: Fontes,
        mesGuardado: ReciboMes?,
        linhasGuardadas: List<ReciboLinha>,
        tabelasIRS: EscaloesIRSDoAno
    ): ReciboUiState {
        val escaloes = tabelasIRS.escaloes
        val anoMes = chaveDe(fontes.cabecalho.anoMes)
        val formatado = formatarAnoMes(fontes.cabecalho.anoMes)
        val inicio = fontes.cabecalho.anoMes.atDay(1).toEpochDay()
        val contrato = fontes.catalogo.contrato
        // 13a: a tabela salarial é a da categoria do contrato; sem categoria (ou sem tabela para
        // o ano do mês) cai na APAA, que é a categoria de referência da app.
        val vigente = parametrosCCTRepository.paraCategoria(
            fontes.catalogo.parametros,
            contrato?.categoriaCodigo ?: CATEGORIA_CCT_PADRAO,
            inicio
        )

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

        // 2026-10-21: feriados do mês visível (nacionais fixos e móveis + o municipal do contrato).
        // É a mesma conta que o motor faz para as rubricas de feriado, aqui só para a UI decidir se
        // as desenha: num mês sem feriado nenhum as quatro linhas ficavam todas a 0,00.
        val temFeriado = temFeriadoNoMes(
            fontes.cabecalho.anoMes,
            municipio?.feriadoDia ?: 0,
            municipio?.feriadoMes ?: 0
        )

        // v2.1.1: os subsídios só se desenham quando fazem sentido no mês.
        // - Natal: em dezembro, o mês em que se paga.
        // - Férias: quando há férias marcadas no mês ou no seguinte. Quem marca férias de 10 a 24
        //   de agosto quer o subsídio à vista em julho, para o prever, e continua a querê-lo em
        //   agosto, o mês em que as goza.
        val mostrarNatal = fontes.cabecalho.anoMes.monthValue == MES_DO_SUBSIDIO_NATAL
        val mostrarFerias = temFeriasNoMesOuSeguinte(
            fontes.agendas.ausencias,
            fontes.agendas.tiposTurno.associate { it.id to it.categoria },
            fontes.cabecalho.anoMes
        )

        // v2.1.1: o subsídio de função e o desconto sindical dependem do contrato, não do mês.
        // - Subsídio de função: só a categoria que o recebe (Team Leader, 52,46 €/mês — ver
        //   CATEGORIAS_CCT) o tem no recibo; nas outras a linha estava sempre a 0,00.
        // - Sindicato: o D04 é 1 % do VENC e só existe se o contrato for sindicalizado. Desligado,
        //   o estimador põe-no a 0 e a linha desaparecia do ecrã na mesma, mas continuava a pesar.
        val mostrarSubsidioFuncao = contrato.categoriaCodigo
            .contains(CATEGORIA_TEAM_LEADER, ignoreCase = true)
        val mostrarSindicato = contrato.sindicalizado

        // Complemento de baixa (COMP_BAIXA, R51): não se estima — é o valor que o recibo do mês
        // traz (importado do PDF ou escrito à mão). Sem isto as bases de incidência ignoravam o
        // complemento e o D01/D02 estimados não batiam com o recibo real de maio/2026, que
        // desconta 31,51 € de SS sobre os 286,41 € do complemento.
        val edicoesDoMes = fontes.cabecalho.edicoes[anoMes].orEmpty()
        val complementoBaixaMil = complementoBaixaDoMes(
            fontes.catalogo.rubricas,
            edicoesDoMes,
            linhasGuardadas
        )

        // Se a semente ainda não chegou (tabelas de IRS vazias), o motor atira — e um fluxo
        // que morre deixava o ecrã vazio para sempre. Fica uma mensagem e o fluxo sobrevive,
        // que assim recupera sozinho quando as tabelas entram. A1: a falta de tabelas para o
        // ano já não chega aqui (vem o recurso ao último ano); o que sobra é a semente por
        // chegar ou um erro a sério, e cada um tem a sua mensagem.
        val desfasamento = contrato.desfasamentoPagamentoVariaveis
        // O motor lê a projeção do mês das variáveis (M com desfasamento 0, M-1 com 1).
        // Constrói-se o intervalo que cobre os dois meses: com desfasamento 0 cobre só M;
        // com 1 cobre M-1 e M. O motor filtra pelo mês certo.
        val inicioVariaveis = fontes.cabecalho.anoMes.minusMonths(desfasamento.toLong()).atDay(1).toEpochDay()
        val inicioProjecao = minOf(inicio, inicioVariaveis)
        val fimProjecao = fontes.cabecalho.anoMes.atEndOfMonth().toEpochDay()
        val estimativa = try {
            estimarRecibo(
                ContextoEstimativa(
                    anoMes = fontes.cabecalho.anoMes,
                    contrato = contrato,
                    parametrosCCT = vigente,
                    rubricas = fontes.catalogo.rubricas,
                    diasReais = fontes.agendas.diasReais,
                    projecao = projetarIntervalo(
                        inicioProjecao,
                        fimProjecao,
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
                    ausencias = fontes.agendas.ausencias,
                    desfasamentoPagamentoVariaveis = desfasamento,
                    complementoBaixaMil = complementoBaixaMil
                )
            )
        } catch (e: CancellationException) {
            // O cancelamento do fluxo (mudar de mês, sair do ecrã) não é erro do cálculo.
            throw e
        } catch (e: Exception) {
            return ReciboUiState(
                anoMes = anoMes,
                anoMesFormatado = formatado,
                guardado = mesGuardado != null,
                mensagemErro = if (escaloes.isEmpty()) {
                    "Ainda a carregar os parâmetros e as tabelas de retenção."
                } else {
                    "Não foi possível estimar o recibo: ${e.message ?: "erro desconhecido"}"
                }
            )
        }
        val avisoTabelasIRS = tabelasIRS.anoDasTabelas
            ?.takeIf { it != fontes.cabecalho.anoMes.year }
            ?.let { ano ->
                "Sem tabelas de retenção de IRS para ${fontes.cabecalho.anoMes.year}: o IRS foi " +
                    "estimado com as tabelas de $ano."
            }

        val estimadasPorId = estimativa.rubricas.associateBy { it.rubricaId }
        val guardadasPorId = linhasGuardadas.associateBy { it.rubricaId }

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
            temFeriado = temFeriado,
            mostrarSubsidioNatal = mostrarNatal,
            mostrarSubsidioFerias = mostrarFerias,
            mostrarSubsidioFuncao = mostrarSubsidioFuncao,
            mostrarSindicato = mostrarSindicato,
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
            nota = fontes.cabecalho.cabecalhoEditado?.nota ?: mesGuardado?.nota ?: "",
            avisoTabelasIRS = avisoTabelasIRS
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

    /**
     * Complemento de baixa (COMP_BAIXA, R51) do mês, em 1/10000 €. Não se estima: vem do recibo
     * — o mesmo valor que o ecrã mostra como Real, com a mesma precedência (edição à mão/importação
     * > gravado). Sem recibo do mês devolve 0, que é o caso de quem ainda não importou nada.
     */
    private fun complementoBaixaDoMes(
        rubricas: List<Rubrica>,
        edicoes: Map<Long, Edicao>,
        linhasGuardadas: List<ReciboLinha>
    ): Long {
        val rubrica = rubricas.firstOrNull { it.codigo == CODIGO_COMPLEMENTO_BAIXA } ?: return 0L
        val guardada = linhasGuardadas.firstOrNull { it.rubricaId == rubrica.id }
        return edicoes[rubrica.id]?.valor ?: guardada?.valorReal ?: 0L
    }

    /**
     * 28c: um recibo com códigos que nenhum mapa conhece não é aplicado já — fica pendente
     * para o utilizador dizer, no modal, onde cada código vai. Sem desconhecidos, o caminho é
     * o de sempre.
     */
    private fun processarReciboUnico(recibo: ReciboImportado, mesConfirmado: Boolean = false) {
        // A3: o recibo só se aplica ao mês a que pertence. Os valores vão para o mês visível, por
        // isso um PDF de agosto aberto em setembro escrevia (e gravava) setembro.
        val mesDoRecibo = recibo.mesReferencia
        val mesVisivel = _anoMesAtual.value
        if (mesDoRecibo == null && !mesConfirmado) {
            // Sem mês legível não há com que comparar: pergunta-se antes de aplicar.
            _reciboSemMesPendente.value = recibo
            return
        }
        if (mesDoRecibo != null && mesDoRecibo != mesVisivel) {
            _mensagemErro.value = "O recibo é de ${formatarAnoMes(mesDoRecibo)}, mas estás no mês " +
                "${formatarAnoMes(mesVisivel)}. Navega para o mês correto antes de importar."
            _mensagemImportacao.value = null
            _avisosImportacao.value = emptyList()
            return
        }

        // C7: além dos códigos desconhecidos, vão ao modal as linhas de desconto que estejam
        // mapeadas para uma rubrica de abono (um mapeamento antigo do utilizador): sem isto o
        // recibo ficava bloqueado sem forma de corrigir o mapa.
        val aMapear = (
            recibo.codigosDesconhecidos +
                recibo.linhas.filter { descontoSemRubricaDeDesconto(it) }.map { it.codigoRecibo }
            ).distinct()
        if (aMapear.isEmpty()) {
            aplicarReciboImportado(recibo)
            return
        }

        // Construir a lista de itens para o modal
        val itens = recibo.linhas
            .filter { it.codigoRecibo in aMapear }
            .map { linha ->
                // A descrição vem do PDF partida em vários blocos ("Baixa Assist" + ". " +
                // "Familia" + "<12 Anos"): no modal tem de ficar numa linha só, senão o
                // `Text` parte-a outra vez em sítios arbitrários. A heurística da sugestão
                // lê a mesma versão limpa.
                val descricao = linha.descricao
                    .replace("\n", " ")
                    .replace(REGEX_ESPACOS, " ")
                    .trim()
                ItemMapeamento(
                    codigoOriginal = linha.codigoRecibo,
                    descricao = descricao.ifEmpty { "Linha ${linha.ordem}" },
                    valor = linha.valor,
                    linha = linha.ordem,
                    sugestao = sugerirRubrica(descricao, linha.natureza)
                )
            }

        // 28g: as sugestões da heurística entram já como escolhas iniciais. Antes só apareciam
        // no dropdown e o "Confirmar" ficava desativado até o utilizador abrir cada um — sem
        // nada a explicar porquê. O utilizador continua a poder trocar; o que a heurística não
        // soube sugerir fica por escolher, e aí o "Confirmar" espera.
        val escolhasIniciais = itens.mapNotNull { item ->
            item.sugestao?.let { item.codigoOriginal to it }
        }.toMap()

        _mapeamentoPendente.value = MapeamentoPendente(
            reciboOriginal = recibo,
            empresaEmissora = recibo.empresaEmissora,
            itens = itens,
            escolhas = escolhasIniciais
        )
    }

    /**
     * Heurística pela descrição da linha ("Subsídio de Alimentação" -> SUP_ALIM). É só uma
     * sugestão pré-selecionada no modal: quem decide é o utilizador.
     *
     * C7: a sugestão tem de ser da mesma natureza da linha. "Desconto Subsídio Alimentação" (D05
     * da Fundação) tem "alimenta" e sugeria SUP_ALIM — um abono, que somava o desconto ao líquido.
     */
    private fun sugerirRubrica(descricao: String, natureza: NaturezaRubrica): String? {
        val d = descricao.lowercase()
        val sugestao = when {
            "alimenta" in d -> "SUP_ALIM"
            "transp" in d -> "SUP_TRAN"
            "vencimento" in d || "salário" in d || "salario" in d -> "VENC"
            "férias" in d || "ferias" in d -> "FERIAS"
            "natal" in d -> "NATAL"
            "irs" in d -> "D02"
            "segurança social" in d || "seguranca social" in d -> "D01"
            "sindicato" in d -> "D04"
            else -> null
        }
        return sugestao?.takeIf { ehRubricaDeDesconto(it) == (natureza == NaturezaRubrica.DESCONTO) }
    }

    /** C7: linha que o recibo dá como desconto, mas sem rubrica de desconto para a receber. */
    private fun descontoSemRubricaDeDesconto(linha: LinhaReciboImportada): Boolean =
        linha.natureza == NaturezaRubrica.DESCONTO &&
            linha.codigoInterno?.let { ehRubricaDeDesconto(it) } != true

    /** "Desconto desconhecido: D05. Configura a rubrica antes de importar." */
    private fun mensagemDescontoDesconhecido(codigos: List<String>): String =
        "Desconto desconhecido: ${codigos.distinct().joinToString(", ")}. " +
            "Configura a rubrica antes de importar."

    /**
     * 27d: aplica um recibo importado às edições do mês atual. Cada linha com `codigoInterno`
     * não-nulo é mapeada para a rubrica correspondente do catálogo; as linhas sem código interno
     * — as informativas (baixa, férias gozadas, ausência parcial, subsídio durante a baixa) e as
     * de código desconhecido — não escrevem valor. Duas linhas do recibo que caiam na mesma
     * rubrica somam-se em vez de se substituírem (ex.: RCR + RCRA → SUP_ALIM), que é o que o
     * recibo declara. A importação substitui o que estava — foi o utilizador que a pediu.
     */
    private fun aplicarReciboImportado(recibo: ReciboImportado) {
        // Regra de negócio (2026-10-16): a app mostra 2 valores — Estimado (calculado pela app) e
        // Real (o que a empresa pagou, via manual ou PDF). Se divergem, a app está a sinalizar um
        // erro. "Recibo declara" não é mostrado separadamente porque coincide com "Real" sempre
        // que a importação está correta — as informativas não escrevem valor e duas linhas da
        // mesma rubrica somam-se (RCR + RCRA), que é o que o recibo declara.
        // Classificação das linhas do recibo (confirmada a 2026-10-16 com 5 recibos reais e os
        // fixtures): as informativas — baixas (F01/F02), subsídio durante a baixa (F40), férias
        // gozadas (F50), ausência parcial (F74) — vêm do parser sem código interno e não escrevem
        // valor; as de "Outros" (R118 média de horas noturnas) são pagamentos reais, trazem
        // Importância no recibo e escrevem. O R51 (complemento de baixa) saiu de "Outros" na 28i:
        // tem rubrica própria (COMP_BAIXA), que incide SS e IRS, e escreve lá.
        val anoMes = chaveDe(_anoMesAtual.value)
        val catalogo = rubricasCatalogo.value.associateBy { it.codigo }
        val naoReconhecidas = mutableListOf<String>()
        val descontosSemRubrica = mutableListOf<String>()
        val avisosDesconto = mutableListOf<String>()

        // A soma é local a esta importação: o que estava no mês antes (edição à mão ou
        // importação anterior) é substituído, para reimportar o mesmo PDF dar o mesmo e não
        // o dobro. Ver somarLinhasPorRubrica para a soma das linhas repetidas (RCR + RCRA).
        val somados = somarLinhasPorRubrica(
            recibo.linhas, catalogo, naoReconhecidas, descontosSemRubrica, avisosDesconto
        )

        // C7: um desconto vai sempre para uma rubrica de desconto — a dele ou, quando não há,
        // OUTROS_DESC (ver somarLinhasPorRubrica). O abort fica para a BD que ainda não tem essa
        // rubrica: aí não há onde escrever o valor sem o somar ao líquido em vez de o tirar.
        if (descontosSemRubrica.isNotEmpty()) {
            _mensagemErro.value = mensagemDescontoDesconhecido(descontosSemRubrica)
            _mensagemImportacao.value = null
            _avisosImportacao.value = emptyList()
            return
        }

        _edicoes.update { atual ->
            val doMes = atual[anoMes].orEmpty().toMutableMap()
            for ((rubricaId, valorFinal) in somados) {
                doMes[rubricaId] = Edicao(textoDe(valorFinal), valorFinal)
            }
            atual + (anoMes to doMes)
        }

        val mes = recibo.mesReferencia?.let { formatarAnoMes(it) } ?: "mês desconhecido"
        // Fase 28b: a empresa vem do rodapé e é o que identifica de onde é o recibo quando o
        // utilizador tem mais do que uma.
        val empresa = recibo.empresaEmissora?.let { " ($it)" } ?: ""
        _mensagemImportacao.value = if (naoReconhecidas.isEmpty()) {
            "Recibo de $mes$empresa importado."
        } else {
            "Recibo de $mes$empresa importado. ${naoReconhecidas.size} rubrica(s) sem correspondência."
        }
        _mensagemErro.value = null
        // 28j: os avisos do parser sobem à UI (é por eles que o utilizador percebe porque é que
        // num mês de baixa total o VENC ficou a 0,00 e o mês fecha pelo complemento R51). Os
        // avisos de desconto (OUTROS_DESC) juntam-se-lhes, sem repetir o mesmo texto.
        _avisosImportacao.value = (recibo.avisos + avisosDesconto).distinct()
    }

    // --- comandos ------------------------------------------------------------

    fun moverMes(delta: Int) {
        _anoMesAtual.update { it.plusMonths(delta.toLong()) }
    }

    fun irParaHoje() {
        _anoMesAtual.value = YearMonth.now()
    }

    // --- 27d: importação do recibo em PDF ------------------------------------

    /**
     * Importa um recibo em PDF. Extrai os blocos com posição, faz o parse e, se houver um só
     * recibo, aplica-o de imediato; se houver vários, abre o seletor (o parser não adivinha).
     */
    fun importarRecibo(uri: Uri, context: Context) {
        viewModelScope.launch {
            _mensagemImportacao.value = null
            // 28j: sem isto, um recibo importado a seguir a outro arrastava os avisos do anterior.
            _avisosImportacao.value = emptyList()
            val blocos = extrairBlocosPdf(uri, context)
            if (blocos.isEmpty()) {
                _mensagemErro.value = "Não foi possível ler o PDF."
                return@launch
            }
            // Fase 28b: o mapa configurável do utilizador (empresa do rodapé -> códigos) vai
            // para o parser; sem ele só o hardcoded se aplica.
            val mapeamentos = mapeamentoRepository.obterTodos()
            val recibos = parseReciboPdf(blocos, mapeamentos)
            if (recibos.isEmpty()) {
                _mensagemErro.value = "Não encontrei nenhum recibo no PDF."
                return@launch
            }
            // 28f: fundir as folhas duplicadas do mesmo recibo (ex: Original + Duplicado)
            val recibosFundidos = fundirRecibos(recibos)

            if (recibosFundidos.size == 1) {
                processarReciboUnico(recibosFundidos.first())
            } else {
                _recibosImportados.value = recibosFundidos
                _recibosParaEscolher.value = recibosFundidos.mapIndexed { indice, recibo ->
                    InfoRecibo(
                        indice = indice,
                        anoMesFormatado = recibo.mesReferencia?.let { formatarAnoMes(it) }
                            ?: "Recibo ${indice + 1}",
                        dataFecho = recibo.dataFecho
                    )
                }
                _seletorDeReciboAberto.value = true
            }
        }
    }

    /** O utilizador escolheu um dos recibos do PDF. */
    fun escolherRecibo(indice: Int) {
        val escolhido = _recibosImportados.value.getOrNull(indice) ?: return
        _recibosImportados.value = emptyList()
        _recibosParaEscolher.value = emptyList()
        _seletorDeReciboAberto.value = false
        processarReciboUnico(escolhido)
    }

    /** O utilizador fechou o seletor sem escolher. */
    fun cancelarSeletor() {
        _recibosImportados.value = emptyList()
        _recibosParaEscolher.value = emptyList()
        _seletorDeReciboAberto.value = false
    }

    /** A3: o recibo sem mês legível é mesmo do mês visível — segue o caminho de sempre. */
    fun confirmarReciboSemMes() {
        val recibo = _reciboSemMesPendente.value ?: return
        _reciboSemMesPendente.value = null
        processarReciboUnico(recibo, mesConfirmado = true)
    }

    /** A3: o utilizador não confirmou o mês — o recibo não é aplicado. */
    fun cancelarReciboSemMes() {
        _reciboSemMesPendente.value = null
        _mensagemImportacao.value = "Importação cancelada."
        _avisosImportacao.value = emptyList()
    }

    /**
     * 28c: o utilizador escolheu a rubrica de um código do recibo.
     */
    fun atualizarEscolhaMapeamento(codigoOriginal: String, codigoInterno: String) {
        _mapeamentoPendente.update { pendente ->
            pendente?.copy(escolhas = pendente.escolhas + (codigoOriginal to codigoInterno))
        }
    }

    /** 28c: o utilizador desistiu — o recibo pendente não chega a ser aplicado. */
    fun cancelarMapeamento() {
        _mapeamentoPendente.value = null
        _mensagemImportacao.value = "Importação cancelada."
        // 28j: o recibo não chegou a ser aplicado — os avisos do parser também não ficam no ecrã.
        _avisosImportacao.value = emptyList()
    }

    /**
     * 28c: grava as escolhas (com a chave normalizada, para casarem com a leitura do parser) e
     * re-aplica o recibo em memória com os códigos já traduzidos — sem voltar a ler o PDF.
     */
    fun confirmarMapeamento() {
        val pendente = _mapeamentoPendente.value ?: return
        viewModelScope.launch {
            // 1. Traduzir as linhas com as escolhas (em memoria, sem voltar a ler o PDF)
            val novasLinhas = pendente.reciboOriginal.linhas.map { linha ->
                val novoCodigo = pendente.escolhas[linha.codigoRecibo] ?: linha.codigoInterno
                linha.copy(codigoInterno = novoCodigo)
            }
            val reciboCorrigido = pendente.reciboOriginal.copy(linhas = novasLinhas)

            // 2. C7: um desconto escolhido para uma rubrica de abono aborta antes de gravar —
            //    senão o mapa errado ficava na BD e voltava em todas as importações seguintes.
            val descontosInvalidos = novasLinhas
                .filter { descontoSemRubricaDeDesconto(it) }
                .map { it.codigoRecibo }
            if (descontosInvalidos.isNotEmpty()) {
                _mapeamentoPendente.value = null
                _mensagemErro.value = mensagemDescontoDesconhecido(descontosInvalidos)
                _mensagemImportacao.value = null
                _avisosImportacao.value = emptyList()
                return@launch
            }

            // 3. Gravar na BD
            val empresa = pendente.empresaEmissora
            if (empresa != null && pendente.escolhas.isNotEmpty()) {
                // Uma transacao (ver MapeamentoRubricaRepository.guardarNormalizados): uma
                // falha a meio nao deixa mapeamentos parciais na BD.
                mapeamentoRepository.guardarNormalizados(empresa, pendente.escolhas)
            }

            // 4. Aplicar e limpar
            aplicarReciboImportado(reciboCorrigido)
            _mapeamentoPendente.value = null
        }
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
        val vigente = parametrosCCTRepository.paraCategoria(
            parametrosCCTRepository.observarTodos().first(),
            contrato?.categoriaCodigo ?: CATEGORIA_CCT_PADRAO,
            inicio
        )
        val categorias = tipoTurnoRepository.observarTodos().first()
            .associate { it.id to it.categoria }
        val projetados = projetarIntervalo(
            inicio,
            anoMes.atEndOfMonth().toEpochDay(),
            rotacaoRepository.obterTodasAplicacoes()
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
        private val municipioRepository: MunicipioRepository,
        private val mapeamentoRepository: MapeamentoRubricaRepository
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
                municipioRepository,
                mapeamentoRepository
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

/** Rubrica de recurso para os descontos que o mapa não sabe onde pôr (ver somarLinhasPorRubrica). */
internal const val RUBRICA_OUTROS_DESC = "OUTROS_DESC"

/**
 * Complemento de baixa (R51). Não se estima — o valor vem do recibo —, mas entra nas bases de
 * incidência do estimador (ver [complementoBaixaDoMes] e [ContextoEstimativa.complementoBaixaMil]).
 */
private const val CODIGO_COMPLEMENTO_BAIXA = "COMP_BAIXA"

/**
 * Aviso que acompanha um desconto lançado na rubrica de recurso. Diz "(desconto)" porque desde
 * 2026-10-21 a rubrica se chama só "Outros", como a dos abonos — sem isto o aviso não distinguia
 * para qual das duas o valor foi.
 */
private const val AVISO_DESCONTO_OUTROS = "Desconto desconhecido foi para Outros (desconto)"

/**
 * Soma as linhas de um recibo importado por rubrica do catálogo, pela ordem em que vêm no PDF.
 *
 * Dois códigos do recibo podem cair na mesma rubrica, e aí o valor dela é a **soma** das linhas,
 * nunca a última delas:
 * - Março 2026: `RCR` +172,70 € logo seguido de `RCRA` −172,70 € (o acerto) — as duas linhas
 *   estão no PDF e anulam-se; somar dá 0,00, que é o que o recibo declara no subsídio de
 *   alimentação. Substituir dava −172,70 €.
 * - Agosto 2025: `RCR` 163,24 € e `RCRA` 7,42 € somam os 170,66 € que entram no total de abonos
 *   desse recibo.
 *
 * As linhas informativas (baixa, férias gozadas, ausência parcial, subsídio durante a baixa) vêm
 * do parser sem `codigoInterno` e são ignoradas — nunca escrevem valor, é o que já era o destino
 * delas. Os códigos que não existem no catálogo ficam em [naoReconhecidas] para a app avisar.
 *
 * A excepção é a linha que o **mapeamento** manda para uma rubrica mas o parser marcou de
 * informativa: aí a rubrica é declarada pelo recibo e não paga (28i: o `R01` num mês de baixa
 * total, que só serve de referência ao cálculo do complemento `R51`). Escreve-se 0, para também
 * limpar o que tivesse ficado de uma importação anterior — sem isso, reimportar Março depois da
 * 28i deixava lá os 1 137,98 € que ninguém recebeu e os Totais Reais não fechavam nos 258,70 €
 * que o recibo declara.
 *
 * 2026-10-21: um desconto nunca fica de fora nem entra numa rubrica de abono. Se não houver
 * rubrica de desconto para o receber (a dele não existe no catálogo, ou o mapa manda-o para um
 * abono), vai para `OUTROS_DESC` e a UI avisa — antes a importação abortava e o recibo da
 * Fundação (D05) não entrava. Só sem `OUTROS_DESC` no catálogo é que aborta (ver
 * [descontosSemRubrica]).
 */
internal fun somarLinhasPorRubrica(
    linhas: List<LinhaReciboImportada>,
    catalogoPorCodigo: Map<String, Rubrica>,
    naoReconhecidas: MutableList<String>,
    /**
     * C7: descontos que não podem ficar na própria rubrica (sem código interno, código fora do
     * catálogo ou mapeado para uma rubrica de abono) **e** que também não puderam ir para
     * [RUBRICA_OUTROS_DESC] — só acontece numa BD anterior à rubrica. Quem chama aborta a
     * importação: escrevê-los num abono somava o desconto ao líquido em vez de o tirar.
     */
    descontosSemRubrica: MutableList<String> = mutableListOf(),
    /**
     * 2026-10-21: avisos dos descontos que foram para [RUBRICA_OUTROS_DESC] em vez de bloquearem a
     * importação. A UI mostra-os a seguir à importação, junto aos avisos do parser.
     */
    avisosDesconto: MutableList<String> = mutableListOf()
): Map<Long, Long> {
    // Primeiro as rubricas declaradas e não pagas, para uma linha com valor escrita depois somar
    // por cima de 0 em vez de ser apagada.
    val somados = mutableMapOf<Long, Long>()
    for (linha in linhas) {
        if (linha.codigoInterno != null) continue
        val codigoMapeado = mapearCodigoRecibo(linha.codigoRecibo).codigoInterno ?: continue
        catalogoPorCodigo[codigoMapeado]?.let { somados[it.id] = 0L }
    }
    for (linha in linhas) {
        if (linha.natureza == NaturezaRubrica.DESCONTO) {
            val codigo = linha.codigoInterno
            val vaiParaDesconto =
                codigo != null && catalogoPorCodigo[codigo] != null && ehRubricaDeDesconto(codigo)
            if (!vaiParaDesconto) {
                // 2026-10-21: o desconto não fica de fora nem entra num abono — vai para
                // OUTROS_DESC e a UI avisa. Sem essa rubrica no catálogo (BD antiga) volta a
                // bloquear: não há onde escrever o valor a subtrair.
                val recurso = catalogoPorCodigo[RUBRICA_OUTROS_DESC]
                if (recurso == null) {
                    descontosSemRubrica.add(linha.codigoRecibo)
                } else {
                    somados[recurso.id] = (somados[recurso.id] ?: 0L) + linha.valor
                    avisosDesconto.add(AVISO_DESCONTO_OUTROS)
                }
                continue
            }
        }
        val codigoInterno = linha.codigoInterno ?: continue
        val rubrica = catalogoPorCodigo[codigoInterno]
        if (rubrica == null) {
            naoReconhecidas.add(codigoInterno)
            continue
        }
        somados[rubrica.id] = (somados[rubrica.id] ?: 0L) + linha.valor
    }
    return somados
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

/** Espaços seguidos, para colapsar as descrições que o PDF parte em blocos. */
private val REGEX_ESPACOS = Regex("\\s+")

/** "31/08/2026" · "30-04-2016" -> epochDay. null se não for data válida. */
private fun parseData(texto: String): Long? =
    try {
        LocalDate.parse(texto.trim().replace('-', '/'), FORMATO_DATA).toEpochDay()
    } catch (_: Exception) {
        null
    }
