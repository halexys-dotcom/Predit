package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.posicaoNoCiclo
import pt.haconnect.predit.domain.model.RotacaoDetalhada
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AplicarRotacaoScreen(
    rotacaoId: Long,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val turnosViewModel: TurnosViewModel = viewModel(
        factory = TurnosViewModel.Factory(TipoTurnoRepository(db.tipoTurnoDao()))
    )
    val rotacoesViewModel: RotacoesViewModel = viewModel(
        factory = RotacoesViewModel.Factory(RotacaoRepository(db.rotacaoDao()))
    )

    val tiposTurno by turnosViewModel.tiposTurno.collectAsState()
    val mapaTipos = remember(tiposTurno) { tiposTurno.associateBy { it.id } }

    var rotacaoDetalhada by remember { mutableStateOf<RotacaoDetalhada?>(null) }

    LaunchedEffect(rotacaoId) {
        rotacaoDetalhada = rotacoesViewModel.carregarDetalhes(rotacaoId)
    }

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dataAtual = LocalDate.now()

    var dataAncoraTexto by remember { mutableStateOf(dataAtual.format(formatter)) }
    var validoDeTexto by remember { mutableStateOf(dataAtual.format(formatter)) }

    var mostrarConfirmacao by remember { mutableStateOf(false) }

    val dataAncoraParsed = parseData(dataAncoraTexto)
    val validoDeParsed = parseData(validoDeTexto)

    val detalhe = rotacaoDetalhada
    val cicloSize = detalhe?.rotacao?.comprimentoCiclo ?: 0
    val mapaSlots = remember(detalhe) { detalhe?.slots?.associateBy { it.posicao } ?: emptyMap() }

    val formValido = dataAncoraParsed != null && validoDeParsed != null && cicloSize > 0

    Scaffold(
        topBar = {
            TopAppBar(
                // Fase 12b: o nome da rotacao e livre, logo o titulo pode ficar longo — mesmas guardas
                // das outras barras (1 linha, sem quebra a meio da palavra, reticencias).
                title = {
                    Text(
                        text = "Aplicar Rotação ${detalhe?.rotacao?.nome?.let { "— $it" } ?: ""}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onVoltar,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        enabled = formValido,
                        onClick = {
                            if (dataAncoraParsed != null && validoDeParsed != null) {
                                mostrarConfirmacao = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirmar")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = dataAncoraTexto,
                onValueChange = { dataAncoraTexto = it },
                label = { Text("Em que dia começa o ciclo? (DD/MM/YYYY) *") },
                singleLine = true,
                isError = dataAncoraParsed == null,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = validoDeTexto,
                onValueChange = { validoDeTexto = it },
                label = { Text("Em vigor a partir de (DD/MM/YYYY) *") },
                singleLine = true,
                isError = validoDeParsed == null,
                modifier = Modifier.fillMaxWidth()
            )

            if (dataAncoraParsed != null && validoDeParsed != null && cicloSize > 0) {
                Text(
                    text = "Pré-visualização dos próximos 21 dias:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val diasPreVisualizacao = remember(dataAncoraParsed, validoDeParsed, cicloSize) {
                    (0 until 21).map { offset ->
                        val dia = validoDeParsed.plusDays(offset.toLong())
                        val pos = posicaoNoCiclo(dia.toEpochDay(), dataAncoraParsed.toEpochDay(), cicloSize)
                        val slot = mapaSlots[pos]
                        val tipo = slot?.let { mapaTipos[it.tipoTurnoId] }
                        dia to tipo
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(diasPreVisualizacao) { (dia, tipo) ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = dia.format(formatter),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = dia.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "PT")).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (tipo != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color(tipo.cor))
                                        )
                                        Text(
                                            text = "${tipo.emoji ?: ""} ${tipo.nome}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Text("Sem turno", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (mostrarConfirmacao && dataAncoraParsed != null && validoDeParsed != null) {
            AlertDialog(
                onDismissRequest = { mostrarConfirmacao = false },
                title = { Text("Aplicar rotação à escala?") },
                text = {
                    Text("A aplicação atual da escala será encerrada e a nova rotação ficará em vigor a partir de $validoDeTexto. Deseja continuar?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mostrarConfirmacao = false
                            rotacoesViewModel.aplicarRotacao(
                                rotacaoId = rotacaoId,
                                dataAncora = dataAncoraParsed.toEpochDay(),
                                validoDe = validoDeParsed.toEpochDay()
                            ) {
                                onVoltar()
                            }
                        }
                    ) {
                        Text("Aplicar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarConfirmacao = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

private fun parseData(texto: String): LocalDate? {
    return try {
        val partes = texto.trim().split("/")
        if (partes.size != 3) return null
        val dia = partes[0].toIntOrNull() ?: return null
        val mes = partes[1].toIntOrNull() ?: return null
        val ano = partes[2].toIntOrNull() ?: return null
        LocalDate.of(ano, mes, dia)
    } catch (_: Exception) {
        null
    }
}
