package pt.haconnect.predit.ui.mais

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.haconnect.predit.data.backup.BackupManager
import pt.haconnect.predit.domain.model.BackupInfo

data class BackupsUiState(
    val backups: List<BackupInfo> = emptyList(),
    val aProcessar: Boolean = false,
    val mensagem: String? = null
)

/**
 * Cópias de segurança internas: listar, criar, apagar e marcar um restauro (Fase 11b).
 *
 * Todo o acesso a ficheiros vai para IO — o `listar()` abre cada backup para lhe ler o
 * `user_version`, e uma década de cópias no main thread notava-se.
 */
class BackupsViewModel(private val backupManager: BackupManager) : ViewModel() {

    private val _estado = MutableStateFlow(BackupsUiState())
    val estado: StateFlow<BackupsUiState> = _estado.asStateFlow()

    /** Onde as cópias vivem, para o ecrã o poder dizer. */
    val pasta: String get() = backupManager.pastaBackups.path

    init {
        listar()
    }

    fun listar() {
        viewModelScope.launch {
            val copias = withContext(Dispatchers.IO) { backupManager.listar() }
            _estado.update { it.copy(backups = copias) }
        }
    }

    /** Exporta a BD para um URI escolhido pelo utilizador (SAF). */
    fun exportarPara(uri: Uri) {
        viewModelScope.launch {
            _estado.update { it.copy(aProcessar = true) }
            val resultado = backupManager.exportarParaUri(uri)
            _estado.update {
                it.copy(
                    aProcessar = false,
                    mensagem = resultado.fold(
                        onSuccess = { "Backup guardado no destino escolhido." },
                        onFailure = { e -> "Nao consegui guardar: ${e.message}" }
                    )
                )
            }
        }
    }

    /**
     * Importa um backup de um URI escolhido pelo utilizador. Em caso de sucesso,
     * chama [onPrecisaReiniciar] — o restauro entra no proximo arranque.
     */
    fun importarDe(uri: Uri, onPrecisaReiniciar: () -> Unit) {
        viewModelScope.launch {
            _estado.update { it.copy(aProcessar = true) }
            val resultado = backupManager.importarDeUri(uri)
            resultado.fold(
                onSuccess = {
                    _estado.update { it.copy(aProcessar = false) }
                    onPrecisaReiniciar()
                },
                onFailure = { e ->
                    _estado.update {
                        it.copy(aProcessar = false, mensagem = "Nao consegui restaurar: ${e.message}")
                    }
                }
            )
        }
    }

    fun apagar(backup: BackupInfo) {
        viewModelScope.launch {
            _estado.update { it.copy(aProcessar = true) }
            withContext(Dispatchers.IO) { backupManager.apagar(backup) }
            atualizarLista("Apagado: ${backup.nome}")
        }
    }

    /**
     * Marca [backup] para entrar no próximo arranque. Quem reinicia é o ecrã, depois de mostrar
     * o aviso — matar o processo é decisão da UI, não do cálculo.
     */
    fun restaurar(backup: BackupInfo, onPrecisaReiniciar: () -> Unit) {
        viewModelScope.launch {
            _estado.update { it.copy(aProcessar = true) }
            try {
                backupManager.prepararRestauro(backup)
                _estado.update { it.copy(aProcessar = false) }
                onPrecisaReiniciar()
            } catch (e: Exception) {
                _estado.update {
                    it.copy(aProcessar = false, mensagem = "Não consegui restaurar: ${e.message}")
                }
            }
        }
    }

    fun mensagemMostrada() {
        _estado.update { it.copy(mensagem = null) }
    }

    private suspend fun atualizarLista(mensagem: String) {
        val copias = withContext(Dispatchers.IO) { backupManager.listar() }
        _estado.update { it.copy(aProcessar = false, backups = copias, mensagem = mensagem) }
    }

    class Factory(private val backupManager: BackupManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BackupsViewModel(backupManager) as T
        }
    }
}
