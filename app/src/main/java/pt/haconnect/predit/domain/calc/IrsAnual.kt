package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.dividirArredondando

/**
 * IRS anual (Fase 21): escalões do Continente para 2026, mínimo de existência (art. 70.º) e o
 * motor do simulador.
 *
 * É um motor separado do recibo: o mensal (CalculoIRS, tabelas da AT por mês) e o anual (as
 * taxas progressivas por escalão, apuradas na declaração) não partilham números nem fórmula.
 * Domínio puro — só [dividirArredondando] do modelo; zero Android/Room.
 */

/**
 * Um escalão do IRS anual.
 *
 * @param limiteSuperior limite do escalão, em 1/10000 € (Long.MAX_VALUE no último)
 * @param taxaBasisPoints taxa × 100 (12,20 % = 1220)
 */
data class EscalaoAnual(
    val limiteSuperior: Long,
    val taxaBasisPoints: Int
)

/**
 * Escalões do IRS anual do Continente para 2026.
 * Fonte: Lei n.º 108/XVII/2 (alteração de setembro de 2026).
 * Substituem os valores do OE2026 (que eram 12,5 % / 15,7 % / ...).
 */
val ESCALOES_ANUAIS_CONTINENTE_2026: List<EscalaoAnual> = listOf(
    EscalaoAnual(limiteSuperior = 83_420_000L, taxaBasisPoints = 1220),
    EscalaoAnual(limiteSuperior = 125_870_000L, taxaBasisPoints = 1520),
    EscalaoAnual(limiteSuperior = 178_380_000L, taxaBasisPoints = 2070),
    EscalaoAnual(limiteSuperior = 230_890_000L, taxaBasisPoints = 2360),
    EscalaoAnual(limiteSuperior = 293_970_000L, taxaBasisPoints = 3060),
    EscalaoAnual(limiteSuperior = 430_900_000L, taxaBasisPoints = 3460),
    EscalaoAnual(limiteSuperior = 465_660_000L, taxaBasisPoints = 4310),
    EscalaoAnual(limiteSuperior = 866_340_000L, taxaBasisPoints = 4460),
    EscalaoAnual(limiteSuperior = Long.MAX_VALUE, taxaBasisPoints = 4800)
)

/** IAS de 2026, em 1/10000 € (537,13 €). */
const val IAS_2026_MIL = 5_371_300L

/** Ano para que este motor tem escalões e IAS tabelados. Fora dele não se simula. */
const val ANO_IRS_ANUAL = 2026

/**
 * Valor de referência do mínimo de existência — art. 70.º n.º 1: o maior entre 12 880 € e
 * 1,5 × 14 × IAS. Com o IAS de 2026 (537,13 €) o segundo dá 11 279,73 €, por isso manda o 12 880 €.
 */
const val VALOR_REFERENCIA_MINIMO_2026_MIL = 128_800_000L

/**
 * L — art. 70.º n.º 3: V − (Limite despesas gerais / (Taxa 1.º escalão × 3,60)) +
 * (Limite 1.º escalão / 3,60). Com os valores de 2026 dá 14 628,01 €; a lei usa o valor
 * arredondado, que é o que aqui fica.
 */
const val LIMITE_INTERMEDIO_MINIMO_2026_MIL = 146_280_000L

/** Limite das despesas gerais familiares por sujeito passivo — art. 70.º n.º 5 c): 250 €. */
const val LIMITE_DESPESAS_GERAIS_2026_MIL = 2_500_000L

/** Limite do 1.º escalão — art. 70.º n.º 5 e): 8 342 €. */
const val LIMITE_1_ESCALAO_2026_MIL = 83_420_000L

/** Taxa normal do 1.º escalão — art. 70.º n.º 5 d): 12,20 % (1220 basis points). */
const val TAXA_1_ESCALAO_2026_BPS = 1220

/**
 * Limite do n.º 4 a): 2,2 × 14 × IAS = 16 543,6040 € **por sujeito passivo**. Soma de
 * rendimentos brutos acima disto e o abatimento não se aplica a nenhum titular.
 */
const val LIMITE_NAO_APLICACAO_MINIMO_2026_MIL = 165_436_040L

/**
 * 250 € ÷ 12,2 % = 2 049,1803 € — o termo que as alíneas a) e b) do n.º 2 abatem a par das
 * deduções específicas (é o valor das despesas gerais já traduzido em rendimento).
 */
