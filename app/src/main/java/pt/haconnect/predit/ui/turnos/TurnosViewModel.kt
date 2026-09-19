package pt.haconnect.predit.ui.turnos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.model.TipoTurno

class TurnosViewModel(private val repository: TipoTurnoRepository) : ViewModel() {

    val tiposTurno: StateFlow<List<TipoTurno>> = repository.observarTodos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun salvarTipoTurno(tipo: TipoTurno) {
        viewModelScope.launch {
            repository.salvar(tipo)
        }
    }

    fun desativarOuAtivar(id: Long, novoEstado: Boolean) {
        viewModelScope.launch {
            repository.definirAtivo(id, novoEstado)
        }
    }

    /**
     * 12c C.3 — apaga o tipo de turno. O [onResultado] devolve `false` (com explicação para o
     * utilizador) quando o tipo está a ser usado numa rotação, ausência ou registo: a FK de
     * tipo_turno é ON DELETE RESTRICT, logo o SQLite recusa o DELETE.
     */
    fun apagar(id: Long, onResultado: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.apagar(id)
                onResultado(true, null)
            } catch (e: Exception) {
                onResultado(
                    false,
                    "Este tipo está a ser usado numa rotação, ausência ou registo. " +
                        "Desativa-o em vez de o apagar."
                )
            }
        }
    }

    class Factory(private val repository: TipoTurnoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TurnosViewModel(repository) as T
        }
    }
}
