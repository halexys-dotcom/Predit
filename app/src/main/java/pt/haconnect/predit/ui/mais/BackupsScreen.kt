package pt.haconnect.predit.ui.mais

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.domain.model.BackupInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cópias de segurança internas (Fase 11b): criar, restaurar e apagar.
 *
 * Restaurar não mexe já no ficheiro da BD: deixa-o marcado e reinicia a app — o restauro entra
 * no arranque seguinte, antes de o Room abrir a base de dados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(onVoltar: () -> Unit) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val viewModel: BackupsViewModel = viewModel(
        factory = BackupsViewModel.Factory(context.backupManager)
    )
    val estado by viewModel.estado.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var paraRestaurar by remember { mutableStateOf<BackupInfo?>(null) }
    var paraApagar by remember { mutableStateOf<BackupInfo?>(null) }

    LaunchedEffect(estado.mensagem) {
        estado.mensagem?.let { texto ->
            snackbarHostState.showSnackbar(texto)
            viewModel.mensagemMostrada()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backups") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.criarManual() },
                        enabled = !estado.aProcessar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("criar-backup")
                    ) {
                        Text("Criar backup agora")
                    }
                    Text(
                        text = "Backups automáticos: últimos 10 (1 por arranque)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Localização: ${viewModel.pasta}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (estado.backups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ainda não há backups.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(estado.backups, key = { it.caminho }) { backup ->
                        CartaoBackup(
                            backup = backup,
                            aProcessar = estado.aProcessar,
                            onRestaurar = { paraRestaurar = backup },
                            onApagar = { paraApagar = backup }
                        )
                    }
                }
            }
        }

        paraRestaurar?.let { backup ->
            DialogoRestaurar(
                backup = backup,
                onCancelar = { paraRestaurar = null },
                onConfirmar = {
                    paraRestaurar = null
                    viewModel.restaurar(backup) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Backup marcado. A reiniciar…")
                            delay(1_000L)
                            // Arranque limpo: o PreditApplication aplica o restauro antes de
                            // o Room abrir a base de dados.
                            Runtime.getRuntime().exit(0)
                        }
                    }
                }
            )
        }

        paraApagar?.let { backup ->
            DialogoApagar(
                backup = backup,
                onCancelar = { paraApagar = null },
                onConfirmar = {
                    paraApagar = null
                    viewModel.apagar(backup)
                }
            )
        }
    }
}

@Composable
private fun DialogoRestaurar(
    backup: BackupInfo,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Restaurar backup?") },
        text = {
            Text(
                "Substituir os dados atuais pelo backup de ${dataHora(backup.criadoEm)}? " +
                    "A app vai ser reiniciada."
            )
        },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Restaurar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoApagar(
    backup: BackupInfo,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Apagar backup?") },
        text = { Text("O ficheiro ${backup.nome} sai do telefone. Não se desfaz.") },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text("Apagar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun CartaoBackup(
    backup: BackupInfo,
    aProcessar: Boolean,
    onRestaurar: () -> Unit,
    onApagar: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup-${backup.nome}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = backup.nome,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${dataHora(backup.criadoEm)} · ${tamanhoLegivel(backup.tamanhoBytes)} · " +
                    "v${backup.versionBd} · ${if (backup.ehAutomatico) "automático" else "manual"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestaurar, enabled = !aProcessar) { Text("Restaurar") }
                TextButton(onClick = onApagar, enabled = !aProcessar) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private val FORMATO_DATA_HORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("pt", "PT"))

private fun dataHora(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(FORMATO_DATA_HORA)

private fun tamanhoLegivel(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(Locale("pt", "PT"), bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(Locale("pt", "PT"), bytes / 1_024.0)
    else -> "$bytes B"
}
