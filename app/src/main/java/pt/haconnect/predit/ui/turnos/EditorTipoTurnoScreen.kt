package pt.haconnect.predit.ui.turnos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.TipoTurnoRepository
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

fun CategoriaTurno.nomeFormatado(): String {
    return when (this) {
        CategoriaTurno.TRABALHO -> "Trabalho"
        CategoriaTurno.FOLGA -> "Folga"
        CategoriaTurno.FERIAS -> "Férias"
        CategoriaTurno.BAIXA -> "Baixa"
        CategoriaTurno.FERIADO -> "Feriado"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTipoTurnoScreen(
    tipoId: Long,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val turnosViewModel: TurnosViewModel = viewModel(
        factory = TurnosViewModel.Factory(TipoTurnoRepository(db.tipoTurnoDao()))
    )

    val tiposTurno by turnosViewModel.tiposTurno.collectAsState()

    val coresEmUso = remember(tiposTurno, tipoId) {
        tiposTurno
            .filter { it.id != tipoId && it.ativo }
            .associateBy({ it.cor }, { it.nome })
    }

    val primeiraCorLivre = remember(coresEmUso) {
        CORES_PREDEFINIDAS.firstOrNull { it !in coresEmUso } ?: CORES_PREDEFINIDAS.first()
    }

    var nome by rememberSaveable { mutableStateOf("") }
    var abreviatura by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf(CategoriaTurno.TRABALHO) }
    var cor by rememberSaveable { mutableStateOf(primeiraCorLivre) }
    var ativo by rememberSaveable { mutableStateOf(true) }

    var inicioHoraTexto by rememberSaveable { mutableStateOf("13:00") }
    var fimHoraTexto by rememberSaveable { mutableStateOf("21:00") }
    var pausaMinTexto by rememberSaveable { mutableStateOf("0") }

    var carregado by remember { mutableStateOf(false) }
    var mostrarDialogoDescartar by remember { mutableStateOf(false) }

    var nomeInicial by rememberSaveable { mutableStateOf("") }
    var abreviaturaInicial by rememberSaveable { mutableStateOf("") }
    var emojiInicial by rememberSaveable { mutableStateOf("") }
    var categoriaInicial by rememberSaveable { mutableStateOf(CategoriaTurno.TRABALHO) }
    var corInicial by rememberSaveable { mutableStateOf(primeiraCorLivre) }
    var ativoInicial by rememberSaveable { mutableStateOf(true) }
    var inicioHoraTextoInicial by rememberSaveable { mutableStateOf("13:00") }
    var fimHoraTextoInicial by rememberSaveable { mutableStateOf("21:00") }
    var pausaMinTextoInicial by rememberSaveable { mutableStateOf("0") }

    LaunchedEffect(tipoId, tiposTurno) {
        if (!carregado) {
            if (tipoId != 0L) {
                val tipoExistente = tiposTurno.find { it.id == tipoId }
                if (tipoExistente != null) {
                    nome = tipoExistente.nome
                    nomeInicial = tipoExistente.nome
                    abreviatura = tipoExistente.abreviatura
                    abreviaturaInicial = tipoExistente.abreviatura
                    emoji = tipoExistente.emoji ?: ""
                    emojiInicial = tipoExistente.emoji ?: ""
                    categoria = tipoExistente.categoria
                    categoriaInicial = tipoExistente.categoria
                    cor = tipoExistente.cor
                    corInicial = tipoExistente.cor
                    ativo = tipoExistente.ativo
                    ativoInicial = tipoExistente.ativo

                    val inicioFmt = formatarHoraMin(tipoExistente.inicioMin)
                    inicioHoraTexto = inicioFmt
                    inicioHoraTextoInicial = inicioFmt

                    val fimFmt = formatarHoraMin(tipoExistente.fimMin)
                    fimHoraTexto = fimFmt
                    fimHoraTextoInicial = fimFmt

                    val pausaFmt = tipoExistente.pausaMin.toString()
                    pausaMinTexto = pausaFmt
                    pausaMinTextoInicial = pausaFmt
                }
            }
            carregado = true
        }
    }

    val temAlteracoes = nome != nomeInicial ||
            abreviatura != abreviaturaInicial ||
            emoji != emojiInicial ||
            categoria != categoriaInicial ||
            cor != corInicial ||
            ativo != ativoInicial ||
            inicioHoraTexto != inicioHoraTextoInicial ||
            fimHoraTexto != fimHoraTextoInicial ||
            pausaMinTexto != pausaMinTextoInicial

    fun tentarSair() {
        if (temAlteracoes) {
            mostrarDialogoDescartar = true
        } else {
            onVoltar()
        }
    }

    BackHandler {
        tentarSair()
    }

    val inicioMin = parseHorarioParaMinutos(inicioHoraTexto) ?: 0
    val fimMin = parseHorarioParaMinutos(fimHoraTexto) ?: 0
    val pausaMin = pausaMinTexto.toIntOrNull() ?: 0

    val ehTrabalho = categoria == CategoriaTurno.TRABALHO
    val duracaoCalculadaMin = if (ehTrabalho) duracaoMinutos(inicioMin, fimMin, pausaMin) else 0

    val nomeValido = nome.trim().isNotEmpty()
    val abreviaturaValida = abreviatura.trim().isNotEmpty() && abreviatura.trim().length <= 6
    val horarioValido = !ehTrabalho || (parseHorarioParaMinutos(inicioHoraTexto) != null &&
            parseHorarioParaMinutos(fimHoraTexto) != null &&
            inicioMin != fimMin &&
            duracaoCalculadaMin > 0)

    val formValido = nomeValido && abreviaturaValida && horarioValido

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (tipoId == 0L) "Novo tipo de turno" else "Editar tipo de turno",
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
                        enabled = formValido,
                        onClick = {
                            turnosViewModel.salvarTipoTurno(
                                TipoTurno(
                                    id = tipoId,
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
                            onVoltar()
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
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome *") },
                singleLine = true,
                isError = !nomeValido && nome.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            Text("Categoria", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoriaTurno.entries.chunked(2).forEach { rowList ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowList.forEach { cat ->
                            FilterChip(
                                selected = categoria == cat,
                                onClick = { categoria = cat },
                                label = { Text(cat.nomeFormatado(), softWrap = false, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowList.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (ehTrabalho) {
                Text("Horário", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            Text("Cor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Aviso: Esta cor já está a ser utilizada pelo turno '$nomeTurnoComMesmaCor'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                    if (primeiraCorLivre != cor) {
                        OutlinedButton(
                            onClick = { cor = primeiraCorLivre },
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text("Usar cor livre sugerida", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Estado do tipo de turno", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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

        if (mostrarDialogoDescartar) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoDescartar = false },
                title = { Text("Descartar alterações?") },
                text = { Text("Existem alterações não guardadas. Deseja sair sem guardar?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            mostrarDialogoDescartar = false
                            onVoltar()
                        }
                    ) {
                        Text("Descartar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogoDescartar = false }) {
                        Text("Continuar a editar")
                    }
                }
            )
        }
    }
}

private fun parseHorarioParaMinutos(texto: String): Int? {
    val partes = texto.trim().split(":")
    if (partes.size != 2) return null
    val h = partes[0].toIntOrNull() ?: return null
    val m = partes[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}
