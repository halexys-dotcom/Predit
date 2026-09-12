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
    onNavegarParaEditorTipoTurno: (Long?) -> Unit = {},
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
                        onNavegarParaEditorTipoTurno(null)
                    } else {
                        onNavegarParaEditorRotacao(null)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                        contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tiposTurno, key = { it.id }) { tipo ->
                            CartaoTipoTurno(
                                tipo = tipo,
                                onClick = { onNavegarParaEditorTipoTurno(tipo.id) }
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
                        contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 88.dp),
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
                            BadgeStatus(
                                texto = "Inativo",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
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
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (slots.isNotEmpty()) {
                        MiniGrelhaPreview3x3(slots = slots, mapaTipos = mapaTipos)
                    }

                    TextoSemQuebra(
                        texto = rotacao.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onClickAplicar,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextoSemQuebra(
                            texto = "Aplicar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Editar rotação",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (slots.isNotEmpty()) 46.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeStatus(
                    texto = "${rotacaoDetalhada.comprimentoReal} Dias",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium
                )

                if (ehVigente) {
                    BadgeStatus(
                        texto = "Em vigor",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniGrelhaPreview3x3(
    slots: List<RotacaoSlot>,
    mapaTipos: Map<Long, TipoTurno>,
    modifier: Modifier = Modifier
) {
    val maxSlots = minOf(slots.size, 9)
    val slotsPrevisualizados = slots.take(maxSlots)
    val numLinhas = (maxSlots + 2) / 3

    Column(
        modifier = modifier
            .width(36.dp)
            .height(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        for (linha in 0 until numLinhas) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (col in 0 until 3) {
                    val index = linha * 3 + col
                    if (index < slotsPrevisualizados.size) {
                        val slot = slotsPrevisualizados[index]
                        val tipo = mapaTipos[slot.tipoTurnoId]
                        val cor = tipo?.let { Color(it.cor) } ?: MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(cor)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}
