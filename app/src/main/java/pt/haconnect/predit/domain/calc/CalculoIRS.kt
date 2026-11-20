package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.EstadoCivil
import pt.haconnect.predit.domain.model.dividirArredondando

/**
 * Motor da retenção de IRS. Funções puras, sem Room nem Android.
 *
 * Valores em unidades de 1/10000 € (ver domain/model/Dinheiro.kt).
 */

/** Divisor dos basis points (taxa × 100000). */
const val TAXA_DENOMINADOR = 100_000L

/** Divisor do k das fórmulas compostas (coeficiente × 10000). */
const val K_DENOMINADOR = 10_000L

/**
 * Divisor da parcela da fórmula composta. A taxa vem em 1/100000, o k em 1/10000 e a
 * diferença (X − R) em 1/10000 € — o produto tem de ser dividido pelos dois denominadores.
 */
const val DENOMINADOR_FORMULA_COMPOSTA = TAXA_DENOMINADOR * K_DENOMINADOR

/** Categorias da tabela_irs: trabalho dependente (tabelas I a VII) e pensões (VIII a XI). */
const val CATEGORIA_TRABALHO = "TRABALHO"
const val CATEGORIA_PENSOES = "PENSOES"

/**
 * Campos do contrato → número da tabela do trabalho dependente (1..7), pelos subtítulos
 * dos XLSX oficiais (o mesmo mapa que está em TabelaIRSEntity):
 *   I   não casado sem dependentes OU casado 2 titulares
 *   II  não casado com um ou mais dependentes
 *   III casado, único titular
 *   IV  (I) com pessoa com deficiência
 *   V   (II) com pessoa com deficiência
 *   VI  casado 2 titulares com dependentes, com pessoa com deficiência
 *   VII (III) com pessoa com deficiência
 *
 * Desde a 8.4 a escolha parte dos campos do contrato, e não de um perfil em texto que
 * alguém tinha de acertar antes de chamar. O contrato ainda não tem pessoa com deficiência:
 * quem chama passa `pessoaComDeficiencia = false`.
 */
fun selecionarTabela(
    estadoCivil: String,
    titulares: Int,
    numeroDependentes: Int,
    pessoaComDeficiencia: Boolean
): Int {
    val casado = estadoCivil == EstadoCivil.CASADO.name
    // A UI aceita texto livre, mas 2 é o único valor válido no civil.
    // Valores >2 (impossíveis) caem no ramo "não casado 2 titulares" — aceitável.
    val casado2Titulares = casado && titulares == 2
    val casadoUnicoTitular = casado && titulares == 1
    val comDependentes = numeroDependentes > 0

    return when {
        pessoaComDeficiencia && casado2Titulares && comDependentes -> 6
        pessoaComDeficiencia && casado2Titulares -> 4
        pessoaComDeficiencia && casadoUnicoTitular -> 7
        pessoaComDeficiencia && !casado2Titulares && comDependentes -> 5
        pessoaComDeficiencia -> 4
        casadoUnicoTitular -> 3
        !casado2Titulares && comDependentes -> 2
        else -> 1
    }
}

/**
 * Retenção de IRS em unidades de 1/10000 € (0 se isento).
 *
 * Algoritmo:
 *  1. escalões do ano/região/categoria/tabela, por limiteAte crescente
 *  2. primeiro escalão com baseTributavel <= limiteAte; base acima do último → 0
 *  3. parcela a abater: `parcelaAbater` ou, nos escalões de fórmula composta, taxa × k × (X − R)
 *  4. IRS = (base × taxaBasisPoints) ÷ 100000 − parcela − parcelaAdicionalDep × dependentes
 *  5. negativo → 0
 *
 * A fórmula composta (2026-10-21) veio substituir o antigo "devolve 0": os dois primeiros
 * escalões das tabelas de 2026 não têm parcela fixa — o XLSX oficial traz `taxa x k x (X - R)`.
 * Sem isso, qualquer base entre o 1.º e o 2.º limite (ex.: 1 105,35 €, escalão 2 da tabela I)
 * dava 0,00 € de retenção, quando a tabela manda reter ~7,15 % nesse limite.
 *
 * O produto base × taxaBasisPoints passa de 10^11 (11 900 700 × 21 200), por isso
 * todos os passos são em Long.
 *
 * A categoria faz parte do filtro (8.4): trabalho (I–VII) e pensões (VIII–XI) são tabelas
 * distintas com numeração na mesma escala 1..11 — sem este filtro bastaria um número
 * repetido entre as duas para o motor escolher escalões da categoria errada.
 */
