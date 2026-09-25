package pt.haconnect.predit.ui.mais

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.haconnect.predit.PreditApplication
import pt.haconnect.predit.data.repository.ContratoRepository
import pt.haconnect.predit.data.repository.ParametrosCCTRepository
import pt.haconnect.predit.data.repository.RubricaRepository
import pt.haconnect.predit.domain.model.milParaEuros
import pt.haconnect.predit.ui.turnos.TextoSemQuebra

/**
 * Fase 21d — simulador de IRS anual: projeção do ano a partir do histórico de recibos, com
 * tributação individual ou conjunta, mínimo de existência e deduções à coleta.
 *
 * O ecrã não faz contas: mostra o que o [SimuladorIrsViewModel] calcula com o motor
 * (domain/calc/IrsAnual.kt). O rendimento bruto do ano vem pré-preenchido com a projeção
 * (média mensal dos abonos guardados × 12; sem recibos, o valor base do CCT); quem quiser
 * simular outro cenário escreve lá o valor e o pré-preenchimento desliga-se até tocar em
 * «Repor». Para casado de 2 titulares há ainda o campo do cônjuge: preenchido, o cálculo passa
 * a ser em tributação conjunta (quociente conjugal).
 *
 * O abatimento do art. 70.º vem ligado por omissão e pode desligar-se no interruptor do topo;
 * as deduções à coleta são opcionais (vazio = 0,00 €, o cenário mais conservador).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimuladorIrsScreen(
    modifier: Modifier = Modifier,
    onVoltar: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext as PreditApplication
    val db = context.database

    val viewModel: SimuladorIrsViewModel = viewModel(
        factory = SimuladorIrsViewModel.Factory(
            reciboRepository = context.reciboRepository,
            contratoRepository = remember { ContratoRepository(db.contratoDao()) },
            parametrosCCTRepository = remember { ParametrosCCTRepository(db.parametrosCCTDao()) },
            rubricaRepository = remember { RubricaRepository(db.rubricaDao()) }
        )
    )

    val estado by viewModel.estado.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    TextoSemQuebra(
                        texto = "Simulador de IRS",
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
                }
            )
        }
    ) { padding ->
        if (estado.carregando) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = "A carregar os dados do contrato…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            ConteudoSimulador(
                estado = estado,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun ConteudoSimulador(
    estado: SimuladorUiState,
    viewModel: SimuladorIrsViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quem é o contribuinte (só leitura): vem do contrato, não se edita aqui.
        Rotulo("Situação familiar")
        Text(situacaoFamiliar(estado), style = MaterialTheme.typography.bodyMedium)

        Rotulo("Região fiscal")
        Text(rotuloRegiao(estado.regiao), style = MaterialTheme.typography.bodyMedium)

        Rotulo("IRS Jovem")
        Text(
            text = estado.percentagemIrsJovem?.let { "Ativo — isenção de $it %" }
                ?: "Não aplicável neste ano",
            style = MaterialTheme.typography.bodyMedium
        )

        // Art. 70.º: ligado por omissão, como nos simuladores da praça, mas desligável para
        // comparar com o cenário sem abatimento.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = estado.aplicarMinimoExistencia,
                onCheckedChange = viewModel::atualizarAplicarMinimoExistencia,
                modifier = Modifier.testTag("simulador-minimo-existencia")
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = "Aplicar mínimo de existência (art. 70.º)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Reduz o rendimento coletável nos rendimentos mais baixos. Deixa de " +
                        "ter efeito acima de cerca de 15 900 €/ano.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Rotulo("Rendimento bruto anual (projeção)")
        OutlinedTextField(
            value = estado.rendimentoAnualTexto,
            onValueChange = viewModel::atualizarRendimentoAnual,
            label = { Text("€ por ano") },
            singleLine = true,
            isError = estado.resultado == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("simulador-rendimento")
        )
        OrigemDoRendimento(estado = estado, onRepor = viewModel::reporRendimento)

        // Casado de 2 titulares: com um valor aqui, o cálculo passa a ser em tributação
        // conjunta; vazio, é o cálculo individual de sempre.
        if (estado.estadoCivil == "CASADO" && estado.titulares == 2) {
            Rotulo("Rendimento bruto anual do cônjuge")
            OutlinedTextField(
                value = estado.rendimentoConjugeTexto,
                onValueChange = viewModel::atualizarRendimentoConjuge,
                label = { Text("€ por ano") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulador-rendimento-conjuge")
            )
            Text(
                text = "(opcional) — preenche só se quiseres simular a tributação conjunta; " +
                    "deixa vazio para cálculo individual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Deduções à coleta: o utilizador introduz (não são automáticas). Vazio é 0,00 €.
        Rotulo("Deduções à coleta (opcional)")
        OutlinedTextField(
            value = estado.deducoesColetaTexto,
            onValueChange = viewModel::atualizarDeducoesColeta,
            label = { Text("€ por ano") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("simulador-deducoes-coleta")
        )
        Text(
            text = "Saúde, educação, PPR, despesas gerais. Deixa 0 para o cenário mais " +
                "conservador.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "${estado.numeroPagamentos} pagamentos anuais é o habitual. " +
                "O valor acima deve ser o total anual.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        estado.aviso?.let { aviso ->
            Text(
                text = aviso,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Rotulo("Contas do ano")
        val resultado = estado.resultado
        if (resultado == null) {
            Text(
                text = "Sem dados para simular: escreve um rendimento anual válido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val aPagar = resultado.diferenca >= 0L
            val cor = if (aPagar) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

            LinhaCalculo("Rendimento bruto anual", resultado.rendimentoBruto.milParaEuros())
            LinhaCalculo(
                if (estado.tributacaoConjunta) {
                    "Dedução específica (2 × 8,54 IAS)"
                } else {
                    "Dedução específica (8,54 IAS)"
                },
                resultado.deducaoEspecifica.milParaEuros()
            )
            LinhaCalculo("Rendimento coletável", resultado.rendimentoColetavel.milParaEuros())
            LinhaCalculo(
                "Abatimento por mínimo de existência (art. 70.º)",
                abatimentoFormatado(resultado.abatimentoMinimoExistencia)
            )
            LinhaCalculo(
                "Coletável após abatimento",
                resultado.coletavelAposAbatimento.milParaEuros()
            )
            LinhaCalculo(
                estado.percentagemIrsJovem?.let { "Isenção IRS Jovem ($it %)" }
                    ?: "Isenção IRS Jovem",
                resultado.isencaoIrsJovem.milParaEuros()
            )
            LinhaCalculo("Coletável após IRS Jovem", resultado.coletavelAposJovem.milParaEuros())

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LinhaCalculo("IRS devido", resultado.coletaDevida.milParaEuros(), destaque = true)

            // O quociente não é imposto a mais: é a forma de o calcular. Aqui vê-se quanto a
            // mesma soma pagaria tratada como um só titular (uma só dedução específica).
            if (estado.tributacaoConjunta) {
                LinhaCalculo(
                    "Sem quociente conjugal seria",
                    estado.coletaSeIndividual.milParaEuros()
                )
            }

            LinhaCalculo("Retenções efetuadas no ano", resultado.retencoesEfetuadas.milParaEuros())
            LinhaCalculo("Deduções à coleta", resultado.deducoesColetaAplicadas.milParaEuros())

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // O fecho das contas: quanto sai (a pagar) ou quanto volta (a receber).
            LinhaCalculo(
                if (aPagar) {
                    "A PAGAR quando entregares o IRS"
                } else {
                    "A RECEBER quando entregares o IRS"
                },
                diferencaFormatada(resultado.diferenca),
                destaque = true,
                corValor = cor
            )

            // Fecho do modo conjunto: quanto se poupa face a tributar o agregado como um só.
            if (estado.tributacaoConjunta) {
                Text(
                    text = "O quociente conjugal poupa-te " +
                        "${estado.poupancaQuociente.milParaEuros()} face à tributação " +
                        "individual do agregado.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Bruto que não chega à dedução específica: coletável a 0 e nada a pagar de IRS.
            if (resultado.rendimentoBruto > 0L && resultado.rendimentoColetavel == 0L) {
                Text(
                    text = "O rendimento bruto não cobre a dedução específica: o coletável fica 0 " +
                        "e o IRS devido também. É o esperado — um ano com pouco rendimento não " +
                        "paga IRS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Estimativa indicativa. Aplica o mínimo de existência (art. 70.º) e as " +
                "deduções à coleta que introduzires.\nTributação conjunta calculada pelo método " +
                "do quociente conjugal (art. 69.º CIRS).\nNão substitui as contas da AT.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * De onde veio o valor do campo: com recibos guardados, diz em quantos meses se apoia e quais;
 * sem nenhum, avisa que está no valor base do CCT. Quando foi escrito à mão, aparece o aviso e
 * o «Repor», que devolve o pré-preenchido.
 */
