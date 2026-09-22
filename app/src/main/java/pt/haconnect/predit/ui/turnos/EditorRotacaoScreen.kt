package pt.haconnect.predit.ui.turnos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.gerarResumoPadrao
import pt.haconnect.predit.domain.model.Rotacao
import pt.haconnect.predit.domain.model.RotacaoSlot
import pt.haconnect.predit.domain.model.TipoTurno

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun EditorRotacaoScreen(
    rotacaoId: Long,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val turnosViewModel: TurnosViewModel = viewModel(
        factory = TurnosViewModel.Factory(TipoTurnoRepository(db.tipoTurnoDao()))
    )
    val rotacoesViewModel: RotacoesViewModel = viewModel(
        factory = RotacoesViewModel.Factory(RotacaoRepository(db.rotacaoDao()))
    )

    val tiposTurno by turnosViewModel.tiposTurno.collectAsState()
    val rotacoes by rotacoesViewModel.rotacoes.collectAsState()
    val tiposAtivos = remember(tiposTurno) { tiposTurno.filter { it.ativo } }
    val mapaTipos = remember(tiposTurno) { tiposTurno.associateBy { it.id } }

    var nome by rememberSaveable { mutableStateOf("") }
    var nomeInicial by rememberSaveable { mutableStateOf("") }

    val slots = remember { mutableStateListOf<Long>() }
    var slotsIniciaisList by remember { mutableStateOf<List<Long>>(emptyList()) }

    var tipoSelecionado by remember { mutableStateOf<TipoTurno?>(null) }
    var carregado by remember { mutableStateOf(false) }

    var alturaPainelPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val alturaPainelDp = remember(alturaPainelPx, density) {
        with(density) { alturaPainelPx.toDp() }
    }

    var mostrarDialogoLimpar by remember { mutableStateOf(false) }
    var mostrarDialogoDescartar by remember { mutableStateOf(false) }

    LaunchedEffect(rotacaoId, tiposAtivos) {
        if (!carregado) {
            if (rotacaoId != 0L) {
                val detalhe = rotacoesViewModel.carregarDetalhes(rotacaoId)
                if (detalhe != null) {
                    nome = detalhe.rotacao.nome
                    nomeInicial = detalhe.rotacao.nome
                    val listaOrdenada = detalhe.slots.sortedBy { it.posicao }.map { it.tipoTurnoId }
                    slots.clear()
                    slots.addAll(listaOrdenada)
                    slotsIniciaisList = listaOrdenada
                }
            }
            if (tipoSelecionado == null && tiposAtivos.isNotEmpty()) {
                tipoSelecionado = tiposAtivos.first()
            }
            carregado = true
        }
    }

    val temAlteracoesNaoGuardadas = nome != nomeInicial || slots.toList() != slotsIniciaisList

    fun tentarSair() {
        if (temAlteracoesNaoGuardadas) {
            mostrarDialogoDescartar = true
        } else {
            onVoltar()
        }
    }

    BackHandler {
        tentarSair()
    }

    val nomeLimpo = nome.trim()
    val temPeloMenos2LetrasOuDigitos = nomeLimpo.filter { it.isLetterOrDigit() }.length >= 2
    val nomeInvalido = nomeLimpo.isNotEmpty() && !temPeloMenos2LetrasOuDigitos
    val existeNomeDuplicado = remember(nomeLimpo, rotacoes, rotacaoId) {
        rotacoes.any { it.id != rotacaoId && it.nome.trim().equals(nomeLimpo, ignoreCase = true) }
    }
    val nomeValido = temPeloMenos2LetrasOuDigitos && !existeNomeDuplicado
    val podeSalvar = nomeValido && slots.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (rotacaoId == 0L) "Nova Rotação" else "Editar Rotação",
                        // Fase 18d: 16 sp sem bold — todas as barras da app com o mesmo tamanho visual.
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
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
                    IconButton(
                        onClick = { mostrarDialogoLimpar = true },
                        enabled = slots.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Limpar tudo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        alturaPainelPx = coordinates.size.height
                    }
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Turno selecionado para pintar:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tiposAtivos.forEach { tipo ->
                            val selecionado = tipoSelecionado?.id == tipo.id
                            val corTexto = calcularCorTexto(tipo.cor)

                            FilterChip(
                                selected = selecionado,
                                onClick = { tipoSelecionado = tipo },
                                leadingIcon = {
                                    if (selecionado) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = corTexto
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .height(14.dp)
                                                .widthIn(min = 21.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(tipo.cor))
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = tipo.abreviatura,
                                        fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selecionado) corTexto else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(tipo.cor),
                                    selectedLabelColor = corTexto,
                                    selectedLeadingIconColor = corTexto
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { tentarSair() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            enabled = podeSalvar,
                            onClick = {
                                val rotacao = Rotacao(
                                    id = rotacaoId,
                                    nome = nome.trim(),
                                    comprimentoCiclo = slots.size
                                )
                                val listaSlots = slots.mapIndexed { idx, tipoId ->
                                    RotacaoSlot(
                                        rotacaoId = rotacaoId,
                                        posicao = idx,
                                        tipoTurnoId = tipoId
                                    )
                                }
                                rotacoesViewModel.salvarRotacaoComSlots(rotacao, listaSlots) {
                                    onVoltar()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Concluído")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome da rotação *") },
                singleLine = true,
                isError = nomeInvalido || existeNomeDuplicado,
                supportingText = if (nomeInvalido) {
                    { Text("O nome deve ter pelo menos 2 letras ou números", color = MaterialTheme.colorScheme.error) }
                } else if (existeNomeDuplicado) {
                    { Text("Já existe uma rotação com o nome '$nomeLimpo'", color = MaterialTheme.colorScheme.error) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Toca num dia para pintar ou mantém premido para remover",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "${slots.size} Dias",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            // Grid calculation (7 columns)
            val totalSlots = slots.size
            val totalLinhas = if (totalSlots == 0) 0 else (totalSlots + 6) / 7

            if (totalSlots > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (linha in 0 until totalLinhas) {
                        val inicioDia = linha * 7 + 1
                        val fimDiaCalculado = (linha + 1) * 7
                        val fimDia = minOf(fimDiaCalculado, totalSlots)
                        val textoIntervalo = if (inicioDia == fimDia) "$inicioDia" else "$inicioDia-$fimDia"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = textoIntervalo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.widthIn(min = 44.dp)
                            )

                            for (col in 0 until 7) {
                                val index = linha * 7 + col
                                Box(modifier = Modifier.weight(1f)) {
                                    if (index < totalSlots) {
                                        val tipoId = slots[index]
                                        val tipo = mapaTipos[tipoId]

                                        CelulaTipoTurno(
                                            tipo = tipo,
                                            tamanho = 40.dp,
                                            formato = FormatoCelula.RETANGULAR,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = {
                                                        tipoSelecionado?.let { slots[index] = it.id }
                                                    },
                                                    onLongClick = {
                                                        if (index in 0 until slots.size) {
                                                            slots.removeAt(index)
                                                        }
                                                    }
                                                )
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(CircleShape)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { tipoSelecionado?.let { slots.add(it.id) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Adicionar dia ao ciclo (+)", fontWeight = FontWeight.Medium)
            }

            // Pattern Summary
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Padrão da Rotação",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = gerarResumoPadrao(slots, mapaTipos),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "Nota: O dia de início do ciclo (âncora) é definido no momento de aplicar a rotação à escala.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(alturaPainelDp + 16.dp))
        }

        if (mostrarDialogoLimpar) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoLimpar = false },
                title = { Text("Limpar tudo?") },
                text = { Text("Tem a certeza que deseja limpar todos os dias da rotação?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            slots.clear()
                            mostrarDialogoLimpar = false
                        }
                    ) {
                        Text("Limpar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogoLimpar = false }) {
                        Text("Cancelar")
                    }
                }
            )
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
