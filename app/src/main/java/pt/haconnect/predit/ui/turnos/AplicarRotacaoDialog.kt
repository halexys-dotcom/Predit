package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.domain.calc.posicaoNoCiclo
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.RotacaoDetalhada
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AplicarRotacaoDialog(
    rotacaoDetalhada: RotacaoDetalhada,
    tiposTurnoAtivos: List<TipoTurno>,
    onDismiss: () -> Unit,
    onConfirmarAplicacao: (rotacaoId: Long, dataAncora: Long, validoDe: Long) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dataAtual = LocalDate.now()

    var dataAncoraTexto by remember { mutableStateOf(dataAtual.format(formatter)) }
    var validoDeTexto by remember { mutableStateOf(dataAtual.format(formatter)) }

    val dataAncoraParsed = parseData(dataAncoraTexto)
    val validoDeParsed = parseData(validoDeTexto)

    val formValido = dataAncoraParsed != null && validoDeParsed != null && rotacaoDetalhada.slots.isNotEmpty()

    val mapaTipos = remember(tiposTurnoAtivos) { tiposTurnoAtivos.associateBy { it.id } }
    val mapaSlots = remember(rotacaoDetalhada.slots) { rotacaoDetalhada.slots.associateBy { it.posicao } }

    val cicloSize = rotacaoDetalhada.rotacao.comprimentoCiclo

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplicar Rotação — ${rotacaoDetalhada.rotacao.nome}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
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
                        style = MaterialTheme.typography.labelMedium,
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
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(diasPreVisualizacao) { (dia, tipo) ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = dia.format(formatter),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = dia.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "PT")).uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
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
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(tipo.cor))
                                            )
                                            Text(
                                                text = "${tipo.emoji ?: ""} ${tipo.nome}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Text("Sem turno", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = formValido,
                onClick = {
                    if (dataAncoraParsed != null && validoDeParsed != null) {
                        onConfirmarAplicacao(
                            rotacaoDetalhada.rotacao.id,
                            dataAncoraParsed.toEpochDay(),
                            validoDeParsed.toEpochDay()
                        )
                    }
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
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
