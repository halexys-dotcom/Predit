package pt.haconnect.predit.ui.escala

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
import pt.haconnect.predit.data.repository.PlanejamentoMesRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.AplicacaoVigente
import pt.haconnect.predit.domain.calc.ORIGEM_PDF
import pt.haconnect.predit.domain.calc.TIPO_ESCALA_PDF_MENSAL
import pt.haconnect.predit.domain.calc.TIPO_ESCALA_ROTACAO
import pt.haconnect.predit.domain.calc.aplicarAusencias
import pt.haconnect.predit.domain.calc.diasProjetadosPara
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.feriadoMunicipal
import pt.haconnect.predit.domain.calc.feriadosNacionais
import pt.haconnect.predit.domain.calc.temEscalaAplicada
import pt.haconnect.predit.domain.calc.tipoDerivadoDoPdf
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.PlanejamentoMes
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DiaMesEscala(
    val data: LocalDate,
    val pertenceAoMesAtual: Boolean,
    val ehHoje: Boolean,
    /** Feriado nacional ou municipal (do contrato): pinta o chip de azul na grelha. */
    val ehFeriado: Boolean = false,
    val tipoTurnoProjetado: TipoTurno?,
    val ausenciaBruta: Ausencia?,
    val ausenciaEfetiva: Ausencia?,
    val tipoTurnoEfetivo: TipoTurno?,
    val diaReal: DiaReal? = null,
    val tipoTurnoChip: TipoTurno? = null
) {
    val ausencia: Ausencia? get() = ausenciaBruta
}

enum class FonteEstatistica { PROJECAO, PLANEJAMENTO }

data class EstatisticasMes(
    val numTurnos: Int,
    val numFolgas: Int,
    val numAusencias: Int,
    val totalMinutosTrabalho: Int,
    val fonte: FonteEstatistica = FonteEstatistica.PROJECAO
)

data class EscalaUiState(
    val anoMesAtual: YearMonth = YearMonth.now(),
    val diasGrelha: List<DiaMesEscala> = emptyList(),
    val estatisticas: EstatisticasMes = EstatisticasMes(0, 0, 0, 0),
    val temEscalaAplicada: Boolean = false,
    /** Modo de escala do contrato (Fase 19): decide a mensagem do estado vazio. */
    val tipoEscala: String = TIPO_ESCALA_ROTACAO
)