private const val DESPESAS_GERAIS_SOBRE_TAXA_1_MIL = 20_491_803L

/**
 * Dedução específica da categoria A: 8,54 × IAS.
 *
 * 854 × 5 371 300 ÷ 100 = **45 870 902** unidades = 4 587,09 € (o maior dos dois valores
 * habituais — o outro são as contribuições obrigatórias para a SS, que não temos ao detalhe).
 * Um ano sem IAS tabelado devolve 0: quem chama tem de recusar o ano, não simular coleta a mais.
 */
fun deducaoEspecificaCategoriaA(ano: Int): Long {
    if (ano != ANO_IRS_ANUAL) return 0L
    return dividirArredondando(854L * IAS_2026_MIL, 100L)
}

/**
 * Abatimento por mínimo de existência (art. 70.º do CIRS), em unidades de 1/10000 €.
 *
 * Reduz o RENDIMENTO COLETÁVEL (não a coleta), por titular, em três patamares — n.º 2:
 *
 *  a) R ≤ V      → V − deduções específicas − 250 € ÷ 12,2 %
 *  b) V < R ≤ L  → V − 2,60 × (R − V) − deduções específicas − 250 € ÷ 12,2 %
 *  c) R > L      → L − 8 342 € − 1,35 × (R − L) − deduções específicas
 *
 * O resultado nunca é negativo nem superior a (R − deduções específicas) — n.º 2 d). O
 * abatimento chega a zero por volta dos 15 887 € de rendimento, antes do limite do n.º 4 a).
 *
 * A regra do n.º 4 a) (não aplicação quando a soma dos brutos do agregado excede
 * 2,2 × 14 × IAS × n.º de sujeitos passivos) **não** é verificada aqui: quem chama sabe quantos
 * titulares tem. Ver [calcularIrsAnual] e [calcularIrsAnualConjunta].
 *
 * Um ano sem IAS tabelado devolve 0 — como a dedução específica, sem os números do ano não se
 * inventam abatimentos.
 */
fun abatimentoMinimoExistencia(
    rendimentoBrutoAnual: Long,
    deducoesEspecificas: Long,
    ano: Int
): Long {
    if (ano != ANO_IRS_ANUAL) return 0L

    val v = VALOR_REFERENCIA_MINIMO_2026_MIL
    val r = rendimentoBrutoAnual

    val abatimento = when {
        r <= v -> v - deducoesEspecificas - DESPESAS_GERAIS_SOBRE_TAXA_1_MIL
        r <= LIMITE_INTERMEDIO_MINIMO_2026_MIL ->
            v - dividirArredondando(260L * (r - v), 100L) - deducoesEspecificas -
                DESPESAS_GERAIS_SOBRE_TAXA_1_MIL
        else ->
            LIMITE_INTERMEDIO_MINIMO_2026_MIL - LIMITE_1_ESCALAO_2026_MIL -
                dividirArredondando(135L * (r - LIMITE_INTERMEDIO_MINIMO_2026_MIL), 100L) -
                deducoesEspecificas
    }

    val maximo = (r - deducoesEspecificas).coerceAtLeast(0L)
    return abatimento.coerceIn(0L, maximo)
}

/**
 * O simulador de IRS anual: do rendimento bruto às contas da declaração.
 *
 * Unidades de 1/10000 €, como todo o dinheiro do projeto (ver domain/model/Dinheiro.kt).
 *
 * @param abatimentoMinimoExistencia art. 70.º: sai ao rendimento coletável antes dos escalões
 * @param coletavelAposAbatimento rendimento coletável já sem o abatimento (é o que é tributado)
 * @param deducoesColetaAplicadas deduções à coleta que o utilizador introduziu
 * @param coletaAposDeducoes coleta depois das deduções à coleta, nunca abaixo de 0
 * @param diferenca coletaAposDeducoes − retenções: positivo é a pagar, negativo a receber.
 */
data class ResultadoIrsAnual(
    val rendimentoBruto: Long,
    val deducaoEspecifica: Long,
    val rendimentoColetavel: Long,
    val isencaoIrsJovem: Long,
    val coletavelAposJovem: Long,
    val coletaDevida: Long,
    val retencoesEfetuadas: Long,
    val diferenca: Long,
    val abatimentoMinimoExistencia: Long = 0L,
    val coletavelAposAbatimento: Long = 0L,
    val deducoesColetaAplicadas: Long = 0L,
    val coletaAposDeducoes: Long = 0L
)

