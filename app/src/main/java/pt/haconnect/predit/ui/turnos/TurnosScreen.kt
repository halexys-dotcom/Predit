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
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosScreen(
    modifier: Modifier = Modifier,
    onNavegarParaEditorRotacao: (Long?) -> Unit = {},
    onNavegarParaAplicarRotacao: (Long) -> Unit = {}
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
    val rotacoesDetalhadas by rotacoesViewModel.rotacoesDetalhadas.collectAsState()
    val aplicacaoVigente by rotacoesViewModel.aplicacaoVigente.collectAsState()

    val mapaTipos = remember(tiposTurno) { tiposTurno.associateBy { it.id } }

    var tipoParaEditar by remember { mutableStateOf<TipoTurno?>(null) }
    var mostrarCriadorTurno by remember { mutableStateOf(false) }

    var abaSelecionada by remember { mutableIntStateOf(0) }

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
                        onNavegarParaEditorRotacao(null)
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
                if (rotacoesDetalhadas.isEmpty()) {
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
                        items(rotacoesDetalhadas, key = { it.rotacao.id }) { detalhe ->
                            val ehVigente = aplicacaoVigente?.rotacaoId == detalhe.rotacao.id
                            CartaoRotacao(
                                rotacaoDetalhada = detalhe,
                                mapaTipos = mapaTipos,
                                ehVigente = ehVigente,
                                onClickEditar = {
                                    onNavegarParaEditorRotacao(detalhe.rotacao.id)
                                },
                                onClickAplicar = {
                                    onNavegarParaAplicarRotacao(detalhe.rotacao.id)
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
    rotacaoDetalhada: RotacaoDetalhada,
    mapaTipos: Map<Long, TipoTurno>,
    ehVigente: Boolean,
    onClickEditar: () -> Unit,
    onClickAplicar: () -> Unit
) {
    val rotacao = rotacaoDetalhada.rotacao
    val slots = rotacaoDetalhada.slots.sortedBy { it.posicao }

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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

                if (slots.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        slots.take(10).forEach { slot ->
                            val tipo = mapaTipos[slot.tipoTurnoId]
                            val cor = tipo?.let { Color(it.cor) } ?: MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(cor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tipo?.emoji ?: tipo?.abreviatura?.take(1) ?: "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (slots.size > 10) {
                            Text(
                                text = "+${slots.size - 10}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
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
