package pt.haconnect.predit.ui.contrato

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.domain.model.ContratoUtilizador

class ContratoViewModel(private val repository: ContratoRepository) : ViewModel() {

    val contrato: StateFlow<ContratoUtilizador?> = repository.observar()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun guardar(contratoAtualizado: ContratoUtilizador, onConcluido: () -> Unit = {}) {
        viewModelScope.launch {
            repository.guardar(contratoAtualizado.copy(primeiroArranqueConcluido = true))
            onConcluido()
        }
    }

    class Factory(private val repository: ContratoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContratoViewModel(repository) as T
        }
    }
}
