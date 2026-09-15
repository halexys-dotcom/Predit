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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.abreviarPosto
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.ui.turnos.CelulaPosto
import pt.haconnect.predit.ui.turnos.CelulaTipoTurno
import pt.haconnect.predit.ui.turnos.TextoSemQuebra
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

    val uiState by viewModel.uiState.collectAsState()
    var abaSelecionada by remember { mutableIntStateOf(0) } // 0 = Mês, 1 = Conferência

    val formatterData = remember { DateTimeFormatter.ofPattern("dd/MM") }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Horário") }
                )
                TabRow(selectedTabIndex = abaSelecionada) {
                    Tab(
                        selected = abaSelecionada == 0,
                        onClick = { abaSelecionada = 0 },
                        text = { Text("Mês") }
                    )
                    Tab(
                        selected = abaSelecionada == 1,
                        onClick = { /* Conferência desativada até à Fase 7 */ },
                        enabled = false,
                        text = { Text("Conferência") }
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
            // A aba Conferência está desativada, por isso abaSelecionada é sempre 0
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
                            Text("Hoje", style = MaterialTheme.typography.labelMedium)
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
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = linha.data.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "PT")).uppercase(),
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
                            Text("Horas reais registadas", style = MaterialTheme.typography.bodyMedium)
                            Text(formatarHoraMin(totais.minutosReais), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(totais.rotuloPrevisto, style = MaterialTheme.typography.bodyMedium)
                                if (totais.subtituloPrevisto != null) {
                                    Text(
                                        text = totais.subtituloPrevisto,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Text(formatarHoraMin(totais.minutosPrevistos), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Diferença face à escala", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            val dif = totais.diferencaMinutos
                            val sinal = if (dif >= 0) "+" else "-"
                            val absDif = abs(dif)
                            Text(
                                text = "$sinal${formatarHoraMin(absDif)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (dif >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
