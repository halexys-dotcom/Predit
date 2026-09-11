package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import pt.haconnect.predit.domain.model.Rotacao
import pt.haconnect.predit.domain.model.RotacaoSlot
import pt.haconnect.predit.domain.model.TipoTurno

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorRotacaoScreen(
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
    val tiposAtivos = remember(tiposTurno) { tiposTurno.filter { it.ativo } }
    val mapaTipos = remember(tiposTurno) { tiposTurno.associateBy { it.id } }

    var nome by remember { mutableStateOf("") }
    val slots = remember { mutableStateListOf<Long>() }
    var tipoSelecionado by remember { mutableStateOf<TipoTurno?>(null) }
    var carregado by remember { mutableStateOf(false) }

    LaunchedEffect(rotacaoId, tiposAtivos) {
        if (!carregado) {
            if (rotacaoId != 0L) {
                val detalhe = rotacoesViewModel.carregarDetalhes(rotacaoId)
                if (detalhe != null) {
                    nome = detalhe.rotacao.nome
                    slots.clear()
                    slots.addAll(detalhe.slots.sortedBy { it.posicao }.map { it.tipoTurnoId })
                }
            }
            if (tipoSelecionado == null && tiposAtivos.isNotEmpty()) {
                tipoSelecionado = tiposAtivos.first()
            }
            carregado = true
        }
    }

    val podeSalvar = nome.isNotBlank() && slots.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (rotacaoId == 0L) "Nova Rotação" else "Editar Rotação") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${slots.size} Dias",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
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
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Turno selecionado para pintar:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tiposAtivos, key = { it.id }) { tipo ->
                            val selecionado = tipoSelecionado?.id == tipo.id
                            FilterChip(
                                selected = selecionado,
                                onClick = { tipoSelecionado = tipo },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(tipo.cor))
                                    )
                                },
                                label = { Text(tipo.abreviatura) } // Compact abbreviation label
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onVoltar,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Voltar")
                        }

                        Button(
                            enabled = podeSalvar,
                            onClick = {
                                val rotacao = Rotacao(
                                    id = rotacaoId,
                                    nome = nome.trim(),
                                    comprimentoCiclo = slots.size
                                )
                                val listaSlots = slots.mapIndexed { idx, tipoId ->
                                    RotacaoSlot(
                                        rotacaoId = rotacaoId,
                                        posicao = idx,
                                        tipoTurnoId = tipoId
                                    )
                                }
                                rotacoesViewModel.salvarRotacaoComSlots(rotacao, listaSlots) {
                                    onVoltar()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Concluído")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome da rotação *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Grelha do Ciclo (7 colunas)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Calculate grid rows (7 columns)
            val totalSlots = slots.size
            val totalMostrados = totalSlots + 1
            val totalLinhas = (totalMostrados + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (linha in 0 until totalLinhas) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${linha + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(18.dp)
                        )

                        for (col in 0 until 7) {
                            val index = linha * 7 + col
                            Box(modifier = Modifier.weight(1f)) {
                                if (index < totalSlots) {
                                    val tipoId = slots[index]
                                    val tipo = mapaTipos[tipoId]
                                    val cor = tipo?.let { Color(it.cor) } ?: MaterialTheme.colorScheme.surfaceVariant

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(cor)
                                            .combinedClickable(
                                                onClick = {
                                                    tipoSelecionado?.let { slots[index] = it.id }
                                                },
                                                onLongClick = {
                                                    if (index == slots.size - 1) {
                                                        slots.removeAt(index)
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tipo?.emoji ?: tipo?.abreviatura ?: "?",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                } else if (index == totalSlots) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            .clickable {
                                                tipoSelecionado?.let { slots.add(it.id) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Adicionar slot",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
