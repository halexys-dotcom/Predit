package pt.haconnect.predit.ui.horario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.PlanejamentoMesRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.AplicacaoVigente
import pt.haconnect.predit.domain.calc.aplicarAusencias
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.projetarIntervalo
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.PlanejamentoMes
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LinhaHorario(
    val data: LocalDate,
    val tipoProjetado: TipoTurno?,
    val diaReal: DiaReal?,
    val tipoReal: TipoTurno?,
    val ausenciaEfetiva: Ausencia?,
    val tipoAusencia: TipoTurno? = null
) {
    val tipoTurno: TipoTurno? get() =
        if (tipoAusencia?.categoria == CategoriaTurno.FOLGA) tipoAusencia else (tipoReal ?: tipoProjetado)
    val posto: String? get() = diaReal?.posto
}

data class TotaisMes(
    val minutosReais: Int,
    val minutosPrevistos: Int,
    val rotuloPrevisto: String = "Horas previstas pela escala",
    val subtituloPrevisto: String? = null
) {
    val diferencaMinutos: Int get() = minutosReais - minutosPrevistos
}

data class HorarioUiState(
    val anoMesAtual: YearMonth = YearMonth.now(),
    val linhas: List<LinhaHorario> = emptyList(),
    val totais: TotaisMes = TotaisMes(0, 0)
)

