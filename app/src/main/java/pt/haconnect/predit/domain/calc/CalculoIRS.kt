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
 *  3. formulaComposta (taxa × k × (X − R)) → 0: o utilizador real não cai nestes escalões
 *  4. IRS = (base × taxaBasisPoints) ÷ 100000 − parcelaAbater − parcelaAdicionalDep × dependentes
 *  5. negativo → 0
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

    if (escalao.formulaComposta) return 0L

    val bruto = dividirArredondando(baseTributavel * escalao.taxaBasisPoints, TAXA_DENOMINADOR)
    val liquido = bruto - escalao.parcelaAbater - escalao.parcelaAdicionalDep * numDependentes
    return if (liquido < 0L) 0L else liquido
}
