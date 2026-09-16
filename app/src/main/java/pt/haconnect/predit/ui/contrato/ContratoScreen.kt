package pt.haconnect.predit.ui.contrato

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.domain.calc.RegiaoIRS
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.EstadoCivil
import pt.haconnect.predit.domain.model.RegimeHorario
import pt.haconnect.predit.ui.turnos.TextoSemQuebra
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContratoScreen(
    modifier: Modifier = Modifier,
    ehPrimeiroArranque: Boolean = false,
    onVoltar: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val viewModel: ContratoViewModel = viewModel(
        factory = ContratoViewModel.Factory(ContratoRepository(db.contratoDao()))
    )

    val contratoExistente by viewModel.contrato.collectAsState()

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val hoje = LocalDate.now()

    var dataAdmissaoTexto by rememberSaveable { mutableStateOf("") }
    var regimeHorario by rememberSaveable { mutableStateOf(RegimeHorario.NORMAL) }
    var horarioSemanalTexto by rememberSaveable { mutableStateOf("40") }
    var estadoCivil by rememberSaveable { mutableStateOf(EstadoCivil.SOLTEIRO) }
    var numeroDependentesTexto by rememberSaveable { mutableStateOf("0") }
    var titularesTexto by rememberSaveable { mutableStateOf("1") }
    var regiao by rememberSaveable { mutableStateOf(RegiaoIRS.CONTINENTE) }

    var carregado by remember { mutableStateOf(false) }

    LaunchedEffect(contratoExistente) {
        if (!carregado && contratoExistente != null) {
            val c = contratoExistente!!
            dataAdmissaoTexto = c.dataAdmissao?.let { LocalDate.ofEpochDay(it).format(formatter) } ?: ""
            regimeHorario = c.regimeHorario
            horarioSemanalTexto = c.horarioSemanalH.toString()
            estadoCivil = c.estadoCivil
            numeroDependentesTexto = c.numeroDependentes.toString()
            titularesTexto = c.titulares.toString()
            regiao = c.regiao
            carregado = true
        }
    }

    val dataAdmissaoParsed = parseData(dataAdmissaoTexto)
    val dataNoFuturo = dataAdmissaoParsed != null && dataAdmissaoParsed.isAfter(hoje)
    val dataValida = dataAdmissaoParsed != null && !dataNoFuturo

    val podeSalvar = dataValida

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    TextoSemQuebra(
                        texto = "Contrato de Trabalho",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (!ehPrimeiroArranque) {
                        IconButton(onClick = onVoltar) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    Button(
                        enabled = podeSalvar,
                        onClick = {
                            if (dataAdmissaoParsed != null) {
                                val c = ContratoUtilizador(
                                    id = 1,
                                    categoriaNivel = "XIII, Vigilante Aeroportuário/APA-A",
                                    dataAdmissao = dataAdmissaoParsed.toEpochDay(),
                                    regimeHorario = regimeHorario,
                                    horarioSemanalH = horarioSemanalTexto.toIntOrNull() ?: 40,
                                    numeroDependentes = numeroDependentesTexto.toIntOrNull() ?: 0,
                                    estadoCivil = estadoCivil,
                                    titulares = titularesTexto.toIntOrNull() ?: 1,
                                    primeiroArranqueConcluido = true,
                                    regiao = regiao
                                )
                                viewModel.guardar(c) {
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
            if (ehPrimeiroArranque) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Bem-vindo ao Predit!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Por favor, indica a tua data de admissão para configurar a tua escala e direitos contratuais.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Text("Categoria / Nível Professional", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = "XIII, Vigilante Aeroportuário/APA-A",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Data de Admissão *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = dataAdmissaoTexto,
                onValueChange = { dataAdmissaoTexto = it },
                label = { Text("DD/MM/YYYY *") },
                singleLine = true,
                isError = dataAdmissaoTexto.isNotEmpty() && !dataValida,
                supportingText = if (dataNoFuturo) {
                    { Text("A data de admissão não pode ser no futuro.", color = MaterialTheme.colorScheme.error) }
                } else if (dataAdmissaoTexto.isNotEmpty() && dataAdmissaoParsed == null) {
                    { Text("Formato de data inválido (ex: 15/07/2004).", color = MaterialTheme.colorScheme.error) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Regime de Horário", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = regimeHorario == RegimeHorario.NORMAL,
                    onClick = { regimeHorario = RegimeHorario.NORMAL },
                    label = { Text("Normal") }
                )
                FilterChip(
                    selected = regimeHorario == RegimeHorario.ADAPTABILIDADE,
                    onClick = { regimeHorario = RegimeHorario.ADAPTABILIDADE },
                    label = { Text("Adaptabilidade") }
                )
            }

            Text("Região Fiscal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val rotulos = mapOf(
                    RegiaoIRS.CONTINENTE to "Continente",
                    RegiaoIRS.ACORES to "Açores",
                    RegiaoIRS.MADEIRA to "Madeira"
                )
                RegiaoIRS.entries.forEach { r ->
                    FilterChip(
                        selected = regiao == r,
                        onClick = { regiao = r },
                        label = { Text(rotulos.getValue(r)) }
                    )
                }
            }

            Text("Horário Semanal (Horas)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = horarioSemanalTexto,
                onValueChange = { horarioSemanalTexto = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Estado Civil", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EstadoCivil.entries.forEach { estado ->
                    FilterChip(
                        selected = estadoCivil == estado,
                        onClick = { estadoCivil = estado },
                        label = { Text(estado.nomeFormatado()) }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dependentes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = numeroDependentesTexto,
                        onValueChange = { numeroDependentesTexto = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Titulares", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = titularesTexto,
                        onValueChange = { titularesTexto = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun EstadoCivil.nomeFormatado(): String {
    return when (this) {
        EstadoCivil.SOLTEIRO -> "Solteiro(a)"
        EstadoCivil.CASADO -> "Casado(a)"
        EstadoCivil.VIUVO -> "Viúvo(a)"
        EstadoCivil.DIVORCIADO -> "Divorciado(a)"
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
