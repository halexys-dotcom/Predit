package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.dividirArredondando

/**
 * Motor da retenção de IRS. Funções puras, sem Room nem Android.
 *
 * Valores em unidades de 1/10000 € (ver domain/model/Dinheiro.kt).
 */

/**
 * Perfis das tabelas I a VII (trabalho dependente), pelos subtítulos dos XLSX oficiais:
 *   I   não casado sem dependentes OU casado 2 titulares
 *   II  não casado com um ou mais dependentes
 *   III casado, único titular
 *   IV  (I) com pessoa com deficiência
 *   V   (II) com pessoa com deficiência
 *   VI  casado 2 titulares com dependentes, com pessoa com deficiência
 *   VII (III) com pessoa com deficiência
 */
enum class PerfilIRS { I, II, III, IV, V, VI, VII }

const val PERFIL_NAO_CASADO_SEM_DEP = "NAO_CASADO_SEM_DEP"
const val PERFIL_NAO_CASADO_COM_DEP = "NAO_CASADO_COM_DEP"
const val PERFIL_CASADO_2_TITULARES = "CASADO_2_TITULARES"
const val PERFIL_CASADO_1_TITULAR = "CASADO_1_TITULAR"

/**
 * Variante necessária para chegar à tabela VI (casado 2 titulares com dependentes
 * e pessoa com deficiência) — o comando só listava quatro perfis.
 */
const val PERFIL_CASADO_2_TITULARES_COM_DEP = "CASADO_2_TITULARES_COM_DEP"

/** Divisor dos basis points (taxa × 100000). */
const val TAXA_DENOMINADOR = 100_000L

/**
 * Perfil + pessoa com deficiência → número da tabela do trabalho dependente (1..7).
 * As pensões (tabelas VIII a XI) usam os mesmos perfis deslocados em 7 — ficam para a 8.2b.
 */
fun selecionarTabela(perfil: String, pessoaDeficiente: Boolean): Int = when (perfil) {
    PERFIL_NAO_CASADO_SEM_DEP, PERFIL_CASADO_2_TITULARES -> if (pessoaDeficiente) 4 else 1
    PERFIL_NAO_CASADO_COM_DEP -> if (pessoaDeficiente) 5 else 2
    PERFIL_CASADO_1_TITULAR -> if (pessoaDeficiente) 7 else 3
    PERFIL_CASADO_2_TITULARES_COM_DEP -> if (pessoaDeficiente) 6 else 1
    else -> throw IllegalArgumentException("perfil desconhecido: $perfil")
}

/**
 * Retenção de IRS em unidades de 1/10000 € (0 se isento).
 *
 * Algoritmo:
 *  1. primeiro escalão (por limiteAte) com baseTributavel <= limiteAte
 *  2. formulaComposta (taxa × k × (X − R)) → 0: o utilizador real não cai nestes escalões
 *  3. IRS = (base × taxaBasisPoints) ÷ 100000 − parcelaAbater − parcelaAdicionalDep × dependentes
 *  4. negativo → 0
 *
 * O produto base × taxaBasisPoints passa de 10^11 (11 900 700 × 21 200), por isso
 * todos os passos são em Long.
 */
fun calcularIRS(
    baseTributavel: Long,
    tabelaNumero: Int,
    numDependentes: Int,
    ano: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>
): Long {
    val escaloes = tabelas
        .filter { it.ano == ano && it.regiao == regiao && it.tabelaNumero == tabelaNumero }
        .sortedBy { it.limiteAte }

    require(escaloes.isNotEmpty()) {
        "sem escalões para $ano/${regiao.name}/tabela $tabelaNumero"
    }

    val escalao = escaloes.firstOrNull { baseTributavel <= it.limiteAte }
        ?: throw IllegalArgumentException(
            "base $baseTributavel acima do último escalão da tabela $tabelaNumero"
        )

    if (escalao.formulaComposta) return 0L

    val bruto = dividirArredondando(baseTributavel * escalao.taxaBasisPoints, TAXA_DENOMINADOR)
    val liquido = bruto - escalao.parcelaAbater - escalao.parcelaAdicionalDep * numDependentes
    return if (liquido < 0L) 0L else liquido
}
