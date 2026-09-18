package pt.haconnect.predit.ui.horario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.CicloJornadaRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.abreviarPosto
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.ui.turnos.CelulaPosto
import pt.haconnect.predit.ui.turnos.CelulaTipoTurno
import pt.haconnect.predit.ui.turnos.TextoSemQuebra
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorarioScreen(
    modifier: Modifier = Modifier,
    onNavegarParaEditarDiaReal: (Long) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val viewModel: HorarioViewModel = viewModel(
        factory = HorarioViewModel.Factory(
            diaRealRepository = DiaRealRepository(db.diaRealDao()),
            rotacaoRepository = RotacaoRepository(db.rotacaoDao()),
            tipoTurnoRepository = TipoTurnoRepository(db.tipoTurnoDao()),
            ausenciaRepository = AusenciaRepository(db.ausenciaDao()),
            planejamentoMesRepository = context.planejamentoMesRepository
        )
    )

    val conferenciaViewModel: ConferenciaViewModel = viewModel(
        factory = ConferenciaViewModel.Factory(
            diaRealRepository = DiaRealRepository(db.diaRealDao()),
            cicloJornadaRepository = context.cicloJornadaRepository
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val conferenciaUiState by conferenciaViewModel.uiState.collectAsState()
    var abaSelecionada by rememberSaveable { mutableStateOf(0) } // 0 = Mês, 1 = Conferência, 2 = Recibo

    val formatterData = remember { DateTimeFormatter.ofPattern("dd/MM") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Horário") }
                )
                TabRow(selectedTabIndex = abaSelecionada) {
                    Tab(
                        selected = abaSelecionada == 0,
                        onClick = { abaSelecionada = 0 },
                        text = { Text("Mês", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                    Tab(
                        selected = abaSelecionada == 1,
                        onClick = { abaSelecionada = 1 },
                        text = { Text("Conferência", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (abaSelecionada == 0) {
                // Aba Mês
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.mesAnterior() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val nomeMes = uiState.anoMesAtual.month.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString() }
                        TextoSemQuebra(
                            texto = "$nomeMes ${uiState.anoMesAtual.year}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = { viewModel.irParaHoje() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hoje", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    IconButton(onClick = { viewModel.mesSeguinte() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mês seguinte")
                    }
                }

                if (uiState.linhas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sem registos reais neste mês.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.linhas, key = { it.data.toEpochDay() }) { linha ->
                            val diaReal = linha.diaReal
                            if (diaReal != null) {
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavegarParaEditarDiaReal(linha.data.toEpochDay()) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = linha.data.format(formatterData),
                                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = linha.data.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "PT")).uppercase(),
                                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                val tipo = linha.tipoTurno
                                                val postoAbrev = abreviarPosto(linha.posto)
                                                if (tipo != null) {
                                                    if (postoAbrev != null) {
                                                        CelulaPosto(
                                                            texto = postoAbrev,
                                                            corTipo = Color(tipo.cor),
                                                            tamanho = 28.dp,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                    } else {
                                                        CelulaTipoTurno(tipo = tipo, tamanho = 28.dp, modifier = Modifier.size(28.dp))
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.size(28.dp))
                                                }
                                                Text(
                                                    text = "${formatarHoraMin(diaReal.inicioMin)}–${formatarHoraMin(diaReal.fimMin)}",
                                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            val dur = duracaoMinutos(diaReal.inicioMin, diaReal.fimMin, diaReal.pausaMin)
                                            Box(
                                                modifier = Modifier.widthIn(min = 56.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                TextoSemQuebra(
                                                    texto = formatarHoraMin(dur),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (!diaReal.posto.isNullOrBlank()) {
                                            Text(
                                                text = diaReal.posto,
                                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Rodapé Mês
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val totais = uiState.totais
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Horas reais registadas", style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Text(formatarHoraMin(totais.minutosReais), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(totais.rotuloPrevisto, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                if (totais.subtituloPrevisto != null) {
                                    Text(
                                        text = totais.subtituloPrevisto,
                                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Text(formatarHoraMin(totais.minutosPrevistos), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Diferença face à escala", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            val dif = totais.diferencaMinutos
                            val sinal = if (dif >= 0) "+" else "-"
                            val absDif = abs(dif)
                            Text(
                                text = "$sinal${formatarHoraMin(absDif)}",
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (dif >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                // Aba Conferência
                ConferenciaTabContent(
                    uiState = conferenciaUiState,
                    onSemestreAnterior = { conferenciaViewModel.semestreAnterior() },
                    onSemestreSeguinte = { conferenciaViewModel.semestreSeguinte() },
                    onHoje = { conferenciaViewModel.irParaHoje() },
                    onFecharSemestre = {
                        conferenciaViewModel.fecharSemestre { extras, saldo ->
                            val txtExtra = formatarHoraMin(extras)
                            val txtSaldo = if (saldo < 0) "−${formatarHoraMin(abs(saldo))}" else "00:00"
                            scope.launch {
                                snackbarHostState.showSnackbar("Semestre fechado. Extras pagos: $txtExtra. Défice limpo: $txtSaldo.")
                            }
                        }
                    },
                    onIrParaMes = { abaSelecionada = 0 }
                )
            }
        }
    }
}

@Composable
private fun ConferenciaTabContent(
    uiState: ConferenciaUiState,
    onSemestreAnterior: () -> Unit,
    onSemestreSeguinte: () -> Unit,
    onHoje: () -> Unit,
    onFecharSemestre: () -> Unit,
    onIrParaMes: () -> Unit
) {
    val (ano, semestre) = uiState.anoMesSelecionado
    val mesVazio = uiState.meses.all { it.realMinutos == 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header com navegação entre semestres
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSemestreAnterior) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semestre anterior")
            }

            TextoSemQuebra(
                texto = "${semestre}º Semestre $ano",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onHoje,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hoje", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }

                IconButton(onClick = onSemestreSeguinte) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semestre seguinte")
                }
            }
        }

        if (mesVazio && uiState.estado == EstadoCiclo.ATIVO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sem registos neste semestre",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedButton(onClick = onIrParaMes) {
                        Text("Ir para Horário → Mês")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.meses) { m ->
                    val yearMonth = try { YearMonth.parse(m.anoMes) } catch (_: Exception) { null }
                    val nomeMes = yearMonth?.month?.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))
                        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString() }
                        ?: m.anoMes

                    // Uma linha por mês: real · jornada · o que sobra (abate / pago / saldo)
                    val detalhe = when {
                        m.extraPagoMinutos > 0 && m.abateAoDeficeMinutos > 0 ->
                            "abate ${formatarHoraMin(m.abateAoDeficeMinutos)}  ·  +${formatarHoraMin(m.extraPagoMinutos)} pago"
                        m.extraPagoMinutos > 0 -> "+${formatarHoraMin(m.extraPagoMinutos)} pago"
                        m.abateAoDeficeMinutos > 0 -> "abate ${formatarHoraMin(m.abateAoDeficeMinutos)}"
                        m.saldoAcumuladoMinutos < 0 -> "saldo −${formatarHoraMin(abs(m.saldoAcumuladoMinutos))}"
                        else -> "00:00"
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextoSemQuebra(
                                texto = "$nomeMes $ano",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            TextoSemQuebra(
                                texto = "real ${formatarHoraMin(m.realMinutos)}  ·  jornada ${formatarHoraMin(m.jornadaMinutos)}  ·  $detalhe",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (uiState.estado == EstadoCiclo.ATIVO && uiState.podeFechar) {
            Button(
                onClick = onFecharSemestre,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Fechar semestre")
            }
        } else if (uiState.estado == EstadoCiclo.FECHADO && uiState.dataFecho != null) {
            val dfFecho = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val dtFechoStr = Instant.ofEpochMilli(uiState.dataFecho)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(dfFecho)

            Text(
                text = "Fechado em $dtFechoStr",
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Rodapé Ciclo
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Real total", style = MaterialTheme.typography.bodyMedium)
                    Text(formatarHoraMin(uiState.realTotalMinutos), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Jornada ciclo", style = MaterialTheme.typography.bodyMedium)
                    Text(formatarHoraMin(uiState.jornadaTotalMinutos), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Extras pagos", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "+${formatarHoraMin(uiState.extrasPagosTotalMinutos)}",
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Saldo final", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (uiState.estado == EstadoCiclo.FECHADO && uiState.dataFecho != null && uiState.saldoFinalMinutos < 0) {
                            val dfFecho = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                            val dtFechoStr = Instant.ofEpochMilli(uiState.dataFecho)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(dfFecho)
                            Text(
                                text = "(limpo em $dtFechoStr)",
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else if (uiState.estado == EstadoCiclo.ATIVO && uiState.saldoFinalMinutos < 0) {
                            Text(
                                text = "(provisório)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    val txtSaldo = if (uiState.saldoFinalMinutos < 0) {
                        "−${formatarHoraMin(abs(uiState.saldoFinalMinutos))}"
                    } else {
                        "00:00"
                    }
                    Text(
                        text = txtSaldo,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
