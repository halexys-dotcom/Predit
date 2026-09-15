package pt.haconnect.predit.ui.horario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.repository.CicloJornadaRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.domain.calc.JORNADA_CICLO_MINUTOS
import pt.haconnect.predit.domain.calc.MesCalculado
import pt.haconnect.predit.domain.calc.calcularCiclo
import pt.haconnect.predit.domain.calc.cicloDoAno
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.mesesDoCiclo
import pt.haconnect.predit.domain.calc.semestreDe
import pt.haconnect.predit.domain.model.CicloJornada
import pt.haconnect.predit.domain.model.DiaReal
import java.time.LocalDate

enum class EstadoCiclo { ATIVO, FECHADO }

data class ConferenciaUiState(
    val anoMesSelecionado: Pair<Int, Int> = semestreDe(LocalDate.now()),
    val estado: EstadoCiclo = EstadoCiclo.ATIVO,
    val meses: List<MesCalculado> = emptyList(),
    val realTotalMinutos: Int = 0,
    val jornadaTotalMinutos: Int = JORNADA_CICLO_MINUTOS,
    val extrasPagosTotalMinutos: Int = 0,
    val saldoFinalMinutos: Int = 0,
    val dataFecho: Long? = null,
    val podeFechar: Boolean = false
)

class ConferenciaViewModel(
    private val diaRealRepository: DiaRealRepository,
    private val cicloJornadaRepository: CicloJornadaRepository
) : ViewModel() {

    private val _semestreSelecionado = MutableStateFlow(semestreDe(LocalDate.now()))
    val semestreSelecionado: StateFlow<Pair<Int, Int>> = _semestreSelecionado.asStateFlow()

    private val todosDiasReais: StateFlow<List<DiaReal>> =
        diaRealRepository.observarTodos()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ciclosFechados: StateFlow<List<CicloJornada>> =
        cicloJornadaRepository.observarTodos()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<ConferenciaUiState> = combine(
        _semestreSelecionado,
        todosDiasReais,
        ciclosFechados
    ) { (ano, semestre), diasReais, fechos ->
        val idCiclo = "%04d-S%d".format(ano, semestre)
        val cicloFechado = fechos.find { it.id == idCiclo }

        val (inicioDate, fimDate) = cicloDoAno(ano, semestre)
        val listaMesesStr = mesesDoCiclo(ano, semestre)

        val reaisPorMes = diasReais
            .filter {
                val dt = LocalDate.ofEpochDay(it.data)
                dt >= inicioDate && dt <= fimDate
            }
            .groupBy {
                val dt = LocalDate.ofEpochDay(it.data)
                "%04d-%02d".format(dt.year, dt.monthValue)
            }
            .mapValues { (_, list) ->
                list.sumOf { duracaoMinutos(it.inicioMin, it.fimMin, it.pausaMin) }
            }

        val res = calcularCiclo(reaisPorMes, listaMesesStr)

        if (cicloFechado != null) {
            ConferenciaUiState(
                anoMesSelecionado = ano to semestre,
                estado = EstadoCiclo.FECHADO,
                meses = res.meses,
                realTotalMinutos = cicloFechado.realTotalMinutos,
                jornadaTotalMinutos = JORNADA_CICLO_MINUTOS,
                extrasPagosTotalMinutos = cicloFechado.extrasPagosMinutos,
                saldoFinalMinutos = cicloFechado.saldoFinalMinutos,
                dataFecho = cicloFechado.dataFecho,
                podeFechar = false
            )
        } else {
            val hoje = LocalDate.now()
            // Só ciclos terminados podem ser fechados — e apenas manualmente.
            val podeFechar = fimDate < hoje
            ConferenciaUiState(
                anoMesSelecionado = ano to semestre,
                estado = EstadoCiclo.ATIVO,
                meses = res.meses,
                realTotalMinutos = res.realTotalMinutos,
                jornadaTotalMinutos = JORNADA_CICLO_MINUTOS,
                extrasPagosTotalMinutos = res.extrasPagosTotalMinutos,
                saldoFinalMinutos = res.saldoFinalMinutos,
                dataFecho = null,
                podeFechar = podeFechar
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConferenciaUiState())

    fun semestreAnterior() {
        val (a, s) = _semestreSelecionado.value
        _semestreSelecionado.value = if (s == 1) (a - 1) to 2 else a to 1
    }

    fun semestreSeguinte() {
        val (a, s) = _semestreSelecionado.value
        _semestreSelecionado.value = if (s == 2) (a + 1) to 1 else a to 2
    }

    fun irParaHoje() {
        _semestreSelecionado.value = semestreDe(LocalDate.now())
    }

    fun fecharSemestre(onConcluido: (Int, Int) -> Unit = { _, _ -> }) {
        val (ano, semestre) = _semestreSelecionado.value
        val (inicio, fim) = cicloDoAno(ano, semestre)
        val idCiclo = "%04d-S%d".format(ano, semestre)

        viewModelScope.launch {
            val todosReais = diaRealRepository.observarTodos().first()
            val meses = mesesDoCiclo(ano, semestre)
            val reaisMap = todosReais
                .filter {
                    val dt = LocalDate.ofEpochDay(it.data)
                    dt >= inicio && dt <= fim
                }
                .groupBy {
                    val dt = LocalDate.ofEpochDay(it.data)
                    "%04d-%02d".format(dt.year, dt.monthValue)
                }
                .mapValues { (_, list) ->
                    list.sumOf { duracaoMinutos(it.inicioMin, it.fimMin, it.pausaMin) }
                }

            val res = calcularCiclo(reaisMap, meses)
            val dataFecho = System.currentTimeMillis()

            val entity = CicloJornada(
                id = idCiclo,
                inicio = inicio.toEpochDay(),
                fim = fim.toEpochDay(),
                realTotalMinutos = res.realTotalMinutos,
                extrasPagosMinutos = res.extrasPagosTotalMinutos,
                saldoFinalMinutos = res.saldoFinalMinutos,
                dataFecho = dataFecho
            )

            cicloJornadaRepository.upsert(entity)
            onConcluido(res.extrasPagosTotalMinutos, res.saldoFinalMinutos)
        }
    }

    class Factory(
        private val diaRealRepository: DiaRealRepository,
        private val cicloJornadaRepository: CicloJornadaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConferenciaViewModel(diaRealRepository, cicloJornadaRepository) as T
        }
    }
}
