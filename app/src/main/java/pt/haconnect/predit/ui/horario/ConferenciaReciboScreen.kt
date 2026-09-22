package pt.haconnect.predit.ui.horario

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import pt.haconnect.predit.data.repository.MunicipioRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.RubricaRepository
import pt.haconnect.predit.data.repository.TabelaIRSRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.model.milParaEuros
import pt.haconnect.predit.ui.turnos.TextoSemQuebra

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
            municipioRepository = remember { MunicipioRepository(db.municipioDao()) }
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    var pedirConfirmacao by remember { mutableStateOf(false) }
    var menuAberto by remember { mutableStateOf(false) }

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

        if (uiState.carregando) {
            Text(
                text = "A carregar catálogo e parâmetros...",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(12.dp)
            )
        } else {
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
                for (natureza in listOf(NaturezaRubrica.ABONO, NaturezaRubrica.DESCONTO)) {
                    val doGrupo = uiState.linhas.filter { it.natureza == natureza }
                    if (doGrupo.isEmpty()) continue
                    Text(
                        text = if (natureza == NaturezaRubrica.ABONO) "ABONOS" else "DESCONTOS",
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
}

/** Uma rubrica: nome, estimado, campo do real e a divergência quando passa 1 cêntimo. */
@Composable
private fun LinhaRubrica(
    linha: LinhaConferencia,
    onTextoAlterado: (String) -> Unit
) {
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
                value = linha.textoRealEditavel,
                onValueChange = onTextoAlterado,
                label = { Text("Real", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)) },
                singleLine = true,
                isError = linha.textoInvalido,
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
