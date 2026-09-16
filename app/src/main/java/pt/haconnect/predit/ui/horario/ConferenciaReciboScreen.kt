package pt.haconnect.predit.ui.horario

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.DiaRealRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.data.repository.RotacaoRepository
import pt.haconnect.predit.data.repository.RubricaRepository
import pt.haconnect.predit.data.repository.TabelaIRSRepository
import pt.haconnect.predit.data.repository.TipoTurnoRepository
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.model.milParaEuros
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
            tabelaIRSRepository = remember { TabelaIRSRepository(db.tabelaIRSDao()) }
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    var pedirConfirmacao by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Recibo de vencimento") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.irParaHoje() }) { Text("Hoje") }
                    TextButton(
                        onClick = { viewModel.guardar() },
                        enabled = uiState.podeGuardar
                    ) { Text("Guardar") }
                    TextButton(
                        onClick = { pedirConfirmacao = true },
                        enabled = uiState.podeApagar
                    ) { Text("Apagar") }
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.moverMes(1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Mês seguinte do recibo"
                )
            }
        }

        // Os botões Hoje/Guardar/Apagar passaram para as actions da TopAppBar (Passo B).

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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        uiState.mensagemErro?.let { erro ->
            Text(
                text = erro,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        if (uiState.carregando) {
            Text(
                text = "A carregar catálogo e parâmetros...",
                style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
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

/** Uma rubrica: código · nome, estimado, campo do real e a divergência quando passa 1 cêntimo. */
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
            text = "${linha.codigo} · ${linha.nome}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Estimado: ${linha.valorEstimado.milParaEuros()}",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = linha.textoRealEditavel,
                onValueChange = onTextoAlterado,
                label = { Text("Real") },
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (linha.temDivergencia) {
            val sinal = if (linha.divergencia < 0L) "\u2212" else "+"
            Text(
                text = "\u26A0 $sinal${abs(linha.divergencia).milParaEuros()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("divergencia-${linha.codigo}")
            )
        }
    }
}

/** Rodapé com os totais estimados e os reais. */
@Composable
private fun TotaisRecibo(uiState: ReciboUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HorizontalDivider()
        LinhaTotal(
            "Totais estimados",
            uiState.totalAbonosEstimado,
            uiState.totalDescontosEstimado,
            uiState.liquidoEstimado
        )
        LinhaTotal(
            "Totais reais",
            uiState.totalAbonosReal,
            uiState.totalDescontosReal,
            uiState.liquidoReal
        )
    }
}

@Composable
private fun LinhaTotal(rotulo: String, abonos: Long, descontos: Long, liquido: Long) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = rotulo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "Abonos ${abonos.milParaEuros()} · Desc. ${descontos.milParaEuros()} · Líq. ${liquido.milParaEuros()}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
