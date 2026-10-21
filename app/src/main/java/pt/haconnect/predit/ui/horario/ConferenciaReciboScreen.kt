package pt.haconnect.predit.ui.horario

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.AusenciaRepository
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.MapeamentoRubricaRepository
import pt.haconnect.predit.data.repository.MunicipioRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.RubricaRepository
import pt.haconnect.predit.data.repository.TabelaIRSRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.model.milParaEuros
import pt.haconnect.predit.ui.turnos.TextoSemQuebra
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import kotlin.math.abs

/**
 * Fase 8.2b.2b — conferência do recibo introduzido: por rubrica, o estimado ao lado do
 * real, com a divergência marcada quando passa 1 cêntimo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenciaReciboScreen(
    modifier: Modifier = Modifier,
    onVoltar: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val viewModel: ReciboViewModel = viewModel(
        factory = ReciboViewModel.Factory(
            reciboRepository = context.reciboRepository,
            contratoRepository = remember { ContratoRepository(db.contratoDao()) },
            parametrosCCTRepository = remember { ParametrosCCTRepository(db.parametrosCCTDao()) },
            rubricaRepository = remember { RubricaRepository(db.rubricaDao()) },
            diaRealRepository = remember { DiaRealRepository(db.diaRealDao()) },
            rotacaoRepository = remember { RotacaoRepository(db.rotacaoDao()) },
            tipoTurnoRepository = remember { TipoTurnoRepository(db.tipoTurnoDao()) },
            tabelaIRSRepository = remember { TabelaIRSRepository(db.tabelaIRSDao()) },
            ausenciaRepository = remember { AusenciaRepository(db.ausenciaDao()) },
            municipioRepository = remember { MunicipioRepository(db.municipioDao()) },
            mapeamentoRepository = remember {
                MapeamentoRubricaRepository(db)
            }
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    var pedirConfirmacao by remember { mutableStateOf(false) }
    var menuAberto by remember { mutableStateOf(false) }

    // Fase 27d: o mesmo ActivityResultContracts.OpenDocument do ImportacaoScreen, agora com
    // filtro de MIME só para este botão (o launcher do planeamento fica como está).
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importarRecibo(uri, context)
        }
    }
    val formatoDataFecho = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // Fase 12b (A2): titulo curto + guardas e acoes em icone. Com 3 TextButtons na barra
                // e font_scale 1.3 sobravam ~28dp para o titulo: primeiro partia a meio da palavra
                // ("Rec/ibo"), depois de encurtado ficava "R…". Com icones sobram ~144dp.
                title = {
                    // Fase 18d: 16 sp sem bold, com o mesmo TextoSemQuebra da barra do Contrato —
                    // todas as barras da app ficam com o mesmo tamanho visual. As guardas (1 linha,
                    // sem quebra a meio da palavra e reticências) vêm de dentro do TextoSemQuebra.
                    TextoSemQuebra(
                        texto = "Recibo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    // A2: icones em vez de texto para libertar o titulo. DateRange e o mesmo icone
                    // do separador Escala (Navegacao.CALENDARIO) e do botao "Hoje" do calendario.
                    IconButton(onClick = { viewModel.irParaHoje() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Ir para hoje")
                    }
                    // Fase 27d: importar o recibo em PDF. Fica entre o "Hoje" e o "Guardar":
                    // as acoes de leitura primeiro, a de escrita depois.
                    IconButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Importar recibo PDF"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.guardar() },
                        enabled = uiState.podeGuardar
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Guardar recibo")
                    }
                    Box {
                        IconButton(onClick = { menuAberto = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                        }
                        DropdownMenu(
                            expanded = menuAberto,
                            onDismissRequest = { menuAberto = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Apagar registo",
                                        // Fase 18b: item de menu a 14 sp.
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                enabled = uiState.podeApagar,
                                onClick = {
                                    menuAberto = false
                                    pedirConfirmacao = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.moverMes(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Mês anterior do recibo"
                )
            }
            Text(
                text = uiState.anoMesFormatado,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                // Fase 18c: 16 sp = tamanho do título de cartão do Menu Mais (titleMedium).
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.moverMes(1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Mês seguinte do recibo"
                )
            }
        }

        // Os botões Hoje/Guardar/Apagar passaram para as actions da TopAppBar (Fase 12a, Passo B)
        // e daí para ícones + menu ⋮ (Fase 12b, A2) para libertar o título da barra.

        if (!uiState.carregando) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = uiState.dataFechoTexto,
                    onValueChange = viewModel::atualizarDataFecho,
                    label = { Text("Data de fecho (dd/mm/aaaa)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cabecalho-data")
                )
                OutlinedTextField(
                    value = uiState.irsRetidoAnoTexto,
                    onValueChange = viewModel::atualizarIrsRetidoAno,
                    label = { Text("IRS retido no ano") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cabecalho-irs")
                )
                OutlinedTextField(
                    value = uiState.nota,
                    onValueChange = viewModel::atualizarNota,
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cabecalho-nota")
                )
            }
        }

        if (!uiState.carregando && !uiState.guardado) {
            Text(
                text = "Sem recibo introduzido",
                // Fase 18b: nota de estado a 12 sp.
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        uiState.mensagemErro?.let { erro ->
            Text(
                text = erro,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // A1: o IRS foi estimado com as tabelas de outro ano (ainda não há as deste).
        uiState.avisoTabelasIRS?.let { aviso ->
            Text(
                text = aviso,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("aviso-tabelas-irs")
            )
        }

        // Fase 27d: o aviso da importação fica no mesmo sítio do erro, mas em cor neutra —
        // é informação, não falha.
        uiState.mensagemImportacao?.let { aviso ->
            Text(
                text = aviso,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("aviso-importacao")
            )
        }

        if (uiState.carregando) {
            Text(
                text = "A carregar catálogo e parâmetros...",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            // 2026-10-21: as rubricas de recurso ("Outros" e "Outros" dos descontos) ficam fora da
            // lista enquanto estiverem a zero nas duas colunas, as de feriado só aparecem nos
            // meses com feriado e os subsídios só nos meses em que fazem sentido — ver
            // linhasVisiveis.
            val visiveis = linhasVisiveis(
                uiState.linhas,
                uiState.temFeriado,
                uiState.mostrarSubsidioNatal,
                uiState.mostrarSubsidioFerias
            )
            // Column com scroll em vez de LazyColumn: são 20 rubricas, não vale a pena
            // virtualizar — e uma lista lazy dentro de uma Column condicional não estava a
            // desenhar os itens quando o ecrã era composto por um clique no separador.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .testTag("lista-recibo"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (natureza in listOf(
                    NaturezaRubrica.ABONO,
                    NaturezaRubrica.DESCONTO,
                    NaturezaRubrica.INFORMATIVA
                )) {
                    val doGrupo = visiveis.filter { it.natureza == natureza }
                    if (doGrupo.isEmpty()) continue
                    Text(
                        text = when (natureza) {
                            NaturezaRubrica.ABONO -> "ABONOS"
                            NaturezaRubrica.DESCONTO -> "DESCONTOS"
                            // As ausências são informativas: não entram nos totais nem cortam
                            // o VENC, por isso têm um grupo próprio.
                            NaturezaRubrica.INFORMATIVA -> "INFORMATIVO"
                        },
                        // Fase 18c: 12 sp (igual ao subtítulo do cartão do Mais) + letterSpacing.
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                    for (linha in doGrupo) {
                        LinhaRubrica(
                            linha = linha,
                            onTextoAlterado = { texto ->
                                viewModel.atualizarValorReal(linha.rubricaId, texto)
                            }
                        )
                        HorizontalDivider()
                    }
                }
                TotaisRecibo(uiState)
            }
        }
    }
    }

    if (pedirConfirmacao) {
        AlertDialog(
            onDismissRequest = { pedirConfirmacao = false },
            title = { Text("Apagar o recibo") },
            text = {
                Text("Apagar o recibo de ${uiState.anoMesFormatado}? Os valores introduzidos perdem-se.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pedirConfirmacao = false
                    viewModel.apagar()
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { pedirConfirmacao = false }) { Text("Cancelar") }
            }
        )
    }

    // Fase 27d: o PDF trazia mais do que um recibo (a folha principal e o suplemento do
    // mesmo mês). Quem escolhe é o utilizador; o parser não adivinha. Sem códigos nem
    // valores: só o mês de referência e a data de fecho, quando o recibo a traz.
    if (uiState.seletorDeReciboAberto) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelarSeletor() },
            title = { Text("Encontrei vários recibos no PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Escolhe qual queres aplicar:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.recibosParaEscolher.forEach { info ->
                        val etiqueta = info.dataFecho?.let { dia ->
                            "${info.anoMesFormatado} — fecho " +
                                LocalDate.ofEpochDay(dia).format(formatoDataFecho)
                        } ?: info.anoMesFormatado
                        TextButton(
                            onClick = { viewModel.escolherRecibo(info.indice) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(etiqueta)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelarSeletor() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // A3: o recibo não traz mês legível, por isso não se sabe se é do mês visível. Só se aplica
    // com a confirmação do utilizador.
    if (uiState.confirmarReciboSemMes) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelarReciboSemMes() },
            title = { Text("Recibo sem mês reconhecível") },
            text = {
                Text(
                    "Não consegui ler o mês deste recibo. Aplicar os valores a " +
                        "${uiState.anoMesFormatado}?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmarReciboSemMes() }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelarReciboSemMes() }) { Text("Cancelar") }
            }
        )
    }

    // Fase 28c: o recibo trazia códigos que nenhum mapa conhece. Enquanto o utilizador não
    // disser o que são, o recibo não é aplicado — só sai daqui com "Confirmar" ou "Cancelar".
    uiState.mapeamentoPendente?.let { pendente ->
        MapeamentoModal(
            pendente = pendente,
            // A lista mostra nomes; o código só serve para gravar. A ordem é a do catálogo.
            // 2026-10-21: as duas rubricas de recurso chamam-se "Outros". Aqui o nome é uma escolha
            // do utilizador, por isso o "(desconto)" volta na de descontos — escolher a de abonos
            // somava ao líquido um valor que devia sair.
            rubricas = uiState.linhas
                .sortedBy { it.ordem }
                .map { linha ->
                    val nome = if (linha.codigo == RUBRICA_OUTROS_DESC) "Outros (desconto)" else linha.nome
                    nome to linha.codigo
                }
                .distinctBy { it.second },
            onEscolha = viewModel::atualizarEscolhaMapeamento,
            onConfirmar = viewModel::confirmarMapeamento,
            onCancelar = viewModel::cancelarMapeamento
        )
    }
}

/** Uma rubrica: nome, estimado, campo do real e a divergência quando passa 1 cêntimo. */
@Composable
private fun LinhaRubrica(
    linha: LinhaConferencia,
    onTextoAlterado: (String) -> Unit
) {
    // O campo guarda o `TextFieldValue`, e não só o texto: ligado a uma String que volta do
    // ViewModel, cada tecla regressava com a seleção a zero e o cursor saltava para a esquerda
    // (o clássico "escreve ao contrário" dos campos controlados por texto puro). O estado é do
    // campo — chaveado pela rubrica, para não vazar de uma linha para outra — e só se reescreve
    // quando o texto muda por fora: importação do recibo, limpeza ou mudança de mês.
    var textFieldValue by remember(linha.rubricaId) {
        mutableStateOf(
            TextFieldValue(
                linha.textoRealEditavel,
                selection = TextRange(linha.textoRealEditavel.length)
            )
        )
    }
    LaunchedEffect(linha.textoRealEditavel) {
        if (textFieldValue.text != linha.textoRealEditavel) {
            textFieldValue = textFieldValue.copy(
                text = linha.textoRealEditavel,
                selection = TextRange(linha.textoRealEditavel.length)
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (linha.ativaConferencia) 1f else 0.5f)
    ) {
        Text(
            // Fase 18c: só o nome da rubrica (sem o código): o VENC/HNOT/D01... já não aparece.
            text = linha.nome,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            // Fase 18c: título de rubrica a 14 sp (um degrau abaixo do título de cartão do Mais).
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fase 18b: rótulo "Estimado:" a 11 sp e valor a 14 sp — um só Text com dois
            // estilos, para o texto continuar a ser "Estimado: 1 137,98 €" inteiro.
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) { append("Estimado: ") }
                    withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)) {
                        append(linha.valorEstimado.milParaEuros())
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { novoValor ->
                    textFieldValue = novoValor
                    onTextoAlterado(novoValor.text)
                },
                label = { Text("Real", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)) },
                singleLine = true,
                isError = linha.textoInvalido,
                // Não se confere o que não se quer conferir: a flag é do catálogo.
                enabled = linha.ativaConferencia,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .width(150.dp)
                    .testTag("campo-${linha.codigo}")
            )
        }
        if (!linha.ativaConferencia) {
            Text(
                text = "não conferida",
                // Fase 18b: 11 sp (labels).
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (linha.temDivergencia) {
            val sinal = if (linha.divergencia < 0L) "\u2212" else "+"
            Text(
                text = "\u26A0 $sinal${abs(linha.divergencia).milParaEuros()}",
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                // Fase 18b: 11 sp, na cor de erro.
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("divergencia-${linha.codigo}")
            )
        }
    }
}