class HorarioViewModel(
    private val diaRealRepository: DiaRealRepository,
    private val rotacaoRepository: RotacaoRepository,
    private val tipoTurnoRepository: TipoTurnoRepository,
    private val ausenciaRepository: AusenciaRepository,
    private val planejamentoMesRepository: PlanejamentoMesRepository
) : ViewModel() {

    private val _anoMesAtual = MutableStateFlow(YearMonth.now())
    val anoMesAtual: StateFlow<YearMonth> = _anoMesAtual.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val planejamentoMes: StateFlow<PlanejamentoMes?> = _anoMesAtual.flatMapLatest { mes ->
        val anoMesStr = "%04d-%02d".format(mes.year, mes.monthValue)
        planejamentoMesRepository.observarPorMes(anoMesStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    val uiState: StateFlow<HorarioUiState> = combine(
        combine(_anoMesAtual, planejamentoMes) { mes, plan -> mes to plan },
        aplicacoesVigentes,
        tiposTurno,
        ausencias,
        diasReais
    ) { (mes, planejamento), aplicacoes, tipos, listaAusencias, listaDiasReais ->
        val mapaTipos = tipos.associateBy { it.id }
        val categoriasPorTipo = tipos.associate { it.id to it.categoria }

        val inicioMes = mes.atDay(1)
        val fimMes = mes.atEndOfMonth()

        // NOTA: o HorarioViewModel projeta sempre pela rotacao, ignorando o
        // modo do contrato (ROTACAO vs PDF_MENSAL). Consequencia: o
        // ausenciaEfetiva so e calculada se houver rotacao aplicada. Com
        // contrato PDF_MENSAL sem rotacao, os DIFFs C-F do fix da Folga
        // ficam inertes. Fica registado — a resolver numa proxima iteracao,
        // quando o HorarioViewModel passar a usar diasProjetadosPara.
        val diasProjetados = if (aplicacoes.isNotEmpty()) {
            projetarIntervalo(
                deEpochDay = inicioMes.toEpochDay(),
                ateEpochDay = fimMes.toEpochDay(),
                aplicacoes = aplicacoes
            )
        } else emptyList()

        val diasComEstado = aplicarAusencias(diasProjetados, listaAusencias, categoriasPorTipo, listaDiasReais)
        val mapaComEstado = diasComEstado.associateBy { it.epochDay }
        val mapaReais = listaDiasReais.associateBy { it.data }

        val listaLinhas = mutableListOf<LinhaHorario>()
        var curr = inicioMes
        while (!curr.isAfter(fimMes)) {
            val epochDay = curr.toEpochDay()
            val estado = mapaComEstado[epochDay]
            val real = mapaReais[epochDay]

            val tipoProjetado = estado?.tipoTurnoProjetadoId?.let { mapaTipos[it] }
            val tipoReal = real?.tipoTurnoId?.let { mapaTipos[it] } ?: tipoProjetado
            val ausenciaEfetiva = estado?.ausenciaEfetiva
            val tipoAusencia = ausenciaEfetiva?.tipoTurnoId?.let { mapaTipos[it] }

            if (real != null) {
                listaLinhas.add(
                    LinhaHorario(
                        data = curr,
                        tipoProjetado = tipoProjetado,
                        diaReal = real,
                        tipoReal = tipoReal,
                        ausenciaEfetiva = ausenciaEfetiva,
                        tipoAusencia = tipoAusencia
                    )
                )
            }
            curr = curr.plusDays(1)
        }

        // Horas reais registadas — soma das durações de todos os DiaReal do mês, exceto os dias
        // com folga efetiva marcada por cima (Folga = 0h).
        val minutosReais = listaDiasReais
            .filter { LocalDate.ofEpochDay(it.data).year == mes.year && LocalDate.ofEpochDay(it.data).month == mes.month }
            .filter { real ->
                mapaComEstado[real.data]?.ausenciaEfetiva?.tipoTurnoId?.let { categoriasPorTipo[it] } != CategoriaTurno.FOLGA
            }
            .sumOf { duracaoMinutos(it.inicioMin, it.fimMin, it.pausaMin) }

        val minutosPrevistos: Int
        val rotuloPrevisto: String
        val subtituloPrevisto: String?

        if (planejamento != null) {
            val descontoFolga = listaDiasReais
                .filter { LocalDate.ofEpochDay(it.data).year == mes.year && LocalDate.ofEpochDay(it.data).month == mes.month }
                .filter { real ->
                    mapaComEstado[real.data]?.ausenciaEfetiva?.tipoTurnoId?.let { categoriasPorTipo[it] } == CategoriaTurno.FOLGA
                }
                .sumOf { duracaoMinutos(it.inicioMin, it.fimMin, it.pausaMin) }
            minutosPrevistos = planejamento.totalMinutos - descontoFolga
            rotuloPrevisto = "Horas previstas pela escala"
            val formatterDate = DateTimeFormatter.ofPattern("dd/MM")
            val dataImpStr = Instant.ofEpochMilli(planejamento.dataImportacao)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(formatterDate)
            subtituloPrevisto = "Fonte: importado em $dataImpStr"
        } else {
            var currPrev = inicioMes
            var projSum = 0
            while (!currPrev.isAfter(fimMes)) {
                val epochDay = currPrev.toEpochDay()
                val estado = mapaComEstado[epochDay]
                val tProjetado = estado?.tipoTurnoProjetadoId?.let { mapaTipos[it] }
                val semAusencia = estado?.ausenciaEfetiva == null

                if (semAusencia && tProjetado != null && tProjetado.categoria == CategoriaTurno.TRABALHO) {
                    projSum += duracaoMinutos(tProjetado.inicioMin, tProjetado.fimMin, tProjetado.pausaMin)
                }
                currPrev = currPrev.plusDays(1)
            }
            minutosPrevistos = projSum
            rotuloPrevisto = "Horas estimadas (projeção)"
            subtituloPrevisto = null
        }

        HorarioUiState(
            anoMesAtual = mes,
            linhas = listaLinhas,
            totais = TotaisMes(
                minutosReais = minutosReais,
                minutosPrevistos = minutosPrevistos,
                rotuloPrevisto = rotuloPrevisto,
                subtituloPrevisto = subtituloPrevisto
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HorarioUiState())

    fun mesAnterior() {
        _anoMesAtual.update { it.minusMonths(1) }
    }

    fun mesSeguinte() {
        _anoMesAtual.update { it.plusMonths(1) }
    }

    fun irParaHoje() {
        _anoMesAtual.value = YearMonth.now()
    }

    class Factory(
        private val diaRealRepository: DiaRealRepository,
        private val rotacaoRepository: RotacaoRepository,
        private val tipoTurnoRepository: TipoTurnoRepository,
        private val ausenciaRepository: AusenciaRepository,
        private val planejamentoMesRepository: PlanejamentoMesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HorarioViewModel(
                diaRealRepository,
                rotacaoRepository,
                tipoTurnoRepository,
                ausenciaRepository,
                planejamentoMesRepository
            ) as T
        }
    }
}
