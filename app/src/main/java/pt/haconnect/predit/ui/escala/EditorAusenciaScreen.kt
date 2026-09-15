package pt.haconnect.predit.ui.escala

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.AplicacaoVigente
import pt.haconnect.predit.domain.calc.diasConsumidos
import pt.haconnect.predit.domain.calc.posicaoNoCiclo
import pt.haconnect.predit.domain.calc.projetarDia
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.TipoTurno
import pt.haconnect.predit.ui.turnos.calcularCorTexto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorAusenciaScreen(
    ausenciaId: Long,
    dataInicioInicialEpochDay: Long? = null,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val turnosViewModel: pt.haconnect.predit.ui.turnos.TurnosViewModel = viewModel(
        factory = pt.haconnect.predit.ui.turnos.TurnosViewModel.Factory(TipoTurnoRepository(db.tipoTurnoDao()))
    )
    val rotacaoRepository = remember { RotacaoRepository(db.rotacaoDao()) }
    val ausenciaRepository = remember { AusenciaRepository(db.ausenciaDao()) }

    val tiposTurno by turnosViewModel.tiposTurno.collectAsState()
    val tiposAusenciaAtivos = remember(tiposTurno) {
        tiposTurno.filter {
            it.ativo && (it.categoria == CategoriaTurno.FERIAS ||
                    it.categoria == CategoriaTurno.BAIXA ||
                    it.categoria == CategoriaTurno.FERIADO)
        }
    }
    val mapaTipos = remember(tiposTurno) { tiposTurno.associateBy { it.id } }

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dataAtual = LocalDate.now()
    val dataInicial = dataInicioInicialEpochDay?.let { LocalDate.ofEpochDay(it) } ?: dataAtual

    var tipoSelecionado by remember { mutableStateOf<TipoTurno?>(null) }
    var dataInicioTexto by rememberSaveable { mutableStateOf(dataInicial.format(formatter)) }
    var dataFimTexto by rememberSaveable { mutableStateOf(dataInicial.format(formatter)) }
    var nota by rememberSaveable { mutableStateOf("") }

    var carregado by remember { mutableStateOf(false) }
    var mostrarDialogoDescartar by remember { mutableStateOf(false) }

    var aplicacoesVigentes by remember { mutableStateOf<List<AplicacaoVigente>>(emptyList()) }
    var ausenciasExistentes by remember { mutableStateOf<List<Ausencia>>(emptyList()) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(ausenciaId, tiposAusenciaAtivos) {
        if (!carregado) {
            aplicacoesVigentes = rotacaoRepository.obterAplicacoesVigentes()
            ausenciasExistentes = ausenciaRepository.observarTodas().first()

            if (ausenciaId != 0L) {
                val existente = ausenciasExistentes.find { it.id == ausenciaId }
                if (existente != null) {
                    tipoSelecionado = mapaTipos[existente.tipoTurnoId]
                    dataInicioTexto = LocalDate.ofEpochDay(existente.dataInicio).format(formatter)
                    dataFimTexto = LocalDate.ofEpochDay(existente.dataFim).format(formatter)
                    nota = existente.nota ?: ""
                }
            }
            if (tipoSelecionado == null && tiposAusenciaAtivos.isNotEmpty()) {
                tipoSelecionado = tiposAusenciaAtivos.first()
            }
            carregado = true
        }
    }

    val dataInicioParsed = parseData(dataInicioTexto)
    val dataFimParsed = parseData(dataFimTexto)

    val datasValidas = dataInicioParsed != null && dataFimParsed != null && !dataFimParsed.isBefore(dataInicioParsed)

    val sobrepoeOutraAusencia = remember(dataInicioParsed, dataFimParsed, ausenciasExistentes, ausenciaId) {
        if (dataInicioParsed != null && dataFimParsed != null) {
            val inicioEpoch = dataInicioParsed.toEpochDay()
            val fimEpoch = dataFimParsed.toEpochDay()
            ausenciasExistentes.any { ap ->
                ap.id != ausenciaId && !(fimEpoch < ap.dataInicio || inicioEpoch > ap.dataFim)
            }
        } else false
    }

    val podeSalvar = tipoSelecionado != null && datasValidas

    fun tentarSair() {
        onVoltar()
    }

    BackHandler {
        tentarSair()
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (ausenciaId == 0L) "Marcar Ausência" else "Editar Ausência",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { tentarSair() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    Button(
                        enabled = podeSalvar,
                        onClick = {
                            if (tipoSelecionado != null && dataInicioParsed != null && dataFimParsed != null) {
                                scope.launch {
                                    ausenciaRepository.salvar(
                                        Ausencia(
                                            id = ausenciaId,
                                            tipoTurnoId = tipoSelecionado!!.id,
                                            dataInicio = dataInicioParsed.toEpochDay(),
                                            dataFim = dataFimParsed.toEpochDay(),
                                            nota = nota.trim().ifEmpty { null }
                                        )
                                    )
                                    onVoltar()
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Guardar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Tipo de Ausência *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tiposAusenciaAtivos.forEach { tipo ->
                    val selecionado = tipoSelecionado?.id == tipo.id
                    val corTexto = calcularCorTexto(tipo.cor)

                    FilterChip(
                        selected = selecionado,
                        onClick = { tipoSelecionado = tipo },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(tipo.cor))
                            )
                        },
                        label = {
                            Text(
                                text = "${tipo.emoji ?: ""} ${tipo.nome}",
                                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                                color = if (selecionado) corTexto else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(tipo.cor),
                            selectedLabelColor = corTexto
                        )
                    )
                }
            }

            Text("Período *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = dataInicioTexto,
                    onValueChange = { dataInicioTexto = it },
                    label = { Text("Início (DD/MM/YYYY)") },
                    singleLine = true,
                    isError = dataInicioParsed == null,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = dataFimTexto,
                    onValueChange = { dataFimTexto = it },
                    label = { Text("Fim (DD/MM/YYYY)") },
                    singleLine = true,
                    isError = dataFimParsed == null || (dataInicioParsed != null && dataFimParsed.isBefore(dataInicioParsed)),
                    modifier = Modifier.weight(1f)
                )
            }

            if (dataInicioParsed != null && dataFimParsed != null && dataFimParsed.isBefore(dataInicioParsed)) {
                Text(
                    text = "A data de fim deve ser igual ou posterior à data de início.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (sobrepoeOutraAusencia) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aviso: O intervalo selecionado sobrepõe-se a uma ausência já registada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = nota,
                onValueChange = { nota = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Pré-visualização dos dias e contador
            val inicioLocal = dataInicioParsed
            val fimLocal = dataFimParsed
            if (datasValidas && inicioLocal != null && fimLocal != null) {
                val diasLista = remember(inicioLocal, fimLocal, aplicacoesVigentes) {
                    var curr: LocalDate = inicioLocal
                    val res = mutableListOf<Pair<LocalDate, TipoTurno?>>()
                    while (!curr.isAfter(fimLocal)) {
                        val proj = projetarDia(curr.toEpochDay(), aplicacoesVigentes)
                        val tipo = proj.tipoTurnoId?.let { mapaTipos[it] }
                        res.add(curr to tipo)
                        curr = curr.plusDays(1)
                    }
                    res
                }

                val feriados = remember { emptySet<Long>() }
                val inicioEpoch = inicioLocal.toEpochDay()
                val fimEpoch = fimLocal.toEpochDay()

                val totalDias = diasLista.size
                val numDiasConsumidos = diasConsumidos(inicioEpoch, fimEpoch, feriados)
                val numDiasComTurnoEscala = diasLista.count { (dia, tipo) ->
                    tipo?.categoria == CategoriaTurno.TRABALHO && dia.toEpochDay() !in feriados
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$totalDias ${if (totalDias == 1) "dia" else "dias"} de calendário",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$numDiasConsumidos ${if (numDiasConsumidos == 1) "dia consumido" else "dias consumidos"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$numDiasComTurnoEscala ${if (numDiasComTurnoEscala == 1) "dia" else "dias"} com turno na escala",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text("Pré-visualização das substituições:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    diasLista.forEach { (dia, tipoProjetado) ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = dia.format(formatter),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = dia.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "PT")).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val nomeProjetado = tipoProjetado?.nome ?: "Sem escala"
                                    val eFimDeSemana = dia.dayOfWeek == DayOfWeek.SATURDAY ||
                                                       dia.dayOfWeek == DayOfWeek.SUNDAY
                                    val categoriaProjetada = tipoProjetado?.categoria
                                    val eSubstituivel = tipoProjetado == null || categoriaProjetada == CategoriaTurno.TRABALHO

                                    val texto = when {
                                        !eSubstituivel -> nomeProjetado
                                        eFimDeSemana -> "$nomeProjetado Folga"
                                        else -> "$nomeProjetado → ${tipoSelecionado?.nome ?: "Ausência"}"
                                    }

                                    Text(
                                        text = texto,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
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