@Composable
private fun OrigemDoRendimento(estado: SimuladorUiState, onRepor: () -> Unit) {
    val cinza = MaterialTheme.colorScheme.onSurfaceVariant
    val estilo = MaterialTheme.typography.bodySmall

    if (estado.mesesRegistados > 0) {
        val meses = if (estado.mesesRegistados == 1) "1 mês" else "${estado.mesesRegistados} meses"
        Text(
            text = "Baseado em $meses " +
                "(${estado.mesesRegistadosRotulos.joinToString(", ")}): " +
                estado.rendimentoAcumuladoMil.milParaEuros(),
            style = estilo,
            color = cinza
        )
        Text(
            text = "Projeção: média mensal × 12",
            style = estilo,
            color = cinza
        )
    } else {
        Text(
            text = "Ainda não tens recibos guardados neste ano.",
            style = estilo,
            color = cinza
        )
        if (!estado.valorEditadoManualmente) {
            Text(
                text = "Estás a simular com o valor base do CCT (${estado.rendimentoAnualTexto} €).",
                style = estilo,
                color = cinza
            )
        }
        Text(
            text = "Edita se quiseres simular outro cenário.",
            style = estilo,
            color = cinza
        )
    }

    if (estado.valorEditadoManualmente) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (estado.mesesRegistados > 0) {
                    "Valor editado manualmente. Toca em «Repor» para voltar aos valores " +
                        "registados."
                } else {
                    "Valor editado manualmente. Toca em «Repor» para voltar ao valor base do CCT."
                },
                modifier = Modifier.weight(1f),
                style = estilo,
                color = cinza
            )
            TextButton(onClick = onRepor) {
                Text("Repor")
            }
        }
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

