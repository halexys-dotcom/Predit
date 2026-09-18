package pt.haconnect.predit.ui.mais

import android.content.Context
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
import pt.haconnect.predit.data.update.ApkDownloader
import pt.haconnect.predit.data.update.ApkInstaller
import pt.haconnect.predit.data.update.EstadoDownload
import pt.haconnect.predit.data.update.UpdateRepository
import pt.haconnect.predit.domain.update.ResultadoVerificacao
import pt.haconnect.predit.domain.update.VersionManifest
import pt.haconnect.predit.domain.update.compararVersoes
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class EstadoAtualizacao {
    data object Inicial : EstadoAtualizacao()
    data object AVerificar : EstadoAtualizacao()
    data object SemAtualizacao : EstadoAtualizacao()
    data class Disponivel(val manifest: VersionManifest) : EstadoAtualizacao()
    data class ADescarregar(val progresso: Float) : EstadoAtualizacao()
    data object Descarregado : EstadoAtualizacao()
    data class Erro(val mensagem: String) : EstadoAtualizacao()
}

data class VerificarAtualizacoesUiState(
    val versaoAtualCode: Int,
    val versaoAtualNome: String,
    val estado: EstadoAtualizacao = EstadoAtualizacao.Inicial
)

/**
 * Verificacao de atualizacoes e instalacao OTA (Fase 11d.2).
 *
 * O download fica a cargo do DownloadManager do sistema: o ViewModel so acompanha o
 * progresso e, no fim, prepara o APK na cache para o instalador o poder ler.
 */
class VerificarAtualizacoesViewModel(
    private val updateRepository: UpdateRepository,
    private val apkDownloader: ApkDownloader,
    private val versionCodeAtual: Int,
    private val versionNameAtual: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VerificarAtualizacoesUiState(versionCodeAtual, versionNameAtual)
    )
    val uiState: StateFlow<VerificarAtualizacoesUiState> = _uiState.asStateFlow()

    fun verificar() {
        viewModelScope.launch {
            _uiState.update { it.copy(estado = EstadoAtualizacao.AVerificar) }
            updateRepository.obterManifesto()
                .onSuccess { manifest ->
                    when (val resultado = compararVersoes(versionCodeAtual, manifest)) {
                        is ResultadoVerificacao.SemAtualizacao ->
                            _uiState.update { it.copy(estado = EstadoAtualizacao.SemAtualizacao) }

                        is ResultadoVerificacao.AtualizacaoDisponivel ->
                            _uiState.update {
                                it.copy(estado = EstadoAtualizacao.Disponivel(resultado.manifest))
                            }

                        is ResultadoVerificacao.Erro ->
                            _uiState.update {
                                it.copy(estado = EstadoAtualizacao.Erro(resultado.mensagem))
                            }
                    }
                }
                .onFailure { erro -> definirErro(erro) }
        }
    }

    fun descarregar(apkUrl: String) {
        viewModelScope.launch {
            val id = try {
                apkDownloader.descarregar(apkUrl)
            } catch (e: Exception) {
                definirErro(e)
                return@launch
            }

            apkDownloader.observar(id).collect { estado ->
                when (estado) {
                    is EstadoDownload.Progresso -> {
                        val fracao =
                            if (estado.total > 0) estado.baixado.toFloat() / estado.total else 0f
                        _uiState.update { it.copy(estado = EstadoAtualizacao.ADescarregar(fracao)) }
                    }

                    is EstadoDownload.Concluido -> {
                        // O DownloadManager escreve no armazenamento externo; o instalador
                        // le da cache (copia de 15 MB, por isso fora do thread principal).
                        val apk = withContext(Dispatchers.IO) {
                            apkDownloader.prepararApkParaInstalar()
                        }
                        _uiState.update {
                            it.copy(
                                estado = if (apk != null) {
                                    EstadoAtualizacao.Descarregado
                                } else {
                                    EstadoAtualizacao.Erro("O ficheiro descarregado não é válido.")
                                }
                            )
                        }
                    }

                    is EstadoDownload.Falhou ->
                        _uiState.update {
                            it.copy(estado = EstadoAtualizacao.Erro("O download falhou."))
                        }
                }
            }
        }
    }

    fun instalar(context: Context) {
        if (!ApkInstaller.instalar(context, apkDownloader.caminhoApk())) {
            _uiState.update {
                it.copy(estado = EstadoAtualizacao.Erro("Não consegui abrir o instalador."))
            }
        }
    }

    private fun definirErro(erro: Throwable) {
        val mensagem = when (erro) {
            is NoSuchElementException -> "Ainda não há nenhuma versão publicada."
            is UnknownHostException -> "Sem ligação à internet."
            is SocketTimeoutException -> "A ligação expirou. Tenta outra vez."
            else -> "Não foi possível verificar: ${erro.message ?: "erro desconhecido"}"
        }
        _uiState.update { it.copy(estado = EstadoAtualizacao.Erro(mensagem)) }
    }

    class Factory(
        private val updateRepository: UpdateRepository,
        private val apkDownloader: ApkDownloader,
        private val versionCodeAtual: Int,
        private val versionNameAtual: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VerificarAtualizacoesViewModel(
                updateRepository, apkDownloader, versionCodeAtual, versionNameAtual
            ) as T
    }
}
