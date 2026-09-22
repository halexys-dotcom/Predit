package pt.haconnect.predit.ui.contrato

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import pt.haconnect.predit.data.repository.MunicipioRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.domain.calc.RegiaoIRS
import pt.haconnect.predit.domain.model.CATEGORIA_CCT_PADRAO
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.EstadoCivil
import pt.haconnect.predit.domain.model.Municipio
import pt.haconnect.predit.domain.model.RegimeHorario
import pt.haconnect.predit.ui.turnos.TextoSemQuebra
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
        factory = ContratoViewModel.Factory(
            repository = ContratoRepository(db.contratoDao()),
            parametrosCCTRepository = ParametrosCCTRepository(db.parametrosCCTDao())
        )
    )

    // Catálogo de municípios (Fase 10): semeado em PreditApplication, só se lê daqui.
    val municipioRepository = remember { MunicipioRepository(db.municipioDao()) }
    val municipios by municipioRepository.observarTodos().collectAsState(initial = emptyList())

    val contratoExistente by viewModel.contrato.collectAsState()
    // 13a: categorias do CCT para o selector (vêm dos parâmetros semeados).
    val categorias by viewModel.categorias.collectAsState()

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val hoje = LocalDate.now()

    var dataAdmissaoTexto by rememberSaveable { mutableStateOf("") }
    var regimeHorario by rememberSaveable { mutableStateOf(RegimeHorario.NORMAL) }
    var horarioSemanalTexto by rememberSaveable { mutableStateOf("40") }
    var estadoCivil by rememberSaveable { mutableStateOf(EstadoCivil.SOLTEIRO) }
    var numeroDependentesTexto by rememberSaveable { mutableStateOf("0") }
    var titularesTexto by rememberSaveable { mutableStateOf("1") }
    var regiao by rememberSaveable { mutableStateOf(RegiaoIRS.CONTINENTE) }
    var municipioId by rememberSaveable { mutableStateOf<Int?>(null) }
    // 13a: categoria do contrato. A chave (categoriaCodigo) é o que o recibo procura na tabela
    // salarial; categoriaNivel é o nome que se mostra, no formato que a app já usava.
    var categoriaCodigo by rememberSaveable { mutableStateOf(CATEGORIA_CCT_PADRAO) }
    var categoriaNivel by rememberSaveable { mutableStateOf("XIII, Vigilante Aeroportuário/APA-A") }

    var pickerMunicipioAberto by remember { mutableStateOf(false) }
    var pesquisaMunicipio by rememberSaveable { mutableStateOf("") }
    var pickerCategoriaAberto by remember { mutableStateOf(false) }
    var pesquisaCategoria by rememberSaveable { mutableStateOf("") }

    var carregado by remember { mutableStateOf(false) }

    val municipioSelecionado: Municipio? = municipios.firstOrNull { it.id == municipioId }

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
            municipioId = c.municipioId
            categoriaCodigo = c.categoriaCodigo
            categoriaNivel = c.categoriaNivel
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
                    // Fase 18d: título curto "Contrato" e sem bold (o cartão do Menu Mais continua
                    // com "Contrato de Trabalho" + subtítulo: lá tem espaço e não sofre truncagem).
                    TextoSemQuebra(
                        texto = "Contrato",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal
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
                                    categoriaNivel = categoriaNivel,
                                    categoriaCodigo = categoriaCodigo,
                                    dataAdmissao = dataAdmissaoParsed.toEpochDay(),
                                    regimeHorario = regimeHorario,
                                    horarioSemanalH = horarioSemanalTexto.toIntOrNull() ?: 40,
                                    numeroDependentes = numeroDependentesTexto.toIntOrNull() ?: 0,
                                    estadoCivil = estadoCivil,
                                    titulares = titularesTexto.toIntOrNull() ?: 1,
                                    primeiroArranqueConcluido = true,
                                    regiao = regiao,
                                    municipioId = municipioId
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
            // 13a: selector de categoria, com o mesmo padrão do município — Surface clicável em
            // vez de OutlinedTextField readOnly, que engolia o toque e nunca abria o picker.
            Surface(
                onClick = { pickerCategoriaAberto = true },
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = categoriaNivel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text("▾", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                text = "A categoria escolhe a tabela salarial do CCT: vencimento, subsídio de " +
                    "alimentação e subsídio de função do recibo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pickerCategoriaAberto) {
                val procurada = pesquisaCategoria.trim()
                val visiveis = categorias.filter { c ->
                    procurada.isEmpty() ||
                        c.nome.contains(procurada, ignoreCase = true) ||
                        c.codigo.contains(procurada, ignoreCase = true) ||
                        c.nivel.contains(procurada, ignoreCase = true)
                }

                ModalBottomSheet(onDismissRequest = { pickerCategoriaAberto = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Categoria / Nível",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = pesquisaCategoria,
                            onValueChange = { pesquisaCategoria = it },
                            label = { Text("Pesquisar (categoria, nível ou código)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (visiveis.isEmpty()) {
                            Text(
                                text = "Nenhuma categoria do CCT corresponde à pesquisa.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        ) {
                            items(visiveis, key = { it.codigo }) { categoria ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            categoriaCodigo = categoria.codigo
                                            categoriaNivel = "${categoria.nivel}, ${categoria.nome}"
                                            pickerCategoriaAberto = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(categoria.nome, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = "${categoria.nivel} · ${categoria.codigo}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (categoria.codigo == categoriaCodigo) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Categoria escolhida",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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

            Text("Município", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            // Surface clicável em vez de OutlinedTextField readOnly: o campo de texto engolia o
            // toque (fica para edição/seleção) e o picker nunca abria.
            Surface(
                onClick = { pickerMunicipioAberto = true },
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = municipioSelecionado?.nome ?: "Escolher município",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (municipioSelecionado == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text("▾", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                text = municipioSelecionado?.let { m ->
                    "Distrito: ${m.distrito} · Feriado: ${feriadoFormatado(m)}"
                } ?: "Sem município escolhido: o recibo usa só os feriados nacionais.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (municipioSelecionado?.verificado == false) {
                Text(
                    text = "⚠ Feriado municipal por verificar — confirma antes de confiar no cálculo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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

    if (pickerMunicipioAberto) {
        val procurado = pesquisaMunicipio.trim()
        val visiveis = municipios
            .filter {
                procurado.isEmpty() ||
                    it.nome.contains(procurado, ignoreCase = true) ||
                    it.distrito.contains(procurado, ignoreCase = true)
            }
            .sortedWith(compareBy({ ordemRegiao(it.regiao) }, { it.distrito }, { it.nome }))
        // groupBy preserva a ordem de encontro das chaves, por isso os cabeçalhos saem já
        // ordenados por região e distrito.
        val grupos = visiveis.groupBy { it.regiao to it.distrito }

        ModalBottomSheet(onDismissRequest = { pickerMunicipioAberto = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Município",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = pesquisaMunicipio,
                    onValueChange = { pesquisaMunicipio = it },
                    label = { Text("Pesquisar (nome ou distrito)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (grupos.isEmpty()) {
                    Text(
                        text = "Nenhum município no catálogo corresponde à pesquisa.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    grupos.forEach { (chave, lista) ->
                        val (regiaoDoGrupo, distritoDoGrupo) = chave
                        item(key = "cabecalho-$regiaoDoGrupo-$distritoDoGrupo") {
                            Text(
                                text = "${rotuloRegiao(regiaoDoGrupo)} · $distritoDoGrupo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(lista, key = { it.id }) { municipio ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        municipioId = municipio.id
                                        pickerMunicipioAberto = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(municipio.nome, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = feriadoFormatado(municipio) +
                                            if (municipio.verificado) "" else " · por verificar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (municipio.verificado) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
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

/** Ordem das regiões no picker: Continente primeiro, depois as ilhas. */
private fun ordemRegiao(regiao: String): Int = when (regiao) {
    "CONTINENTE" -> 0
    "ACORES" -> 1
    "MADEIRA" -> 2
    else -> 3
}

private fun rotuloRegiao(regiao: String): String = when (regiao) {
    "CONTINENTE" -> "Continente"
    "ACORES" -> "Açores"
    "MADEIRA" -> "Madeira"
    else -> regiao
}

/**
 * "13 de junho (Santo António)". O sufixo "(por verificar)" do catálogo é retirado do nome
 * aqui: quem o mostra é o aviso, com o `verificado` do município — no ecrã e na lista.
 */
private fun feriadoFormatado(municipio: Municipio): String {
    val nome = municipio.feriadoNome.removeSuffix(" (por verificar)")
    if (municipio.feriadoDia <= 0 || municipio.feriadoMes <= 0) {
        return "$nome (feriado móvel, sem data no catálogo)"
    }
    val mes = Month.of(municipio.feriadoMes)
        .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-PT"))
    return "${municipio.feriadoDia} de $mes ($nome)"
}