/** A situação familiar do contrato e o modo de cálculo que está a ser usado. */
private fun situacaoFamiliar(estado: SimuladorUiState): String {
    val titulares = if (estado.titulares == 1) "1 titular" else "${estado.titulares} titulares"
    val dependentes = when (estado.numeroDependentes) {
        0 -> "0 dependentes"
        1 -> "1 dependente"
        else -> "${estado.numeroDependentes} dependentes"
    }
    val modo = if (estado.tributacaoConjunta) {
        "Tributação conjunta (quociente conjugal)"
    } else {
        "Tributação individual"
    }
    return "${rotuloEstadoCivil(estado.estadoCivil)}, $titulares · $dependentes · $modo"
}

/** Rótulo do estado civil como no Contrato: o nome da enum não se mostra a ninguém. */
private fun rotuloEstadoCivil(estadoCivil: String): String = when (estadoCivil) {
    "SOLTEIRO" -> "Solteiro(a)"
    "CASADO" -> "Casado(a)"
    "VIUVO" -> "Viúvo(a)"
    "DIVORCIADO" -> "Divorciado(a)"
    else -> estadoCivil
}

/** Rótulo da região como no Contrato: o nome da enum não se mostra a ninguém. */
private fun rotuloRegiao(regiao: String): String = when (regiao) {
    "CONTINENTE" -> "Continente"
    "ACORES" -> "Açores"
    "MADEIRA" -> "Madeira"
    else -> regiao
}

/**
 * O abatimento mostra-se sempre — 0,00 € quando não há — para se ver que foi considerado.
 * Positivo leva o sinal, porque sai ao rendimento coletável.
 */
private fun abatimentoFormatado(abatimento: Long): String =
    if (abatimento <= 0L) 0L.milParaEuros() else "\u2212" + abatimento.milParaEuros()

/** "+688,00 €" / "−7 520,73 €": com o sinal explícito não há dúvida de quem paga a quem. */
private fun diferencaFormatada(diferenca: Long): String {
    val texto = diferenca.milParaEuros()
    return when {
        diferenca > 0L -> "+$texto"
        diferenca < 0L -> texto.replaceFirst("-", "\u2212")
        else -> texto
    }
}

/**
 * Linha da tabela de contas: rótulo à esquerda, valor à direita.
 *
 * O `weight(1f)` no rótulo é o que evita a colisão com o valor: o rótulo cede espaço e pode
 * quebrar em duas linhas (reticências à terceira); o valor nunca quebra — sai sempre inteiro,
 * numa linha só, com 16 dp de respiro entre os dois.
 *
 * @param destaque valor a negrito (o IRS devido e o fecho das contas)
 * @param corValor cor do valor; por omissão, a cor normal do texto
 */
@Composable
private fun LinhaCalculo(
    rotulo: String,
    valor: String,
    destaque: Boolean = false,
    corValor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rotulo,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Medium,
            color = corValor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false
        )
    }
}
