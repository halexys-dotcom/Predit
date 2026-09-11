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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.haconnect.predit.domain.model.Rotacao
import pt.haconnect.predit.domain.model.RotacaoSlot
import pt.haconnect.predit.domain.model.TipoTurno

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorRotacaoDialog(
    rotacaoExistente: Rotacao?,
    slotsIniciais: List<RotacaoSlot>,
    tiposTurnoAtivos: List<TipoTurno>,
    onDismiss: () -> Unit,
    onSalvar: (Rotacao, List<RotacaoSlot>) -> Unit
) {
    var nome by remember { mutableStateOf(rotacaoExistente?.nome ?: "") }
    val slots = remember { mutableStateListOf<Long>().apply { addAll(slotsIniciais.sortedBy { it.posicao }.map { it.tipoTurnoId }) } }
    var tipoSelecionado by remember { mutableStateOf(tiposTurnoAtivos.firstOrNull()) }

    val mapaTipos = remember(tiposTurnoAtivos) { tiposTurnoAtivos.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (rotacaoExistente == null) "Nova Rotação" else "Editar Rotação")
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${slots.size} Dias",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome da rotação *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Grelha do Ciclo (7 colunas)", style = MaterialTheme.typography.labelMedium)

                // Render slots grid in rows of 7
                val totalSlotsMostrados = maxOf(slots.size + 1, 14)
                val totalLinhas = (totalSlotsMostrados + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (linha in 0 until totalLinhas) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${linha + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(16.dp)
                            )

                            for (col in 0 until 7) {
                                val index = linha * 7 + col
                                val ehSlotPreenchido = index < slots.size
                                val ehProximoVazio = index == slots.size

                                if (ehSlotPreenchido) {
                                    val tipoId = slots[index]
                                    val tipo = mapaTipos[tipoId]
                                    val cor = tipo?.let { Color(it.cor) } ?: MaterialTheme.colorScheme.surfaceVariant

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
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
                                            text = tipo?.emoji ?: tipo?.abreviatura?.take(2) ?: "?",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                } else if (ehProximoVazio) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                tipoSelecionado?.let { slots.add(it.id) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                Text("Paleta de Turnos Ativos", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tiposTurnoAtivos, key = { it.id }) { tipo ->
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
                            label = { Text("${tipo.emoji ?: ""} ${tipo.nome} (${tipo.abreviatura})") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = nome.isNotBlank() && slots.isNotEmpty(),
                onClick = {
                    val rotacao = Rotacao(
                        id = rotacaoExistente?.id ?: 0L,
                        nome = nome.trim(),
                        comprimentoCiclo = slots.size
                    )
                    val listaSlots = slots.mapIndexed { idx, tipoId ->
                        RotacaoSlot(
                            rotacaoId = rotacao.id,
                            posicao = idx,
                            tipoTurnoId = tipoId
                        )
                    }
                    onSalvar(rotacao, listaSlots)
                }
            ) {
                Text("Concluído")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Voltar")
            }
        }
    )
}
