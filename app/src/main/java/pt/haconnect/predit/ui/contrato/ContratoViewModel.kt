package pt.haconnect.predit.ui.contrato

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.domain.model.ContratoUtilizador

/** Uma linha do selector de categoria do contrato (13a): a chave, o nível e o nome para o ecrã. */
data class OpcaoCategoriaCCT(
    val codigo: String,
    val nivel: String,
    val nome: String
)

/**
 * Níveis das 11 categorias do CCT (13b), na ordem da tabela. É uma lista fixa e não uma
 * comparação alfabética: "XXX" não é romano, mas é o Team Leader e fica por isso no fim.
 */
private val ORDEM_NIVEIS: List<String> = listOf(
    "III", "V", "VII", "IX", "X", "XI", "XIII", "XIV", "XV", "XIX", "XXX"
)

/** Posição na lista da tabela; o que não estiver nela vai para o fim. */
private fun ordemDoNivel(nivel: String): Int =
    ORDEM_NIVEIS.indexOf(nivel).let { if (it < 0) Int.MAX_VALUE else it }

class ContratoViewModel(
    private val repository: ContratoRepository,
    parametrosCCTRepository: ParametrosCCTRepository
) : ViewModel() {

    val contrato: StateFlow<ContratoUtilizador?> = repository.observar()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Categorias do CCT para o selector, pela ordem da tabela (III → XIX, Team Leader em XXX no
     * fim). A lista sai dos parâmetros já semeados (11 categorias × 2 vigências, uma linha por
     * categoria): a UI não tem a tabela salarial escrita à mão.
     */
    val categorias: StateFlow<List<OpcaoCategoriaCCT>> = parametrosCCTRepository.observarTodos()
        .map { parametros ->
            parametros
                .map { OpcaoCategoriaCCT(it.codigoCategoria, it.nivelCCT, it.nomeCategoria) }
                .distinctBy { it.codigo }
                .sortedBy { ordemDoNivel(it.nivel) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun guardar(contratoAtualizado: ContratoUtilizador, onConcluido: () -> Unit = {}) {
        viewModelScope.launch {
            repository.guardar(contratoAtualizado.copy(primeiroArranqueConcluido = true))
            onConcluido()
        }
    }

    class Factory(
        private val repository: ContratoRepository,
        private val parametrosCCTRepository: ParametrosCCTRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContratoViewModel(repository, parametrosCCTRepository) as T
        }
    }
}
