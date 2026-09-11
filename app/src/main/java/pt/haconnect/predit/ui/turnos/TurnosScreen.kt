package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosScreen(
    modifier: Modifier = Modifier
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
    val rotacoes by rotacoesViewModel.rotacoes.collectAsState()
    val aplicacaoVigente by rotacoesViewModel.aplicacaoVigente.collectAsState()

    var tipoParaEditar by remember { mutableStateOf<TipoTurno?>(null) }
    var mostrarCriadorTurno by remember { mutableStateOf(false) }

    var rotacaoParaEditar by remember { mutableStateOf<RotacaoDetalhada?>(null) }
    var mostrarCriadorRotacao by remember { mutableStateOf(false) }
    var rotacaoParaAplicar by remember { mutableStateOf<RotacaoDetalhada?>(null) }

    var abaSelecionada by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Turnos e Rotações") }
                )
                TabRow(selectedTabIndex = abaSelecionada) {
                    Tab(
                        selected = abaSelecionada == 0,
                        onClick = { abaSelecionada = 0 },
                        text = { Text("Turnos") }
                    )
                    Tab(
                        selected = abaSelecionada == 1,
                        onClick = { abaSelecionada = 1 },
                        text = { Text("Rotações") }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (abaSelecionada == 0) {
                        mostrarCriadorTurno = true
                    } else {
                        mostrarCriadorRotacao = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (abaSelecionada == 0) "Adicionar tipo de turno" else "Adicionar rotação"
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (abaSelecionada == 0) {
                if (tiposTurno.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sem tipos de turno registados")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tiposTurno, key = { it.id }) { tipo ->
                            CartaoTipoTurno(
                                tipo = tipo,
                                onClick = { tipoParaEditar = tipo }
                            )
                        }
                    }
                }
            } else {
                if (rotacoes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sem rotações registadas. Toque em + para criar.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rotacoes, key = { it.id }) { rotacao ->
                            val ehVigente = aplicacaoVigente?.rotacaoId == rotacao.id
                            CartaoRotacao(
                                rotacao = rotacao,
                                ehVigente = ehVigente,
                                onClickEditar = {
                                    coroutineScope.launch {
                                        val detalhe = rotacoesViewModel.carregarDetalhes(rotacao.id)
                                        rotacaoParaEditar = detalhe
                                    }
                                },
                                onClickAplicar = {
                                    coroutineScope.launch {
                                        val detalhe = rotacoesViewModel.carregarDetalhes(rotacao.id)
                                        rotacaoParaAplicar = detalhe
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Dialogs for Turnos
        if (mostrarCriadorTurno) {
            EditorTipoTurnoDialog(
                tipo = null,
                onDismiss = { mostrarCriadorTurno = false },
                onSalvar = { novoTipo ->
                    turnosViewModel.salvarTipoTurno(novoTipo)
                    mostrarCriadorTurno = false
                }
            )
        }

        tipoParaEditar?.let { tipo ->
            EditorTipoTurnoDialog(
                tipo = tipo,
                onDismiss = { tipoParaEditar = null },
                onSalvar = { tipoAtualizado ->
                    turnosViewModel.salvarTipoTurno(tipoAtualizado)
                    tipoParaEditar = null
                }
            )
        }

        // Dialogs for Rotações
        if (mostrarCriadorRotacao) {
            val tiposAtivos = tiposTurno.filter { it.ativo }
            EditorRotacaoDialog(
                rotacaoExistente = null,
                slotsIniciais = emptyList(),
                tiposTurnoAtivos = tiposAtivos,
                onDismiss = { mostrarCriadorRotacao = false },
                onSalvar = { rotacao, slots ->
                    rotacoesViewModel.salvarRotacaoComSlots(rotacao, slots)
                    mostrarCriadorRotacao = false
                }
            )
        }

        rotacaoParaEditar?.let { detalhe ->
            val tiposAtivos = tiposTurno.filter { it.ativo }
            EditorRotacaoDialog(
                rotacaoExistente = detalhe.rotacao,
                slotsIniciais = detalhe.slots,
                tiposTurnoAtivos = tiposAtivos,
                onDismiss = { rotacaoParaEditar = null },
                onSalvar = { rotacao, slots ->
                    rotacoesViewModel.salvarRotacaoComSlots(rotacao, slots)
                    rotacaoParaEditar = null
                }
            )
        }

        rotacaoParaAplicar?.let { detalhe ->
            val tiposAtivos = tiposTurno.filter { it.ativo }
            AplicarRotacaoDialog(
                rotacaoDetalhada = detalhe,
                tiposTurnoAtivos = tiposAtivos,
                onDismiss = { rotacaoParaAplicar = null },
                onConfirmarAplicacao = { rotacaoId, dataAncora, validoDe ->
                    rotacoesViewModel.aplicarRotacao(rotacaoId, dataAncora, validoDe) {
                        rotacaoParaAplicar = null
                    }
                }
            )
        }
    }
}

@Composable
private fun CartaoTipoTurno(
    tipo: TipoTurno,
    onClick: () -> Unit
) {
    val alpha = if (tipo.ativo) 1f else 0.5f

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
                        .background(Color(tipo.cor).copy(alpha = alpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tipo.emoji ?: tipo.abreviatura.take(2))
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tipo.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                        )
                        Text(
                            text = "(${tipo.abreviatura})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        )
                        if (!tipo.ativo) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "Inativo",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    val textoHorario = if (tipo.categoria == CategoriaTurno.TRABALHO) {
                        val duracao = duracaoMinutos(tipo.inicioMin, tipo.fimMin, tipo.pausaMin)
                        "${formatarHoraMin(tipo.inicioMin)} - ${formatarHoraMin(tipo.fimMin)} (${formatarHoraMin(duracao)})"
                    } else {
                        "Sem horário"
                    }

                    Text(
                        text = textoHorario,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Editar tipo de turno",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun CartaoRotacao(
    rotacao: Rotacao,
    ehVigente: Boolean,
    onClickEditar: () -> Unit,
    onClickAplicar: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickEditar)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rotacao.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "${rotacao.comprimentoCiclo} Dias",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (ehVigente) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "Em vigor",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onClickAplicar) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Aplicar rotação",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Editar rotação",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
