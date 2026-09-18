package pt.haconnect.predit.ui.escala

// ATENÇÃO: qualquer texto em espaço apertado deve ser TextoSemQuebra
// (maxLines = 1, softWrap = false). Já houve 6 bugs de quebra neste projeto:
// "Hoj/e", "TRABALH/O", "Calendári/o", "B/M", "Em/vigo/r", "08:/0/0".
// Exceção: notas e frases de texto livre podem quebrar entre palavras.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.MunicipioRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.abreviarPosto
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.ui.turnos.CelulaTipoTurno
import pt.haconnect.predit.ui.turnos.TextoSemQuebra
import pt.haconnect.predit.ui.turnos.nomeFormatado
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscalaScreen(
    modifier: Modifier = Modifier,
    onNavegarParaTurnos: () -> Unit = {},
    onNavegarParaMarcarAusencia: (Long) -> Unit = {},
    onNavegarParaRegistoReal: (Long) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val viewModel: EscalaViewModel = viewModel(
        factory = EscalaViewModel.Factory(
            rotacaoRepository = RotacaoRepository(db.rotacaoDao()),
            tipoTurnoRepository = TipoTurnoRepository(db.tipoTurnoDao()),
            ausenciaRepository = AusenciaRepository(db.ausenciaDao()),
            diaRealRepository = DiaRealRepository(db.diaRealDao()),
            planejamentoMesRepository = context.planejamentoMesRepository,
            municipioRepository = MunicipioRepository(db.municipioDao()),
            contratoRepository = ContratoRepository(db.contratoDao())
        )
    )

    val uiState by viewModel.uiState.collectAsState()

    val diasDaSemana = remember { listOf("S", "T", "Q", "Q", "S", "S", "D") }
    var diaSelecionadoParaDetalhe by remember { mutableStateOf<DiaMesEscala?>(null) }
    var ausenciaParaRemoverId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Escala") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!uiState.temEscalaAplicada) {
                // B4 — Estado Vazio
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "Ainda não há escala. Cria uma rotação em Turnos e aplica-a.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = onNavegarParaTurnos,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ir para Turnos")
                            }
                        }
                    }
                }
            } else {
                // B1 — Vista Mensal Sem Scroll
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Cabeçalho com navegação de mês e centragem ótica perfeita
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reserva de espaço à esquerda (48dp, mesma largura do IconButton "Hoje")
                        Box(
                            modifier = Modifier.width(48.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Espaço reservado para centragem ótica
                        }

                        // Centro: [←] [Mês Ano] [→]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { viewModel.mesAnterior() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Mês anterior"
                                )
                            }

                            TextoSemQuebra(
                                texto = uiState.anoMesAtual.nomeMesFormatado(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(onClick = { viewModel.mesSeguinte() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Mês seguinte"
                                )
                            }
                        }

                        // Direita: Ícone "Hoje" (largura fixa 48dp)
                        Box(
                            modifier = Modifier.width(48.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (uiState.anoMesAtual != YearMonth.now()) {
                                IconButton(onClick = { viewModel.irParaHoje() }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Ir para hoje",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Cabeçalho dos dias da semana (S T Q Q S S D)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        diasDaSemana.forEach { dia ->
                            TextoSemQuebra(
                                texto = dia,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                    // Grelha do mês em 7 colunas (com suporte a swipe horizontal)
                    var dragOffset by remember { mutableFloatStateOf(0f) }

                    val semanas = remember(uiState.diasGrelha) {
                        uiState.diasGrelha.chunked(7)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(uiState.anoMesAtual) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset > 50f) {
                                            viewModel.mesAnterior()
                                        } else if (dragOffset < -50f) {
                                            viewModel.mesSeguinte()
                                        }
                                        dragOffset = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragOffset += dragAmount
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        semanas.forEach { semana ->
                            key(semana.firstOrNull()?.data?.toEpochDay() ?: 0L) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    semana.forEach { dia ->
                                        key(dia.data.toEpochDay()) {
                                            CelulaDiaCalendario(
                                                dia = dia,
                                                onClick = { diaSelecionadoParaDetalhe = dia },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Rodapé do Mês ( Totais: Turnos, Folgas, Ausências, Horas )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Turnos",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${uiState.estatisticas.numTurnos}",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(1.dp)
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Folgas",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${uiState.estatisticas.numFolgas}",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(1.dp)
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Ausências",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${uiState.estatisticas.numAusencias}",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(1.dp)
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Total Horas",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                val horas = uiState.estatisticas.totalMinutosTrabalho / 60
                                val minutos = uiState.estatisticas.totalMinutosTrabalho % 60
                                val textoHoras = if (minutos > 0) "${horas}h ${minutos}m" else "${horas}h"
                                Text(
                                    text = textoHoras,
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (uiState.estatisticas.fonte == FonteEstatistica.PLANEJAMENTO) {
                            Text(
                                text = "Fonte: planeamento importado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // B3 — Detalhe do Dia (ModalBottomSheet)
        diaSelecionadoParaDetalhe?.let { dia ->
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { diaSelecionadoParaDetalhe = null },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val dataExtenso = remember(dia.data) {
                        val df = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "PT"))
                        val dataStr = dia.data.format(df)
                        val diaSemana = dia.data.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString() }
                        "$dataStr — $diaSemana"
                    }

                    Text(
                        text = dataExtenso,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    val tipo = dia.tipoTurnoEfetivo
                    if (tipo != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CelulaTipoTurno(tipo = tipo, tamanho = 40.dp, modifier = Modifier.size(40.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = tipo.nome,
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Categoria: ${tipo.categoria.nomeFormatado()}",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (dia.ausencia != null && dia.tipoTurnoProjetado != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Turno projetado substituído: ${dia.tipoTurnoProjetado.nome} (${dia.tipoTurnoProjetado.abreviatura})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (tipo.categoria == CategoriaTurno.TRABALHO) {
                                    val duracao = duracaoMinutos(tipo.inicioMin, tipo.fimMin, tipo.pausaMin)
                                    Text(
                                        text = "Horário: ${formatarHoraMin(tipo.inicioMin)} - ${formatarHoraMin(tipo.fimMin)}",
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Duração: ${formatarHoraMin(duracao)}",
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "Sem horário",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sem escala definida",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    if (dia.diaReal != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Registo real",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CelulaTipoTurno(
                                        tipo = dia.tipoTurnoChip,
                                        tamanho = 28.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = dia.tipoTurnoChip?.nome ?: "Sem tipo",
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                val dur = duracaoMinutos(dia.diaReal.inicioMin, dia.diaReal.fimMin, dia.diaReal.pausaMin)
                                Text(
                                    text = "Horário: ${formatarHoraMin(dia.diaReal.inicioMin)} - ${formatarHoraMin(dia.diaReal.fimMin)}",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Duração: ${formatarHoraMin(dur)}",
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (!dia.diaReal.posto.isNullOrBlank()) {
                                    Text(
                                        text = "Posto: ${dia.diaReal.posto}",
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                if (!dia.diaReal.nota.isNullOrEmpty()) {
                                    Text(
                                        text = "Nota: ${dia.diaReal.nota}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Ação de Registo Real
                    OutlinedButton(
                        onClick = {
                            val d = dia.data.toEpochDay()
                            diaSelecionadoParaDetalhe = null
                            onNavegarParaRegistoReal(d)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (dia.diaReal != null) "Editar registo" else "Registar horas")
                    }

                    // Ação de Ausência
                    val ausBruta = dia.ausenciaBruta
                    if (ausBruta == null) {
                        OutlinedButton(
                            onClick = {
                                val d = dia.data.toEpochDay()
                                diaSelecionadoParaDetalhe = null
                                onNavegarParaMarcarAusencia(d)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Marcar ausência")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                ausenciaParaRemoverId = ausBruta.id
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remover ausência")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        ausenciaParaRemoverId?.let { id ->
            AlertDialog(
                onDismissRequest = { ausenciaParaRemoverId = null },
                title = { Text("Remover ausência?") },
                text = { Text("Tem a certeza que deseja remover esta ausência?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            ausenciaParaRemoverId = null
                            viewModel.removerAusencia(id) {
                                diaSelecionadoParaDetalhe = null
                            }
                        }
                    ) {
                        Text("Remover", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ausenciaParaRemoverId = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

/**
 * Fase 12b — chip largo da célula do calendário (estilo Supershift): ocupa a largura da célula
 * em vez de ser um círculo. Precedência de cores: feriado (azul) > hoje (branco) > cor do tipo.
 */
@Composable
private fun ChipDiaCalendario(
    texto: String,
    corFundo: Color,
    corTexto: Color,
    modifier: Modifier = Modifier,
    ehHoje: Boolean = false,
    ehFeriado: Boolean = false
) {
    val corFundoFinal = when {
        ehFeriado -> Color(0xFF2196F3).copy(alpha = 0.55f) // azul Material com transparência
        ehHoje -> Color.White.copy(alpha = 0.85f)
        else -> corFundo
    }
    val corTextoFinal = when {
        ehFeriado -> Color.White
        ehHoje -> Color.Black
        else -> corTexto
    }
    val tamanhoFonte = when {
        texto.length <= 3 -> 10.sp
        texto.length <= 5 -> 9.sp
        else -> 7.5.sp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(corFundoFinal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            fontSize = tamanhoFonte,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = corTextoFinal,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                letterSpacing = (-0.3).sp
            )
        )
    }
}

@Composable
private fun CelulaDiaCalendario(
    dia: DiaMesEscala,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (dia.pertenceAoMesAtual) 1f else 0.38f
    val tipoChip = dia.tipoTurnoChip

    Surface(
        color = when {
            dia.ehHoje -> Color.White.copy(alpha = 0.15f)
            // Feriado sem turno projetado: a célula ganha a tinta azul (senão ficava vazia).
            dia.ehFeriado && tipoChip == null -> Color(0xFF2196F3).copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .alpha(alpha)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${dia.data.dayOfMonth}",
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = if (dia.ehHoje) FontWeight.Bold else FontWeight.Normal,
                    color = if (dia.ehHoje) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                if (tipoChip != null) {
                    val textoPosto = abreviarPosto(dia.diaReal?.posto)
                    val textoChip = textoPosto ?: tipoChip.abreviatura
                    val corTipo = Color(tipoChip.cor)
                    ChipDiaCalendario(
                        texto = textoChip,
                        corFundo = corTipo.copy(alpha = 0.85f),
                        corTexto = if (corTipo.luminance() > 0.55f) Color.Black else Color.White,
                        ehHoje = dia.ehHoje,
                        ehFeriado = dia.ehFeriado,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(modifier = Modifier.height(22.dp))
                }
            }

            // Marcador de ausência (turno projetado por baixo)
            if (dia.ausencia != null && dia.tipoTurnoProjetado != null) {
                Box(
                    modifier = Modifier
                        .padding(3.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

private fun YearMonth.nomeMesFormatado(): String {
    val mesNome = this.month.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))
    val mesCapitalizado = mesNome.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString() }
    return "$mesCapitalizado ${this.year}"
}