/**
 * Calcula o IRS anual a partir do rendimento bruto do ano, das retenções já efetuadas e da
 * percentagem de isenção do IRS Jovem (null quando o regime não se aplica).
 *
 * Passos:
 *  1. dedução específica da categoria A → rendimento coletável (nunca abaixo de 0)
 *  2. mínimo de existência (art. 70.º) → coletável após abatimento, se [aplicarMinimoExistencia]
 *  3. isenção do IRS Jovem = min(percentagem × coletável, 55 × IAS) — o teto é anual
 *  4. coleta = soma das fatias de cada escalão, cada uma arredondada ao cêntimo
 *  5. deduções à coleta (nunca abaixo de 0) e diferença = coleta − deduções − retenções
 *
 * O arredondamento segue o princípio do projeto (arredondar só no fim): a dedução e o
 * coletável não são arredondados a meio, e a taxa aplica-se ao valor exato. A coleta é a
 * exceção — como no [calcularIRS] mensal, cada fatia é arredondada antes de somar.
 *
 * [aplicarMinimoExistencia] vem desligado por omissão: o abatimento do art. 70.º só existe para
 * rendimentos predominantemente de trabalho dependente e a regra do n.º 4 a) depende do
 * agregado — quem simula é que sabe se o quer aplicar. Com os dois parâmetros novos no default,
 * o resultado é exatamente o mesmo de antes desta fase.
 */
fun calcularIrsAnual(
    rendimentoBrutoAnual: Long,
    retencoesEfetuadas: Long,
    ano: Int,
    escaloes: List<EscalaoAnual>,
    percentagemIrsJovem: Int?,
    deducoesColeta: Long = 0L,
    aplicarMinimoExistencia: Boolean = false
): ResultadoIrsAnual {

    val deducao = deducaoEspecificaCategoriaA(ano)
    val coletavel = (rendimentoBrutoAnual - deducao).coerceAtLeast(0L)

    // n.º 4 a): acima de 2,2 × 14 × IAS (16 543,60 €) por titular não há abatimento nenhum.
    val abatimento = if (
        aplicarMinimoExistencia &&
        rendimentoBrutoAnual <= LIMITE_NAO_APLICACAO_MINIMO_2026_MIL
    ) {
        abatimentoMinimoExistencia(rendimentoBrutoAnual, deducao, ano)
    } else 0L
    val coletavelAposAbatimento = (coletavel - abatimento).coerceAtLeast(0L)

    val iasAno = if (ano == ANO_IRS_ANUAL) IAS_2026_MIL else 0L
    val limiteIsencao = 55L * iasAno
    val isencao = if (percentagemIrsJovem != null) {
        minOf(
            dividirArredondando(coletavelAposAbatimento * percentagemIrsJovem, 100L),
            limiteIsencao
        )
    } else 0L
    val coletavelFinal = (coletavelAposAbatimento - isencao).coerceAtLeast(0L)

    var coleta = 0L
    var anterior = 0L
    for (e in escaloes) {
        if (coletavelFinal <= anterior) break
        val limite = minOf(coletavelFinal, e.limiteSuperior)
        val fatia = limite - anterior
        coleta += dividirArredondando(fatia * e.taxaBasisPoints, 10_000L)
        // A fatia seguinte começa no limite do escalão, não no que foi tributado agora.
        anterior = e.limiteSuperior
    }

    // Deduções à coleta: entram depois da coleta e nunca a tornam negativa — não devolvem dinheiro.
    val deducoesAplicadas = deducoesColeta.coerceAtLeast(0L)
    val coletaAposDeducoes = (coleta - deducoesAplicadas).coerceAtLeast(0L)

    return ResultadoIrsAnual(
        rendimentoBruto = rendimentoBrutoAnual,
        deducaoEspecifica = deducao,
        rendimentoColetavel = coletavel,
        isencaoIrsJovem = isencao,
        coletavelAposJovem = coletavelFinal,
        coletaDevida = coleta,
        retencoesEfetuadas = retencoesEfetuadas,
        diferenca = coletaAposDeducoes - retencoesEfetuadas,
        abatimentoMinimoExistencia = abatimento,
        coletavelAposAbatimento = coletavelAposAbatimento,
        deducoesColetaAplicadas = deducoesAplicadas,
        coletaAposDeducoes = coletaAposDeducoes
    )
}

