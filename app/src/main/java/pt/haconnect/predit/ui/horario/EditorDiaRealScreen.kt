package pt.haconnect.predit.ui.horario

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.coroutines.launch
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.duracaoMinutos
import pt.haconnect.predit.domain.calc.formatarHoraMin
import pt.haconnect.predit.domain.calc.projetarDia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.TipoTurno
import pt.haconnect.predit.ui.turnos.calcularCorTexto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorDiaRealScreen(
    epochDay: Long,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val diaRealRepository = remember { DiaRealRepository(db.diaRealDao()) }
    val tipoTurnoRepository = remember { TipoTurnoRepository(db.tipoTurnoDao()) }
    val rotacaoRepository = remember { RotacaoRepository(db.rotacaoDao()) }

    val dataLocal = remember(epochDay) { LocalDate.ofEpochDay(epochDay) }
    val formatterData = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val diaSemanaExtenso = remember(dataLocal) {
        dataLocal.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "PT")) else it.toString() }
    }

    val tiposTurno by tipoTurnoRepository.observarTodos().collectAsState(initial = emptyList())
    val tiposTrabalhoAtivos = remember(tiposTurno) {
        tiposTurno.filter { it.ativo && it.categoria == CategoriaTurno.TRABALHO }
    }
    val mapaTipos = remember(tiposTurno) { tiposTurno.associateBy { it.id } }

    var registoExistente by remember { mutableStateOf<DiaReal?>(null) }
    var tipoSelecionado by remember { mutableStateOf<TipoTurno?>(null) }
    var semTipoSelecionado by remember { mutableStateOf(false) }

    var horaInicioTexto by rememberSaveable { mutableStateOf("13:00") }
    var horaFimTexto by rememberSaveable { mutableStateOf("21:00") }
    var pausaTexto by rememberSaveable { mutableStateOf("0") }
    var nota by rememberSaveable { mutableStateOf("") }
    var posto by rememberSaveable { mutableStateOf("") }

    var carregado by remember { mutableStateOf(false) }
    var mostrarDialogoApagar by remember { mutableStateOf(false) }
    var tipoProjetadoNoDia by remember { mutableStateOf<TipoTurno?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(epochDay, tiposTrabalhoAtivos) {
        if (!carregado) {
            val aplicacoes = rotacaoRepository.obterTodasAplicacoes()
            val proj = projetarDia(epochDay, aplicacoes)
            tipoProjetadoNoDia = proj.tipoTurnoId?.let { mapaTipos[it] }

            val ex = diaRealRepository.obterPorData(epochDay)
            registoExistente = ex
            if (ex != null) {
                if (ex.tipoTurnoId != null) {
                    tipoSelecionado = mapaTipos[ex.tipoTurnoId]
                    semTipoSelecionado = false
                } else {
                    tipoSelecionado = null
                    semTipoSelecionado = true
                }
                horaInicioTexto = formatarHoraMin(ex.inicioMin)
                horaFimTexto = formatarHoraMin(ex.fimMin)
                pausaTexto = ex.pausaMin.toString()
                nota = ex.nota ?: ""
                posto = ex.posto ?: ""
            } else if (tipoProjetadoNoDia != null && tipoProjetadoNoDia!!.categoria == CategoriaTurno.TRABALHO) {
                tipoSelecionado = tipoProjetadoNoDia
                horaInicioTexto = formatarHoraMin(tipoProjetadoNoDia!!.inicioMin)
                horaFimTexto = formatarHoraMin(tipoProjetadoNoDia!!.fimMin)
                pausaTexto = tipoProjetadoNoDia!!.pausaMin.toString()
            } else if (tiposTrabalhoAtivos.isNotEmpty()) {
                tipoSelecionado = tiposTrabalhoAtivos.first()
                horaInicioTexto = formatarHoraMin(tipoSelecionado!!.inicioMin)
                horaFimTexto = formatarHoraMin(tipoSelecionado!!.fimMin)
                pausaTexto = tipoSelecionado!!.pausaMin.toString()
            }
            carregado = true
        }
    }

    val inicioMin = parseHoraMin(horaInicioTexto)
    val fimMin = parseHoraMin(horaFimTexto)
    val pausaMin = pausaTexto.toIntOrNull() ?: 0

    val horarioValido = inicioMin != null && fimMin != null

    BackHandler { onVoltar() }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (registoExistente != null) "Editar Registo Real" else "Registar Horas",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onVoltar() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (registoExistente != null) {
                        IconButton(onClick = { mostrarDialogoApagar = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Apagar")
                        }
                    }
                    Button(
                        enabled = horarioValido,
                        onClick = {
                            if (inicioMin != null && fimMin != null) {
                                scope.launch {
                                    val idExistente = registoExistente?.id ?: 0L
                                    diaRealRepository.guardar(
                                        DiaReal(
                                            id = idExistente,
                                            data = epochDay,
                                            tipoTurnoId = if (semTipoSelecionado) null else tipoSelecionado?.id,
                                            inicioMin = inicioMin,
                                            fimMin = fimMin,
                                            pausaMin = pausaMin,
                                            nota = nota.trim().ifEmpty { null },
                                            origem = "MANUAL",
                                            posto = posto.trim().ifEmpty { null }
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
            OutlinedTextField(
                value = "${dataLocal.format(formatterData)} — $diaSemanaExtenso",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Data") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Tipo de Turno *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tiposTrabalhoAtivos.forEach { tipo ->
                    val selecionado = !semTipoSelecionado && tipoSelecionado?.id == tipo.id
                    val corTexto = calcularCorTexto(tipo.cor)

                    FilterChip(
                        selected = selecionado,
                        onClick = {
                            semTipoSelecionado = false
                            tipoSelecionado = tipo
                            if (horaInicioTexto.isEmpty() || horaInicioTexto == "00:00") {
                                horaInicioTexto = formatarHoraMin(tipo.inicioMin)
                                horaFimTexto = formatarHoraMin(tipo.fimMin)
                                pausaTexto = tipo.pausaMin.toString()
                            }
                        },
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
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                                color = if (selecionado) corTexto else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(tipo.cor),
                            selectedLabelColor = corTexto
                        )
                    )
                }

                FilterChip(
                    selected = semTipoSelecionado,
                    onClick = {
                        semTipoSelecionado = true
                    },
                    label = { Text("Sem tipo (avulso)") }
                )
            }

            Text("Horário de Trabalho *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = horaInicioTexto,
                    onValueChange = { horaInicioTexto = it },
                    label = { Text("Início (HH:MM)") },
                    singleLine = true,
                    isError = inicioMin == null,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = horaFimTexto,
                    onValueChange = { horaFimTexto = it },
                    label = { Text("Fim (HH:MM)") },
                    singleLine = true,
                    isError = fimMin == null,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = pausaTexto,
                onValueChange = { pausaTexto = it },
                label = { Text("Pausa (minutos)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = nota,
                onValueChange = { nota = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = posto,
                onValueChange = { posto = it },
                label = { Text("Posto (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (horarioValido) {
                val duracaoRealMin = duracaoMinutos(inicioMin, fimMin, pausaMin)
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
                            text = "Duração real: ${formatarHoraMin(duracaoRealMin)}",
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (tipoProjetadoNoDia != null && tipoProjetadoNoDia!!.categoria == CategoriaTurno.TRABALHO) {
                            val duracaoProjMin = duracaoMinutos(
                                tipoProjetadoNoDia!!.inicioMin,
                                tipoProjetadoNoDia!!.fimMin,
                                tipoProjetadoNoDia!!.pausaMin
                            )
                            val difMin = duracaoRealMin - duracaoProjMin
                            val sinal = if (difMin >= 0) "+" else "-"
                            val absDif = kotlin.math.abs(difMin)
                            Text(
                                text = "Projetado: ${formatarHoraMin(duracaoProjMin)} · Real: ${formatarHoraMin(duracaoRealMin)} · Comparação: $sinal${formatarHoraMin(absDif)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            if (mostrarDialogoApagar) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogoApagar = false },
                    title = { Text("Apagar registo?") },
                    text = { Text("O registo de ${dataLocal.format(formatterData)} vai ser eliminado. Esta ação não pode ser anulada.") },
                    confirmButton = {
                        TextButton(onClick = {
                            mostrarDialogoApagar = false
                            scope.launch {
                                registoExistente?.let { diaRealRepository.apagar(it.id) }
                                onVoltar()
                            }
                        }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarDialogoApagar = false }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}

private fun parseHoraMin(texto: String): Int? {
    return try {
        val partes = texto.trim().split(":")
        if (partes.size != 2) return null
        val h = partes[0].toIntOrNull() ?: return null
        val m = partes[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        h * 60 + m
    } catch (_: Exception) {
        null
    }
}
