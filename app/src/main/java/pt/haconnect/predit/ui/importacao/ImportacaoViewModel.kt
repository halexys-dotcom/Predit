package pt.haconnect.predit.ui.importacao

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.pdf.extrairTextoPdf
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.PlanejamentoMesRepository
import pt.haconnect.predit.domain.importacao.AcaoImportacaoDia
import pt.haconnect.predit.domain.importacao.PlanoImportado
import pt.haconnect.predit.domain.importacao.decidirImportacaoDia
import pt.haconnect.predit.domain.importacao.parsePdfPlanner
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.PlanejamentoMes
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SubstituicaoAviso(
    val data: LocalDate,
    val mensagem: String
)

data class ImportacaoUiState(
    val aCarregar: Boolean = false,
    val uriSelecionado: Uri? = null,
    val nomeFicheiro: String? = null,
    val plano: PlanoImportado? = null,
    val avisosSubstituicao: List<SubstituicaoAviso> = emptyList(),
    val importadoComSucesso: Boolean = false,
    val mensagemSucesso: String? = null,
    val erro: String? = null
)

class ImportacaoViewModel(
    private val diaRealRepository: DiaRealRepository,
    private val planejamentoMesRepository: PlanejamentoMesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportacaoUiState())
    val uiState: StateFlow<ImportacaoUiState> = _uiState.asStateFlow()

    fun selecionarEParsearPdf(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(aCarregar = true, uriSelecionado = uri, erro = null) }
            try {
                val nomeFicheiro = obterNomeFicheiro(uri, context)
                val texto = extrairTextoPdf(uri, context)

                if (texto.isBlank()) {
                    _uiState.update {
                        it.copy(
                            aCarregar = false,
                            erro = "Não foi possível extrair texto do ficheiro PDF."
                        )
                    }
                    return@launch
                }

                val plano = parsePdfPlanner(texto, nomeFicheiro)

                // Verificar substituições de registos MANUAIS
                val avisosSub = mutableListOf<SubstituicaoAviso>()
                val formatter = DateTimeFormatter.ofPattern("dd/MM")

                val todosReais = diaRealRepository.observarTodos().first()
                val reaisMap = todosReais.associateBy { it.data }

                for (diaImp in plano.dias) {
                    val realExistente = reaisMap[diaImp.epochDay]
                    if (realExistente != null && realExistente.origem == "MANUAL") {
                        val dt = LocalDate.ofEpochDay(diaImp.epochDay)
                        avisosSub.add(
                            SubstituicaoAviso(
                                data = dt,
                                mensagem = "Dia ${dt.format(formatter)}: registo manual será preservado"
                            )
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        aCarregar = false,
                        nomeFicheiro = nomeFicheiro,
                        plano = plano,
                        avisosSubstituicao = avisosSub
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        aCarregar = false,
                        erro = "Erro ao processar o ficheiro PDF: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun confirmarImportacao(onConcluido: (Int) -> Unit) {
        val plano = _uiState.value.plano ?: return
        val mesRef = plano.mesReferencia ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(aCarregar = true) }

            val inicioMes = mesRef.atDay(1).toEpochDay()
            val fimMes = mesRef.atEndOfMonth().toEpochDay()
            val existentes = diaRealRepository.obterNoIntervalo(inicioMes, fimMes)
            val existentesMap = existentes.associateBy { it.data }

            var contagemImportados = 0
            var contagemManuaisPreservados = 0

            for (diaImp in plano.dias) {
                val existente = existentesMap[diaImp.epochDay]
                when (decidirImportacaoDia(existente)) {
                    AcaoImportacaoDia.INSERIR, AcaoImportacaoDia.ATUALIZAR -> {
                        val idExistente = existente?.id ?: 0L
                        diaRealRepository.guardar(
                            DiaReal(
                                id = idExistente,
                                data = diaImp.epochDay,
                                tipoTurnoId = null,
                                inicioMin = diaImp.inicioMin,
                                fimMin = diaImp.fimMin,
                                pausaMin = diaImp.pausaMin,
                                nota = null,
                                origem = "PDF",
                                posto = diaImp.posto
                            )
                        )
                        contagemImportados++
                    }
                    AcaoImportacaoDia.MANTER_MANUAL -> {
                        contagemManuaisPreservados++
                    }
                }
            }

            // Gravar o total e contadores do mês
            val anoMesStr = "%04d-%02d".format(mesRef.year, mesRef.monthValue)
            val numTurnos = plano.dias.size
            val numFolgas = mesRef.lengthOfMonth() - plano.dias.size

            planejamentoMesRepository.upsert(
                PlanejamentoMes(
                    anoMes = anoMesStr,
                    totalMinutos = plano.totalMinutos,
                    numTurnos = numTurnos,
                    numFolgas = numFolgas,
                    contratoMinutos = plano.contratoTrabalhoMin,
                    dataImportacao = System.currentTimeMillis()
                )
            )

            _uiState.update {
                it.copy(
                    aCarregar = false,
                    importadoComSucesso = true,
                    mensagemSucesso = "$contagemImportados dias importados, $contagemManuaisPreservados manuais preservados"
                )
            }
            onConcluido(contagemImportados)
        }
    }

    private fun obterNomeFicheiro(uri: Uri, context: Context): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Planning.pdf"
    }

    class Factory(
        private val diaRealRepository: DiaRealRepository,
        private val planejamentoMesRepository: PlanejamentoMesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImportacaoViewModel(diaRealRepository, planejamentoMesRepository) as T
        }
    }
}