fun calcularIRS(
    baseTributavel: Long,
    tabelaNumero: Int,
    categoria: String,
    numDependentes: Int,
    ano: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>
): Long {
    val escaloes = tabelas
        .filter {
            it.ano == ano && it.regiao == regiao &&
                it.tabelaNumero == tabelaNumero && it.categoria == categoria
        }
        .sortedBy { it.limiteAte }

    require(escaloes.isNotEmpty()) {
        "sem escalões para $ano/${regiao.name}/$categoria/tabela $tabelaNumero"
    }

    val escalao = escaloes.firstOrNull { baseTributavel <= it.limiteAte } ?: return 0L

    val bruto = dividirArredondando(baseTributavel * escalao.taxaBasisPoints, TAXA_DENOMINADOR)
    val parcela = if (escalao.formulaComposta) {
        parcelaDaFormulaComposta(baseTributavel, escalao)
    } else {
        escalao.parcelaAbater
    }
    val liquido = bruto - parcela - escalao.parcelaAdicionalDep * numDependentes
    return if (liquido < 0L) 0L else liquido
}

/** Divisor da redução excepcional de novembro de 2026: retenção a 50 %. */
private const val DIVISOR_REDUCAO_NOVEMBRO_2026 = 2L

/**
 * Despacho n.º 12523-A/2026 — redução excepcional da retenção de IRS em novembro de 2026.
 *
 * Para rendimentos pagos entre 1 e 30 de novembro de 2026, a retenção apurada pelas tabelas
 * normais (Despacho n.º 233-A/2026) é reduzida a 50 %. Excepções:
 *   - rendimentos acima do limite superior do penúltimo escalão da tabela — sem redução;
 *   - Açores e Madeira — sem redução;
 *   - a partir de 1 de dezembro volta ao normal (mês ≠ 11 ou ano ≠ 2026).
 *
 * O limite do penúltimo escalão não é uma constante: varia com a tabela (I a VII) e com a
 * região. Sai da mesma lista de escalões que o [calcularIRS] usa, com o mesmo filtro e a mesma
 * ordenação por `limiteAte`. O último escalão termina em [Long.MAX_VALUE], por isso o penúltimo
 * é o último limite finito — e as tabelas não têm todas o mesmo número de escalões (a I tem 12,
 * a VII tem 6), pelo que o índice não pode ser fixo. Menos de dois escalões (tabela incompleta)
 * devolve a retenção normal: o fail-safe é não reduzir, nunca rebentar o recibo.
 *
 * Ordem no recibo: depois do [calcularIRS] e antes do
 * [pt.haconnect.predit.domain.calc.aplicarIrsJovem] — é a retenção das tabelas normais que a
 * lei reduz, e a taxa efectiva do IRS Jovem passa a ser a desta retenção. O arredondamento
 * segue a regra única do projecto ([dividirArredondando], half-up), aplicada só no fim.
 *
 * O mês é o do PAGAMENTO (o mês do recibo), não o das variáveis: com desfasamento de um mês as
 * variáveis de outubro são pagas em novembro e é o pagamento que a lei alcança.
 *
 * Limitação conhecida: o subsídio de Natal não é estimado pelo motor (é uma linha FIXA vinda do
 * recibo) nem entra na base de IRS, por isso a redução alcança apenas o D02 mensal.
 */
fun aplicarReducaoNovembro2026(
    retencaoNormal: Long,
    baseIRS: Long,
    tabelaNumero: Int,
    categoria: String,
    ano: Int,
    mes: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>
): Long {
    // A redução só se aplica em Novembro de 2026, no Continente.
    if (ano != 2026 || mes != 11 || regiao != RegiaoIRS.CONTINENTE) {
        return retencaoNormal
    }

    // Encontrar o limite do penúltimo escalão para a tabela.
    val escaloes = tabelas
        .filter {
            it.ano == ano && it.regiao == regiao &&
                it.categoria == categoria && it.tabelaNumero == tabelaNumero
        }
        .sortedBy { it.limiteAte }

    if (escaloes.size < 2) return retencaoNormal // Fallback seguro

    val limitePenultimo = escaloes[escaloes.size - 2].limiteAte

    return if (baseIRS <= limitePenultimo) {
        dividirArredondando(retencaoNormal, DIVISOR_REDUCAO_NOVEMBRO_2026) // Aplica os 50 %
    } else {
        retencaoNormal // Acima do limite, mantém
    }
}

/**
 * Parcela a abater dos escalões de fórmula composta: **taxa × k × (X − R)**.
 *
 * É a fórmula que o XLSX oficial das tabelas de 2026 traz nesses escalões (k e X vêm por
 * escalão: 2,6 e 1 273,85 € no 1.º, 1,35 e 1 554,83 € no 2.º, na tabela I do Continente).
 * Devolve valores na mesma unidade do resto: 1/10000 €.
 *
 * `X − R` é sempre positivo dentro do escalão (X está acima do limite do escalão), mas a
 * conta não é travada: se um dia a semente trouxer um X abaixo do limite, o resultado é o que
 * a fórmula diz — e os testes das tabelas apanham-no.
 */
fun parcelaDaFormulaComposta(baseTributavel: Long, escalao: EscalaoIRS): Long =
    dividirArredondando(
        escalao.taxaBasisPoints * escalao.coeficienteK * (escalao.limiteX - baseTributavel),
        DENOMINADOR_FORMULA_COMPOSTA
    )