/** Rodapé com os totais estimados e os reais, em quatro linhas alinhadas. */
@Composable
private fun TotaisRecibo(uiState: ReciboUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HorizontalDivider()
        // Totais estimados
        Text(
            text = "Totais estimados",
            // Fase 18c: 14 sp (igual ao título de rubrica).
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LinhaTotal("Abonos", uiState.totalAbonosEstimado)
        LinhaTotal("Desc.", uiState.totalDescontosEstimado)
        LinhaTotal("Líquido", uiState.liquidoEstimado, destaque = true)

        Spacer(modifier = Modifier.height(12.dp))

        // Totais reais
        Text(
            text = "Totais reais",
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LinhaTotal("Abonos", uiState.totalAbonosReal)
        LinhaTotal("Desc.", uiState.totalDescontosReal)
        LinhaTotal("Líquido", uiState.liquidoReal, destaque = true)
    }
}

/** Uma linha do rodapé: rótulo à esquerda, valor à direita. */
@Composable
private fun LinhaTotal(
    rotulo: String,
    valorMil: Long,
    destaque: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rotulo,
            // Fase 18b: rótulos e valores das linhas do rodapé a 13 sp.
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = valorMil.milParaEuros(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Medium,
            color = if (destaque) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End
        )
    }
}

/**
 * Fase 28c: o recibo trazia códigos que nenhum mapa conhece. Por cada linha, o utilizador diz
 * qual é a rubrica da app; "Confirmar" só fica ativo quando todos os códigos distintos têm
 * destino escolhido.
 *
 * A lista mostra o **nome** da rubrica, nunca o código (regra da fase 18c): o código é o que
 * fica gravado na BD, o nome é o que se lê.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapeamentoModal(
    pendente: MapeamentoPendente,
    rubricas: List<Pair<String, String>>, // Nome -> Código
    onEscolha: (String, String) -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // O mesmo código pode vir em duas linhas do recibo (RCR + RCRA): o destino é um só.
    val itensUnicos = pendente.itens.distinctBy { it.codigoOriginal }
    val todosMapeados = itensUnicos.all { it.codigoOriginal in pendente.escolhas }

    ModalBottomSheet(
        onDismissRequest = onCancelar,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp) // espaço extra no fundo
                .testTag("modal-mapeamento"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Rubricas por mapear",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            val empresa = pendente.empresaEmissora
            if (empresa == null) {
                Text(
                    text = "Empresa não detetada — o mapeamento vale só para esta importação, " +
                        "não fica memorizado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "Recibo de $empresa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Diz-nos o que são estas rubricas:",
                style = MaterialTheme.typography.bodyMedium
            )

            // Lista scrollável se houver muitos itens
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itensUnicos.forEach { item ->
                    ItemMapeamentoRow(
                        item = item,
                        rubricas = rubricas,
                        escolhaAtual = pendente.escolhas[item.codigoOriginal],
                        onEscolha = { codigoInterno -> onEscolha(item.codigoOriginal, codigoInterno) }
                    )
                }
            }

            // 28c: botoes empilhados e a largura toda. Lado a lado, "Cancelar importacao" e
            // "Confirmar mapeamento" nao cabiam na mesma linha em ecra pequeno (o texto era
            // cortado a meio da palavra).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirmar,
                    enabled = todosMapeados,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guardar-mapeamento")
                ) {
                    Text("Confirmar mapeamento", maxLines = 1)
                }
                TextButton(
                    onClick = onCancelar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar importação", maxLines = 1)
                }
            }
        }
    }
}

/**
 * Uma linha do recibo sem destino. O valor aparece em euros: é o que o utilizador reconhece no
 * papel. A sugestão da heurística ([ItemMapeamento.sugestao]) já vai pré-selecionada e conta
 * como escolha (28g): o "Confirmar" só espera pelas linhas que a heurística não soube mapear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemMapeamentoRow(
    item: ItemMapeamento,
    rubricas: List<Pair<String, String>>,
    escolhaAtual: String?,
    onEscolha: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selecionada = rubricas.find { it.second == (escolhaAtual ?: item.sugestao) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Linha ${item.linha} — ${item.descricao} — ${item.valor.milParaEuros()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.padding(top = 4.dp)
        ) {
            OutlinedTextField(
                value = selecionada?.first ?: "Selecionar rubrica...",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag("mapeamento-${item.codigoOriginal}")
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                rubricas.forEach { (nome, codigo) ->
                    DropdownMenuItem(
                        text = { Text(nome) },
                        onClick = {
                            onEscolha(codigo)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
