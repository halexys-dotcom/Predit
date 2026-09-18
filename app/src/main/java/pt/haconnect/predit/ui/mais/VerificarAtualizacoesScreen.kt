package pt.haconnect.predit.ui.mais

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.BuildConfig
import pt.haconnect.predit.data.update.ApkDownloader
import pt.haconnect.predit.data.update.UpdateRepository
import pt.haconnect.predit.domain.update.VersionManifest

/**
 * Verificar se há versão nova e instalá-la (Fase 11d.2).
 *
 * Estado vem todo do ViewModel: o ecrã não fala com a rede nem com o DownloadManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificarAtualizacoesScreen(onVoltar: () -> Unit) {
    val context = LocalContext.current
    val viewModel: VerificarAtualizacoesViewModel = viewModel(
        factory = VerificarAtualizacoesViewModel.Factory(
            updateRepository = UpdateRepository(),
            apkDownloader = ApkDownloader(context.applicationContext),
            versionCodeAtual = BuildConfig.VERSION_CODE,
            versionNameAtual = BuildConfig.VERSION_NAME
        )
    )
    val estado by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Atualizações") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CartaoVersaoInstalada(
                versaoNome = estado.versaoAtualNome,
                versaoCode = estado.versaoAtualCode,
                ocupada = estado.estado is EstadoAtualizacao.AVerificar ||
                    estado.estado is EstadoAtualizacao.ADescarregar,
                onVerificar = { viewModel.verificar() }
            )

            when (val atual = estado.estado) {
                is EstadoAtualizacao.Inicial ->
                    TextoAjuda("Toca em \"Verificar agora\" para procurar atualizações.")

                is EstadoAtualizacao.AVerificar -> LinhaAProcessar("A verificar...")

                is EstadoAtualizacao.SemAtualizacao ->
                    CartaoSucesso("Estás na versão mais recente.")

                is EstadoAtualizacao.Disponivel -> CartaoDisponivel(
                    manifest = atual.manifest,
                    onDescarregar = { viewModel.descarregar(atual.manifest.apkUrl) }
                )

                is EstadoAtualizacao.ADescarregar -> CartaoProgresso(atual.progresso)

                is EstadoAtualizacao.Descarregado -> CartaoDescarregado(
                    onInstalar = { viewModel.instalar(context) }
                )

                is EstadoAtualizacao.Erro -> CartaoErro(
                    mensagem = atual.mensagem,
                    onTentar = { viewModel.verificar() }
                )
            }
        }
    }
}

@Composable
private fun CartaoVersaoInstalada(
    versaoNome: String,
    versaoCode: Int,
    ocupada: Boolean,
    onVerificar: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Versão instalada",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$versaoNome (código $versaoCode)",
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onVerificar,
                enabled = !ocupada,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verificar-atualizacoes")
            ) {
                Text("Verificar agora")
            }
        }
    }
}

@Composable
private fun TextoAjuda(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LinhaAProcessar(texto: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CartaoSucesso(texto: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32)
            )
            Text(text = texto, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CartaoDisponivel(manifest: VersionManifest, onDescarregar: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Nova versão disponível",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = manifest.versionName,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (manifest.changelog.isNotBlank()) {
                Text(
                    text = manifest.changelog,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = onDescarregar,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("descarregar-atualizacao")
            ) {
                Text("Descarregar")
            }
        }
    }
}

@Composable
private fun CartaoProgresso(progresso: Float) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "A descarregar... ${(progresso * 100).toInt()} %",
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { progresso.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CartaoDescarregado(onInstalar: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Download concluído.", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "O Android vai pedir autorização para instalar e a app reinicia no fim.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onInstalar,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("instalar-atualizacao")
            ) {
                Text("Instalar agora")
            }
        }
    }
}

@Composable
private fun CartaoErro(mensagem: String, onTentar: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(text = mensagem, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onTentar,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tentar-outra-vez")
            ) {
                Text("Tentar outra vez")
            }
        }
    }
}
