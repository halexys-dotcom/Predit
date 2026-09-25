package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.dividirArredondando

/**
 * IRS Jovem (Fase 20): isenção parcial de retenção para quem começou a trabalhar há poucos
 * anos. Domínio puro — sem Room, sem Android.
 *
 * O regime não substitui a retenção normal: parte-se da taxa efetiva do mês (retenção
 * normal ÷ base), isola-se a parte isenta e aplica-se essa taxa só ao que sobra. A lei
 * limita a isenção anual a 55 × IAS, o que dá um teto mensal de 55 × IAS ÷ 14.
 */

/**
 * IAS de cada ano, nas unidades deste projeto (1/10000 €): 537,13 € = 5 371 300.
 * Um ano que não esteja aqui não aplica IRS Jovem — é o caso dos anos cujo IAS ainda não
 * foi tabelado na app (o motor devolve a retenção normal, em vez de rebentar).
 */
private val IAS_POR_ANO = mapOf(
    2025 to 5_225_000L,
    2026 to 5_371_300L,
)

/** Limite legal da isenção anual, em IAS (55 × IAS por ano). */
private const val MULTIPLICADOR_IAS = 55L

/** Nº de prestações do ano: o limite anual reparte-se por 14 meses. */
private const val PRESTACOES = 14L

/** Idade (a 31/12 do ano em causa) até à qual o regime existe: acima disto, já não se enquadra. */
const val IDADE_MAXIMA_IRS_JOVEM = 35

/** Anos civis de obtenção de rendimentos em que o regime existe (o 10.º é o último). */
const val ANOS_MAXIMOS_IRS_JOVEM = 10

/**
 * Devolve a percentagem de isenção aplicável (100, 75, 50, 25) ou null se o jovem não se
 * enquadra (idade > 35 no final do ano, ou fora dos 10 primeiros anos de rendimentos).
 *
 * @param anoNascimento ano de nascimento do titular
 * @param anoPrimeiroRendimento ano civil do 1.º rendimento das categorias A/B declarado
 *                              como sujeito passivo (não dependente)
 * @param anoAtual ano civil em causa (ex: 2026)
 */
fun percentagemIsencaoIrsJovem(
    anoNascimento: Int,
    anoPrimeiroRendimento: Int,
    anoAtual: Int
): Int? {
    val idade = anoAtual - anoNascimento
    if (idade > IDADE_MAXIMA_IRS_JOVEM) return null

    val anoDeObtencao = anoAtual - anoPrimeiroRendimento + 1
    return when (anoDeObtencao) {
        1 -> 100
        in 2..4 -> 75
        in 5..7 -> 50
        in 8..ANOS_MAXIMOS_IRS_JOVEM -> 25
        else -> null
    }
}

/**
 * Aplica o regime do IRS Jovem à retenção mensal.
 *
 * Regra (folheto AT 2025):
 *   1. Calcula-se a taxa efetiva = retenção normal / base
 *   2. Calcula-se a parte isenta = min(base × %isenção, 55 × IAS / 14)
 *   3. Aplica-se a taxa efetiva APENAS à parte não isenta
 *
 * O arredondamento é só no fim (dividirArredondando), como no resto do motor: a taxa
 * efetiva não é arredondada antes de ser aplicada.
 *
 * Fail-safe: sem base, sem retenção, ou num ano sem IAS tabelado, devolve a retenção
 * normal — exatamente o que o recibo teria sem IRS Jovem.
 */
fun aplicarIrsJovem(
    baseMil: Long,
    retencaoNormalMil: Long,
    percentagem: Int,
    anoAtual: Int
): Long {
    if (baseMil <= 0L || retencaoNormalMil <= 0L) return retencaoNormalMil
    val ias = IAS_POR_ANO[anoAtual] ?: return retencaoNormalMil
    val limiteMensalMil = (MULTIPLICADOR_IAS * ias) / PRESTACOES

    val isencaoTeorica = (baseMil * percentagem) / 100L
    val parteIsenta = minOf(isencaoTeorica, limiteMensalMil)
    val parteTributavel = baseMil - parteIsenta
    if (parteTributavel <= 0L) return 0L

    // taxa efetiva = retencaoNormal / base; arredondar só no fim
    return dividirArredondando(retencaoNormalMil * parteTributavel, baseMil)
}
