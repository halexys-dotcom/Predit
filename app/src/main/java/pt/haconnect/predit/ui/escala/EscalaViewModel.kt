package pt.haconnect.predit.ui.escala

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.AplicacaoVigente
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.projetarIntervalo
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DiaMesEscala(
    val data: LocalDate,
    val pertenceAoMesAtual: Boolean,
    val ehHoje: Boolean,
    val tipoTurno: TipoTurno?
)

data class EstatisticasMes(
    val numTurnos: Int,
    val numFolgas: Int,
    val totalMinutosTrabalho: Int
)

data class EscalaUiState(
    val anoMesAtual: YearMonth = YearMonth.now(),
    val diasGrelha: List<DiaMesEscala> = emptyList(),
    val estatisticas: EstatisticasMes = EstatisticasMes(0, 0, 0),
    val temEscalaAplicada: Boolean = false
)

class EscalaViewModel(
    private val rotacaoRepository: RotacaoRepository,
    private val tipoTurnoRepository: TipoTurnoRepository
) : ViewModel() {

    private val _anoMesAtual = MutableStateFlow(YearMonth.now())
    val anoMesAtual: StateFlow<YearMonth> = _anoMesAtual.asStateFlow()

    private val aplicacoesVigentes: StateFlow<List<AplicacaoVigente>> =
        rotacaoRepository.observarAplicacoesVigentes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tiposTurno: StateFlow<List<TipoTurno>> =
        tipoTurnoRepository.observarTodos()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<EscalaUiState> = combine(
        _anoMesAtual,
        aplicacoesVigentes,
        tiposTurno
    ) { mes, aplicacoes, tipos ->
        val temEscala = aplicacoes.isNotEmpty()
        val mapaTipos = tipos.associateBy { it.id }

        // Calcular primeiro dia da grelha (semana começa à segunda-feira)
        val primeiroDiaMes = mes.atDay(1)
        val offsetSegunda = (primeiroDiaMes.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val inicioGrelha = primeiroDiaMes.minusDays(offsetSegunda.toLong())

        val ultimoDiaMes = mes.atEndOfMonth()
        val offsetDomingo = (DayOfWeek.SUNDAY.value - ultimoDiaMes.dayOfWeek.value + 7) % 7
        val fimGrelha = ultimoDiaMes.plusDays(offsetDomingo.toLong())

        val hoje = LocalDate.now()

        val diasProjetados = if (temEscala) {
            projetarIntervalo(
                deEpochDay = inicioGrelha.toEpochDay(),
                ateEpochDay = fimGrelha.toEpochDay(),
                aplicacoes = aplicacoes
            )
        } else emptyList()

        val mapaProjecao = diasProjetados.associate { it.epochDay to it.tipoTurnoId }

        val listaDiasGrelha = mutableListOf<DiaMesEscala>()
        var curr = inicioGrelha
        while (!curr.isAfter(fimGrelha)) {
            val tipoId = mapaProjecao[curr.toEpochDay()]
            val tipo = tipoId?.let { mapaTipos[it] }
            listaDiasGrelha.add(
                DiaMesEscala(
                    data = curr,
                    pertenceAoMesAtual = curr.year == mes.year && curr.month == mes.month,
                    ehHoje = curr == hoje,
                    tipoTurno = tipo
                )
            )
            curr = curr.plusDays(1)
        }

        // Estatísticas do mês atual
        val diasDoMesAtual = listaDiasGrelha.filter { it.pertenceAoMesAtual }
        val numTurnos = diasDoMesAtual.count { it.tipoTurno?.categoria == CategoriaTurno.TRABALHO }
        val numFolgas = diasDoMesAtual.count { it.tipoTurno?.categoria == CategoriaTurno.FOLGA }
        val totalMinutos = diasDoMesAtual
            .filter { it.tipoTurno?.categoria == CategoriaTurno.TRABALHO }
            .sumOf { dia ->
                val t = dia.tipoTurno
                if (t != null) duracaoMinutos(t.inicioMin, t.fimMin, t.pausaMin) else 0
            }

        EscalaUiState(
            anoMesAtual = mes,
            diasGrelha = listaDiasGrelha,
            estatisticas = EstatisticasMes(numTurnos, numFolgas, totalMinutos),
            temEscalaAplicada = temEscala
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

    class Factory(
        private val rotacaoRepository: RotacaoRepository,
        private val tipoTurnoRepository: TipoTurnoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EscalaViewModel(rotacaoRepository, tipoTurnoRepository) as T
        }
    }
}
