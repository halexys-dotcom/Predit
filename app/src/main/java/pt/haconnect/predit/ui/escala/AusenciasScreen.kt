package pt.haconnect.predit.ui.escala

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.TipoTurno
import pt.haconnect.predit.ui.turnos.TextoSemQuebra
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AusenciasScreen(
    modifier: Modifier = Modifier,
    onNavegarParaCriarAusencia: () -> Unit = {},
    onNavegarParaEditarAusencia: (Long) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val ausenciaRepository = remember { AusenciaRepository(db.ausenciaDao()) }
    val tipoTurnoRepository = remember { TipoTurnoRepository(db.tipoTurnoDao()) }

    val ausencias by ausenciaRepository.observarTodas().collectAsState(initial = emptyList())
    val tiposTurno by tipoTurnoRepository.observarTodos().collectAsState(initial = emptyList())
    val mapaTipos = remember(tiposTurno) { tiposTurno.associateBy { it.id } }

    var ausenciaParaRemover by remember { mutableStateOf<Ausencia?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ausências Registadas") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavegarParaCriarAusencia,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Marcar ausência")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (ausencias.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sem ausências registadas. Toque em + para marcar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ausencias, key = { it.id }) { ausencia ->
                        val tipo = mapaTipos[ausencia.tipoTurnoId]
                        CartaoAusencia(
                            ausencia = ausencia,
                            tipoTurno = tipo,
                            onClick = { onNavegarParaEditarAusencia(ausencia.id) },
                            onRemover = { ausenciaParaRemover = ausencia }
                        )
                    }
                }
            }
        }

        ausenciaParaRemover?.let { ausencia ->
            AlertDialog(
                onDismissRequest = { ausenciaParaRemover = null },
                title = { Text("Remover ausência?") },
                text = { Text("Tem a certeza que deseja remover esta ausência?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = ausencia.id
                            ausenciaParaRemover = null
                            scope.launch {
                                ausenciaRepository.apagar(id)
                            }
                        }
                    ) {
                        Text("Remover", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ausenciaParaRemover = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun CartaoAusencia(
    ausencia: Ausencia,
    tipoTurno: TipoTurno?,
    onClick: () -> Unit,
    onRemover: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val inicioStr = remember(ausencia.dataInicio) { LocalDate.ofEpochDay(ausencia.dataInicio).format(formatter) }
    val fimStr = remember(ausencia.dataFim) { LocalDate.ofEpochDay(ausencia.dataFim).format(formatter) }
    val periodoStr = if (inicioStr == fimStr) inicioStr else "$inicioStr - $fimStr"

    val corFundo = tipoTurno?.let { Color(it.cor) } ?: MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(corFundo),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tipoTurno?.emoji ?: tipoTurno?.abreviatura?.take(2) ?: "A")
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextoSemQuebra(
                        texto = tipoTurno?.nome ?: "Ausência",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = periodoStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (!ausencia.nota.isNullOrBlank()) {
                        Text(
                            text = ausencia.nota,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onRemover) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remover ausência",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Editar ausência",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