class EscalaViewModel(
    private val rotacaoRepository: RotacaoRepository,
    private val tipoTurnoRepository: TipoTurnoRepository,
    private val ausenciaRepository: AusenciaRepository,
    private val diaRealRepository: DiaRealRepository,
    private val planejamentoMesRepository: PlanejamentoMesRepository,
    private val municipioRepository: MunicipioRepository,
    private val contratoRepository: ContratoRepository
) : ViewModel() {

    private val _anoMesAtual = MutableStateFlow(YearMonth.now())
    val anoMesAtual: StateFlow<YearMonth> = _anoMesAtual.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val planejamentoDoMes: StateFlow<PlanejamentoMes?> = _anoMesAtual
        .flatMapLatest { mes ->
            planejamentoMesRepository.observarPorMes("%04d-%02d".format(mes.year, mes.monthValue))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val aplicacoesVigentes: StateFlow<List<AplicacaoVigente>> =
        rotacaoRepository.observarTodasAplicacoes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tiposTurno: StateFlow<List<TipoTurno>> =
        tipoTurnoRepository.observarTodos()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ausencias: StateFlow<List<Ausencia>> =
        ausenciaRepository.observarTodas()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val diasReais: StateFlow<List<DiaReal>> =
        diaRealRepository.observarTodos()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Contrato: traz o modo de escala (Fase 19) e o município dos feriados (12b). */
    private val contratoAtual: StateFlow<ContratoUtilizador?> =
        contratoRepository.observar()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState: StateFlow<EscalaUiState> = combine(
        combine(_anoMesAtual, planejamentoDoMes) { mes, plan -> mes to plan },
        aplicacoesVigentes,
        tiposTurno,
        ausencias,
        // Fase 19: o contrato entra aninhado com os dias reais — o combine externo já usa as
        // cinco fontes que a API oferece sem varargs.
        combine(diasReais, contratoAtual) { dias, contrato -> dias to contrato }
    ) { (mes, planejamento), aplicacoes, tipos, listaAusencias, (listaDiasReais, contrato) ->
        val tipoEscala = contrato?.tipoEscala ?: TIPO_ESCALA_ROTACAO
        val mapaTipos = tipos.associateBy { it.id }
        val categoriasPorTipo = tipos.associate { it.id to it.categoria }

        val primeiroDiaMes = mes.atDay(1)
        val offsetSegunda = (primeiroDiaMes.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val inicioGrelha = primeiroDiaMes.minusDays(offsetSegunda.toLong())

        val ultimoDiaMes = mes.atEndOfMonth()
        val offsetDomingo = (DayOfWeek.SUNDAY.value - ultimoDiaMes.dayOfWeek.value + 7) % 7
        val fimGrelha = ultimoDiaMes.plusDays(offsetDomingo.toLong())

        val hoje = LocalDate.now()

        // Feriados do ano visível: nacionais + o municipal do contrato (Fase 12b).
        // O município e o contrato são fixos — buscá-los por mês visível é barato.
        val feriadosDoAno = feriadosNacionais(mes.year).toMutableSet()
        contrato?.municipioId
            ?.let { municipioRepository.obterPorId(it) }
            ?.let { m -> feriadoMunicipal(mes.year, m.feriadoDia, m.feriadoMes)?.let { feriadosDoAno.add(it) } }

        val diasProjetados = diasProjetadosPara(
            tipoEscala = tipoEscala,
            diasReais = listaDiasReais,
            deEpochDay = inicioGrelha.toEpochDay(),
            ateEpochDay = fimGrelha.toEpochDay(),
            aplicacoes = aplicacoes
        )

        val diasComEstado = aplicarAusencias(diasProjetados, listaAusencias, categoriasPorTipo, listaDiasReais)
        val mapaComEstado = diasComEstado.associateBy { it.epochDay }

        val listaDiasGrelha = mutableListOf<DiaMesEscala>()
        var curr = inicioGrelha
        while (!curr.isAfter(fimGrelha)) {
            val estado = mapaComEstado[curr.toEpochDay()]
            val tipoProjetado = estado?.tipoTurnoProjetadoId?.let { mapaTipos[it] }
            val tipoEfetivo = estado?.tipoTurnoEfetivoId?.let { mapaTipos[it] }
            val ausenciaBruta = estado?.ausenciaBruta
            val ausenciaEfetiva = estado?.ausenciaEfetiva
            val diaReal = estado?.diaReal
            // Fase 19: no modo PDF o dia_real guarda só horas e posto (o tipo é null), por isso o
            // chip deriva-se das horas do PDF; o texto continua a ser o posto (EscalaScreen).
            val tipoChip = if (tipoEscala == TIPO_ESCALA_PDF_MENSAL && diaReal?.origem == ORIGEM_PDF) {
                tipoDerivadoDoPdf(diaReal, tipos)
            } else {
                estado?.tipoTurnoChipId?.let { mapaTipos[it] }
            }

            listaDiasGrelha.add(
                DiaMesEscala(
                    data = curr,
                    pertenceAoMesAtual = curr.year == mes.year && curr.month == mes.month,
                    ehHoje = curr == hoje,
                    ehFeriado = curr.toEpochDay() in feriadosDoAno,
                    tipoTurnoProjetado = tipoProjetado,
                    ausenciaBruta = ausenciaBruta,
                    ausenciaEfetiva = ausenciaEfetiva,
                    tipoTurnoEfetivo = tipoEfetivo,
                    diaReal = diaReal,
                    tipoTurnoChip = tipoChip
                )
            )
            curr = curr.plusDays(1)
        }

        val diasDoMesAtual = listaDiasGrelha.filter { it.pertenceAoMesAtual }

        val totalMinutos = if (planejamento != null) {
            planejamento.totalMinutos
        } else {
            diasDoMesAtual.sumOf { dia ->
                val efetivo = dia.tipoTurnoEfetivo
                when {
                    dia.ausenciaEfetiva != null -> 8 * 60
                    efetivo?.categoria == CategoriaTurno.TRABALHO ->
                        duracaoMinutos(efetivo.inicioMin, efetivo.fimMin, efetivo.pausaMin)
                    else -> 0
                }
            }
        }

        val numTurnos = if (planejamento != null) {
            planejamento.numTurnos
        } else {
            diasDoMesAtual.count { it.ausenciaEfetiva == null && it.tipoTurnoEfetivo?.categoria == CategoriaTurno.TRABALHO }
        }

        val numFolgas = if (planejamento != null) {
            planejamento.numFolgas
        } else {
            diasDoMesAtual.count { it.tipoTurnoEfetivo?.categoria == CategoriaTurno.FOLGA }
        }

        val numAusencias = diasDoMesAtual.count { it.ausenciaEfetiva != null }

        val fonte = if (planejamento != null) FonteEstatistica.PLANEJAMENTO else FonteEstatistica.PROJECAO

        EscalaUiState(
            anoMesAtual = mes,
            diasGrelha = listaDiasGrelha,
            estatisticas = EstatisticasMes(
                numTurnos = numTurnos,
                numFolgas = numFolgas,
                numAusencias = numAusencias,
                totalMinutosTrabalho = totalMinutos,
                fonte = fonte
            ),
            temEscalaAplicada = temEscalaAplicada(tipoEscala, listaDiasReais, aplicacoes, mes),
            tipoEscala = tipoEscala
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EscalaUiState())

    fun mesAnterior() {
        _anoMesAtual.update { it.minusMonths(1) }
    }

    fun mesSeguinte() {
        _anoMesAtual.update { it.plusMonths(1) }
    }

    fun irParaHoje() {
        _anoMesAtual.value = YearMonth.now()
    }

    fun removerAusencia(ausenciaId: Long, onConcluido: () -> Unit = {}) {
        viewModelScope.launch {
            ausenciaRepository.apagar(ausenciaId)
            onConcluido()
        }
    }

    class Factory(
        private val rotacaoRepository: RotacaoRepository,
        private val tipoTurnoRepository: TipoTurnoRepository,
        private val ausenciaRepository: AusenciaRepository,
        private val diaRealRepository: DiaRealRepository,
        private val planejamentoMesRepository: PlanejamentoMesRepository,
        private val municipioRepository: MunicipioRepository,
        private val contratoRepository: ContratoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EscalaViewModel(
                rotacaoRepository,
                tipoTurnoRepository,
                ausenciaRepository,
                diaRealRepository,
                planejamentoMesRepository,
                municipioRepository,
                contratoRepository
            ) as T
        }
    }
}
