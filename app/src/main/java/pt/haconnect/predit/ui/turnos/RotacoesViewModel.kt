package pt.haconnect.predit.ui.turnos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.domain.model.AplicacaoRotacao
import pt.haconnect.predit.domain.model.Rotacao
import pt.haconnect.predit.domain.model.RotacaoDetalhada
import pt.haconnect.predit.domain.model.RotacaoSlot
import java.time.LocalDate

class RotacoesViewModel(private val repository: RotacaoRepository) : ViewModel() {

    val rotacoes: StateFlow<List<Rotacao>> = repository.observarTodas()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val aplicacaoVigente: StateFlow<AplicacaoRotacao?> = repository.observarAplicacaoVigente(LocalDate.now().toEpochDay())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    suspend fun carregarDetalhes(id: Long): RotacaoDetalhada? {
        return repository.porId(id)
    }

    fun salvarRotacaoComSlots(rotacao: Rotacao, slots: List<RotacaoSlot>, onConcluido: () -> Unit = {}) {
        viewModelScope.launch {
            repository.salvarRotacaoComSlots(rotacao, slots)
            onConcluido()
        }
    }

    fun apagarRotacao(id: Long) {
        viewModelScope.launch {
            repository.apagarRotacao(id)
        }
    }

    fun aplicarRotacao(rotacaoId: Long, dataAncora: Long, validoDe: Long, onConcluido: () -> Unit = {}) {
        viewModelScope.launch {
            repository.aplicarNovaRotacao(rotacaoId, dataAncora, validoDe)
            onConcluido()
        }
    }

    class Factory(private val repository: RotacaoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RotacoesViewModel(repository) as T
        }
    }
}
