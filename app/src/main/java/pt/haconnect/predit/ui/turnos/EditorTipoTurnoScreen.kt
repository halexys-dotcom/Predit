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

/**
 * 12c D — paleta expandida de 8 para 24 cores (3 linhas de 8 no FlowRow do editor).
 * A primeira cor livre desta lista é também a sugestão automática para um tipo novo.
 */
val PALETA_CORES = listOf(
    // Linha 1 — vermelhos, laranjas, amarelos
    0xFFC62828L, // vermelho escuro
    0xFFE53935L, // vermelho
    0xFFEF6C00L, // laranja escuro
    0xFFFB8C00L, // laranja
    0xFFF9A825L, // amarelo escuro
    0xFFFDD835L, // amarelo
    0xFF827717L, // azeitona
    0xFF9E9D24L, // verde-lima escuro

    // Linha 2 — verdes, teals, azuis claros
    0xFF2E7D32L, // verde escuro
    0xFF43A047L, // verde
    0xFF00897BL, // teal escuro
    0xFF00ACC1L, // ciano
    0xFF0288D1L, // azul claro escuro
    0xFF039BE5L, // azul claro
    0xFF3949ABL, // índigo
    0xFF5E35B1L, // roxo escuro

    // Linha 3 — roxos, rosas, neutros
    0xFF8E24AAL, // roxo
    0xFFAB47BCL, // lilás
    0xFFC2185BL, // rosa escuro
    0xFFD81B60L, // rosa
    0xFF6D4C41L, // castanho
    0xFF546E7AL, // azul-acinzentado
    0xFF37474FL, // cinza escuro
    0xFF212121L  // quase preto
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        PALETA_CORES.firstOrNull { it !in coresEmUso } ?: PALETA_CORES.first()
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
            if (tipoId == 0L) {
                carregado = true
            } else {
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
                    carregado = true
                }
            }
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
                        text = "Editor",
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
            // 12c D — 24 cores em linhas de 8 (a paleta anterior tinha 8 numa única linha).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 8,
                modifier = Modifier.fillMaxWidth()
            ) {
                PALETA_CORES.forEach { corHex ->
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
