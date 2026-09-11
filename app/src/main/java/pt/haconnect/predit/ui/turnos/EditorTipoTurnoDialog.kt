package pt.haconnect.predit.ui.turnos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.TipoTurno

val CORES_PREDEFINIDAS = listOf(
    0xFFC0392BL, // Vermelho
    0xFF1ABC9CL, // Verde-água
    0xFF2980B9L, // Azul
    0xFF8E44ADL, // Roxo
    0xFFE67E22L, // Laranja
    0xFF16A085L, // Verde escuro
    0xFF2C3E50L, // Cinza escuro
    0xFFD35400L  // Amarelo/Acastanhado
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTipoTurnoDialog(
    tipo: TipoTurno?,
    tiposExistentes: List<TipoTurno> = emptyList(),
    onDismiss: () -> Unit,
    onSalvar: (TipoTurno) -> Unit
) {
    val coresEmUso = remember(tiposExistentes, tipo) {
        tiposExistentes
            .filter { it.id != tipo?.id && it.ativo }
            .associateBy({ it.cor }, { it.nome })
    }

    val primeiraCorLivre = remember(coresEmUso) {
        CORES_PREDEFINIDAS.firstOrNull { it !in coresEmUso } ?: CORES_PREDEFINIDAS.first()
    }

    var nome by remember { mutableStateOf(tipo?.nome ?: "") }
    var abreviatura by remember { mutableStateOf(tipo?.abreviatura ?: "") }
    var emoji by remember { mutableStateOf(tipo?.emoji ?: "") }
    var categoria by remember { mutableStateOf(tipo?.categoria ?: CategoriaTurno.TRABALHO) }
    var cor by remember { mutableStateOf(tipo?.cor ?: primeiraCorLivre) }
    var ativo by remember { mutableStateOf(tipo?.ativo ?: true) }

    var inicioHoraTexto by remember { mutableStateOf(formatarHoraMin(tipo?.inicioMin ?: (13 * 60))) }
    var fimHoraTexto by remember { mutableStateOf(formatarHoraMin(tipo?.fimMin ?: (21 * 60))) }
    var pausaMinTexto by remember { mutableStateOf((tipo?.pausaMin ?: 0).toString()) }

    val inicioMin = parseHorarioParaMinutos(inicioHoraTexto) ?: 0
    val fimMin = parseHorarioParaMinutos(fimHoraTexto) ?: 0
    val pausaMin = pausaMinTexto.toIntOrNull() ?: 0

    val ehTrabalho = categoria == CategoriaTurno.TRABALHO
    val duracaoCalculadaMin = if (ehTrabalho) duracaoMinutos(inicioMin, fimMin, pausaMin) else 0

    val nomeValido = nome.isNotBlank()
    val abreviaturaValida = abreviatura.isNotBlank() && abreviatura.length <= 6
    val horarioValido = !ehTrabalho || (parseHorarioParaMinutos(inicioHoraTexto) != null &&
            parseHorarioParaMinutos(fimHoraTexto) != null &&
            inicioMin != fimMin &&
            duracaoCalculadaMin > 0)

    val formValido = nomeValido && abreviaturaValida && horarioValido

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tipo == null) "Novo tipo de turno" else "Editar tipo de turno") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = dpToArrangement(12)
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome *") },
                    singleLine = true,
                    isError = !nomeValido && nome.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = dpToArrangement(8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = abreviatura,
                        onValueChange = { if (it.length <= 6) abreviatura = it },
                        label = { Text("Abreviatura (máx 6) *") },
                        singleLine = true,
                        isError = !abreviaturaValida && abreviatura.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )
                }

                Text("Categoria", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = dpToArrangement(4)) {
                    CategoriaTurno.entries.chunked(2).forEach { rowList ->
                        Row(
                            horizontalArrangement = dpToArrangement(8),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowList.forEach { cat ->
                                FilterChip(
                                    selected = categoria == cat,
                                    onClick = { categoria = cat },
                                    label = { Text(cat.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (ehTrabalho) {
                    Text("Horário", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = dpToArrangement(8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = inicioHoraTexto,
                            onValueChange = { inicioHoraTexto = it },
                            label = { Text("Início (HH:mm)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = fimHoraTexto,
                            onValueChange = { fimHoraTexto = it },
                            label = { Text("Fim (HH:mm)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = pausaMinTexto,
                        onValueChange = { pausaMinTexto = it },
                        label = { Text("Pausa (minutos)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Duração calculada:")
                            Text(
                                text = formatarHoraMin(maxOf(0, duracaoCalculadaMin)),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Text("Cor", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = dpToArrangement(8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CORES_PREDEFINIDAS.forEach { corHex ->
                        val emUso = corHex in coresEmUso
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(corHex))
                                .border(
                                    width = if (cor == corHex) 3.dp else 0.dp,
                                    color = if (cor == corHex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { cor = corHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (emUso && cor != corHex) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                            }
                        }
                    }
                }

                val nomeTurnoComMesmaCor = coresEmUso[cor]
                if (nomeTurnoComMesmaCor != null) {
                    Text(
                        text = "Aviso: Esta cor já está a ser utilizada pelo turno '$nomeTurnoComMesmaCor'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Estado do tipo de turno", style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (ativo) "Ativo" else "Inativo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Switch(
                            checked = ativo,
                            onCheckedChange = { ativo = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = formValido,
                onClick = {
                    onSalvar(
                        TipoTurno(
                            id = tipo?.id ?: 0L,
                            nome = nome.trim(),
                            abreviatura = abreviatura.trim(),
                            cor = cor,
                            emoji = emoji.trim().ifEmpty { null },
                            inicioMin = if (ehTrabalho) inicioMin else 0,
                            fimMin = if (ehTrabalho) fimMin else 0,
                            pausaMin = if (ehTrabalho) pausaMin else 0,
                            categoria = categoria,
                            ativo = ativo
                        )
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun parseHorarioParaMinutos(texto: String): Int? {
    val partes = texto.trim().split(":")
    if (partes.size != 2) return null
    val h = partes[0].toIntOrNull() ?: return null
    val m = partes[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun dpToArrangement(dp: Int) = Arrangement.spacedBy(dp.dp)