/**
 * Isenção do IRS Jovem de um titular, em unidades de 1/10000 €: a percentagem sobre o
 * rendimento coletável (bruto − dedução específica − abatimento do art. 70.º), com o teto anual
 * de 55 × IAS a mandar quando a percentagem dá mais do que isso.
 *
 * É a mesma conta que [calcularIrsAnual] faz por dentro; existe à parte porque a tributação
 * conjunta tem de aplicar a isenção a cada titular antes de agregar os rendimentos. Daí o
 * [abatimentoMil]: quem tem o abatimento já calculado passa-o, para a isenção incidir sobre o
 * mesmo coletável que os escalões vão tributar.
 *
 * Um ano sem IAS tabelado devolve 0 — sem IAS não há limite legal para aplicar com rigor.
 */
fun calcularIsencaoIrsJovem(
    rendimentoBrutoAnual: Long,
    percentagem: Int?,
    ano: Int,
    abatimentoMil: Long = 0L
): Long {
    if (percentagem == null) return 0L
    val iasAno = if (ano == ANO_IRS_ANUAL) IAS_2026_MIL else return 0L
    val coletavel = (rendimentoBrutoAnual - deducaoEspecificaCategoriaA(ano) - abatimentoMil)
        .coerceAtLeast(0L)
    return minOf(dividirArredondando(coletavel * percentagem, 100L), 55L * iasAno)
}

/**
 * IRS em tributação conjunta (casado, 2 titulares), pelo mecanismo do quociente conjugal
 * (art. 69.º do CIRS):
 *
 *  1. o rendimento dos dois titulares soma-se
 *  2. divide-se por 2
 *  3. aplica-se a tabela progressiva a esse quociente
 *  4. multiplica-se a coleta por 2
 *
 * Beneficia o casal quando os rendimentos são desiguais: evita que o agregado caia num escalão
 * superior só por causa do somatório.
 *
 * A dedução específica é aplicada a cada titular **antes** de agregar (é o que a AT faz) e o
 * IRS Jovem também: a isenção sai do rendimento de quem a tem e só depois os dois se somam.
 * Por isso [isencaoIrsJovemTitular1] entra já em unidades de 1/10000 € — quem calcula é o
 * [calcularIsencaoIrsJovem], que faz a conta sobre o coletável do titular.
 *
 * A dedução específica, o abatimento do art. 70.º e o IRS Jovem são aplicados a cada titular
 * antes de agregar; o n.º 4 a) do art. 70.º olha para a soma dos dois rendimentos, porque o
 * limite legal é por sujeito passivo. As deduções à coleta entram no fim, sobre a coleta do casal.
 *
 * @param isencaoIrsJovemTitular1 parcela isenta do titular 1, já calculada (0 se não aplicável);
 *        quem a calcula passa-lhe também o abatimento do titular 1, para incidir no mesmo
 *        coletável que os escalões vão tributar
 * @param deducoesColeta deduções à coleta do agregado, subtraídas à coleta final
 * @param aplicarMinimoExistencia liga o abatimento do art. 70.º (desligado por omissão)
 */
fun calcularIrsAnualConjunta(
    rendimentoTitular1: Long,
    rendimentoTitular2: Long,
    retencoesEfetuadas: Long,
    ano: Int,
    escaloes: List<EscalaoAnual>,
    isencaoIrsJovemTitular1: Long,
    deducoesColeta: Long = 0L,
    aplicarMinimoExistencia: Boolean = false
): ResultadoIrsAnual {

    val deducao = deducaoEspecificaCategoriaA(ano)

    // n.º 4 a) do art. 70.º: o limite é por sujeito passivo, logo com 2 titulares é o dobro da
    // soma. Acima dele nenhum dos dois tem abatimento.
    val somaRendimentos = rendimentoTitular1 + rendimentoTitular2
    val aplicarAbatimento = aplicarMinimoExistencia &&
        somaRendimentos <= LIMITE_NAO_APLICACAO_MINIMO_2026_MIL * 2L
    val abatimento1 =
        if (aplicarAbatimento) abatimentoMinimoExistencia(rendimentoTitular1, deducao, ano) else 0L
    val abatimento2 =
        if (aplicarAbatimento) abatimentoMinimoExistencia(rendimentoTitular2, deducao, ano) else 0L

    // Cada titular desconta a sua dedução específica e o seu abatimento antes de agregar.
    val coletavel1 =
        (rendimentoTitular1 - isencaoIrsJovemTitular1 - deducao - abatimento1).coerceAtLeast(0L)
    val coletavel2 = (rendimentoTitular2 - deducao - abatimento2).coerceAtLeast(0L)

    val coletavelAgregado = coletavel1 + coletavel2
    val quociente = coletavelAgregado / 2L

    var coletaQuociente = 0L
    var anterior = 0L
    for (e in escaloes) {
        if (quociente <= anterior) break
        val limite = minOf(quociente, e.limiteSuperior)
        val fatia = limite - anterior
        coletaQuociente += dividirArredondando(fatia * e.taxaBasisPoints, 10_000L)
        // A fatia seguinte começa no limite do escalão, não no que foi tributado agora.
        anterior = e.limiteSuperior
    }

    // O quociente é a metade, portanto a coleta real do casal é o dobro do que ele paga.
    val coletaFinal = coletaQuociente * 2L

    // Deduções à coleta: do agregado, depois da coleta e nunca abaixo de 0.
    val deducoesAplicadas = deducoesColeta.coerceAtLeast(0L)
    val coletaAposDeducoes = (coletaFinal - deducoesAplicadas).coerceAtLeast(0L)

    return ResultadoIrsAnual(
        rendimentoBruto = rendimentoTitular1 + rendimentoTitular2,
        deducaoEspecifica = deducao * 2L,
        rendimentoColetavel = coletavelAgregado,
        isencaoIrsJovem = isencaoIrsJovemTitular1,
        // A isenção e os abatimentos já saíram dos coletáveis: o agregado é, na prática, o
        // coletável depois disso tudo.
        coletavelAposJovem = coletavelAgregado,
        coletaDevida = coletaFinal,
        retencoesEfetuadas = retencoesEfetuadas,
        diferenca = coletaAposDeducoes - retencoesEfetuadas,
        abatimentoMinimoExistencia = abatimento1 + abatimento2,
        coletavelAposAbatimento = coletavelAgregado,
        deducoesColetaAplicadas = deducoesAplicadas,
        coletaAposDeducoes = coletaAposDeducoes
    )
}

/**
 * A coleta que o agregado do casal pagaria se fosse um só titular: uma única dedução específica
 * e a tabela aplicada ao rendimento todo, sem o mecanismo do quociente.
 *
 * Não é um valor a pagar nem faz parte de nenhum resultado do simulador — serve só para o ecrã
 * mostrar quanto o quociente conjugal poupa ao casal face a essa tributação individual do
 * agregado. Quem apura o IRS da conjunta continua a ser o [calcularIrsAnualConjunta].
 */
fun coletaIndividualSobreAgregado(
    rendimentoAgregado: Long,
    ano: Int,
    escaloes: List<EscalaoAnual>,
    aplicarMinimoExistencia: Boolean = false
): Long {
    val deducao = deducaoEspecificaCategoriaA(ano)
    val coletavel = (rendimentoAgregado - deducao).coerceAtLeast(0L)

    // Se o casal tem direito ao abatimento, é só um titular a usufruir dele nesta comparação.
    val abatimento = if (
        aplicarMinimoExistencia &&
        rendimentoAgregado <= LIMITE_NAO_APLICACAO_MINIMO_2026_MIL
    ) {
        abatimentoMinimoExistencia(rendimentoAgregado, deducao, ano)
    } else 0L
    val coletavelFinal = (coletavel - abatimento).coerceAtLeast(0L)

    var coleta = 0L
    var anterior = 0L
    for (e in escaloes) {
        if (coletavelFinal <= anterior) break
        val limite = minOf(coletavelFinal, e.limiteSuperior)
        coleta += dividirArredondando((limite - anterior) * e.taxaBasisPoints, 10_000L)
        // A fatia seguinte começa no limite do escalão, não no que foi tributado agora.
        anterior = e.limiteSuperior
    }
    return coleta
}
