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
 * Despacho n.º 12523-A/2026: em **novembro de 2026**, no **Continente**, a retenção mensal de IRS
 * desce a metade para quem fica até ao limite superior do penúltimo escalão da sua tabela.
 *
 * A redução não é uma taxa nova: é 50 % da retenção normal, arredondada ao meio para cima (a
 * mesma regra de [dividirArredondando]). Quem está acima do penúltimo escalão mantém a retenção
 * inteira, e Açores/Madeira não são abrangidos.
 */
private const val ANO_REDUCAO_EXCECIONAL_IRS = 2026
private const val MES_REDUCAO_EXCECIONAL_IRS = 11
private const val PERCENTAGEM_REDUCAO_EXCECIONAL_IRS = 50L
private const val DENOMINADOR_PERCENTAGEM = 100L

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
    return calcularIRSComDetalhe(
        baseTributavel = baseTributavel,
        tabelaNumero = tabelaNumero,
        categoria = categoria,
        numDependentes = numDependentes,
        ano = ano,
        regiao = regiao,
        tabelas = tabelas
    ).valorMil
}

/**
 * Retenção de IRS com o escalão que a produziu — é o que a conferência do recibo mostra debaixo do
 * D02 ("Escalão 5 · Taxa 21,2%"). Mesma conta de [calcularIRS], que agora delega aqui.
 *
 * [ResultadoIRS.valorMil] é a retenção da **base mensal**. A retenção autónoma dos subsídios
 * (Natal/férias) fica de fora: tem base e escalão próprios e só se soma ao D02 no fim, no
 * estimador.
 */
fun calcularIRSComDetalhe(
    baseTributavel: Long,
    tabelaNumero: Int,
    categoria: String,
    numDependentes: Int,
    ano: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>
): ResultadoIRS {
    val escaloes = escaloesIRSDe(tabelas, ano, regiao, categoria, tabelaNumero)

    // Base acima do último limite: a tabela não prevê retenção e não há escalão a mostrar.
    val escalao = escaloes.firstOrNull { baseTributavel <= it.limiteAte }
        ?: return ResultadoIRS(
            valorMil = 0L, numeroEscalao = 0, taxaBasisPoints = 0, taxaEfetivaBasisPoints = 0
        )

    val bruto = dividirArredondando(baseTributavel * escalao.taxaBasisPoints, TAXA_DENOMINADOR)
    val parcela = if (escalao.formulaComposta) {
        parcelaDaFormulaComposta(baseTributavel, escalao)
    } else {
        escalao.parcelaAbater
    }
    val liquido = bruto - parcela - escalao.parcelaAdicionalDep * numDependentes
    val valor = if (liquido < 0L) 0L else liquido
    return ResultadoIRS(
        valorMil = valor,
        // ordemEscalao = 0 na semente é o 1.º escalão do XLSX (o de taxa 0): a numeração que o
        // utilizador vê é 1-based. Ex.: ordemEscalao 5 (taxa 24,1 %) é o "Escalão 6" da tabela.
        numeroEscalao = escalao.ordemEscalao + 1,
        taxaBasisPoints = escalao.taxaBasisPoints,
        taxaEfetivaBasisPoints = taxaEfetivaBasisPoints(valor, baseTributavel)
    )
}

/**
 * Retenção de IRS **e** o escalão que a produziu — o detalhe por trás do D02. Produzido por
 * [calcularIRSComDetalhe] (retenção normal) e por [calcularIRSMensalComDetalhe] (retenção mensal, já
 * com IRS Jovem e redução de novembro).
 *
 * [valorMil]: retenção em 1/10000 €, arredondada ao meio para cima e nunca negativa.
 *
 * [numeroEscalao]: numeração **1-based** do XLSX oficial, tal como o utilizador a vê nas tabelas
 * (a linha `ordemEscalao = 0` da semente — a de taxa 0, "até 920,00 €" na tabela I de 2026 — é o
 * escalão 1). `0` significa que a base passou o último limite da tabela: não há escalão, retenção
 * 0,00 €.
 *
 * v2.2.1: a conferência do recibo deixou de mostrar o escalão (a UI dizia "Escalão 5 · Taxa…" e o
 * número não ajudava a conferir nada); o campo fica porque distingue isenção (escalão de taxa 0) de
 * "fora da tabela" (`0`), que a UI continua a anunciar em [pt.haconnect.predit.ui.horario.notasIrs].
 *
 * [taxaBasisPoints]: taxa **marginal** do escalão, na mesma unidade da tabela (21,2 % → 21 200, ou
 * seja 1/100000 do valor). Não é a taxa efetiva da retenção: essa sai do valor final menos as
 * parcelas a abater, e o utilizador vê-a no recibo.
 *
 * [taxaEfetivaBasisPoints]: taxa **efetiva** — a percentagem que a retenção representa sobre a base
 * de cálculo, que é a que o recibo da empresa imprime ao lado da rubrica ("IRS (Venc. 4,29%)").
 * Em basis points de 0,01 ponto percentual (4,14 % → 414), calculada por [taxaEfetivaBasisPoints].
 * É a taxa que a conferência mostra no título da linha ("IRS (4,14%)"), com duas casas: anunciar a
 * marginal confundia (24,1 % de escalão para um IRS que vale 4,1 % da base).
 *
 * [reducaoNovembro2026]: o Despacho n.º 12523-A/2026 aplicou-se (novembro de 2026, Continente, base
 * até ao penúltimo escalão) — a retenção foi a metade. A UI diz "(Redução 50% Nov/2026)".
 *
 * [percentagemIrsJovem]: a percentagem de isenção do IRS Jovem que entrou na conta (100/75/50/25),
 * ou `null` quando o regime não se aplica. A taxa mostrada continua a ser a do escalão: o regime
 * isenta parte da retenção, não muda o escalão da base.
 */
data class ResultadoIRS(
    val valorMil: Long,
    val numeroEscalao: Int,
    val taxaBasisPoints: Int,
    /** 0,01 ponto percentual: 4,14 % → 414. Ver [taxaEfetivaBasisPoints]. */
    val taxaEfetivaBasisPoints: Int,
    val reducaoNovembro2026: Boolean = false,
    val percentagemIrsJovem: Int? = null,
    /**
     * v2.2.0: IRS autónomo dos subsídios pagos no mês (Natal, férias), em 1/10000 €. Soma-se ao D02
     * mas **não** a [valorMil] nem a [taxaEfetivaBasisPoints]: aqueles são os da base mensal, que é
     * a que a tabela tributa. Sem subsídios (ou sem IRS sobre eles) fica 0.
     *
     * É o que explica a conferência quando o D02 do mês é maior do que a taxa efetiva × base — ver
     * [pt.haconnect.predit.ui.horario.notasIrs].
     */
    val irsAutonomoSubsidiosMil: Long = 0L
) {
    /** A base caiu no escalão de taxa zero: a retenção é 0,00 € por isenção, não por conta. */
    val isento: Boolean get() = taxaBasisPoints == 0
}

/**
 * Taxa efetiva da retenção: quanto o IRS vale sobre a base de cálculo, em basis points de 0,01 ponto
 * percentual (4,14 % → 414). `valorMil × 10000 ÷ base`, arredondado ao meio para cima.
 *
 * É a taxa que o recibo em papel mostra ("IRS (Venc. 4,29%)"), ao contrário da marginal do escalão
 * — 24,1 % num escalão em que o IRS retido vale 7,23 % da base. Sem base (0) não há percentagem: 0.
 *
 * A unidade NÃO é a de [ResultadoIRS.taxaBasisPoints]: a tabela guarda as taxas em 1/100000 do valor
 * (21,2 % → 21 200), e aqui a conta é a percentagem × 10000 (4,14 % → 414). A UI mostra esta no
 * título da linha do IRS — ver [pt.haconnect.predit.ui.horario.tituloRubricaIrs].
 */
internal fun taxaEfetivaBasisPoints(valorMil: Long, baseTributavel: Long): Int {
    if (baseTributavel <= 0L) return 0
    return dividirArredondando(valorMil * ESCALA_TAXA_EFETIVA, baseTributavel).toInt()
}

/** 1/10000 do valor = 0,01 ponto percentual: 4,14 % → 414. */
private const val ESCALA_TAXA_EFETIVA = 10_000L

/**
 * Retenção mensal de IRS, com a redução excecional do Despacho n.º 12523-A/2026 aplicada por
 * cima da retenção normal. É este o ponto de entrada mensal do estimador.
 *
 * A ordem das duas correções é a decidida na investigação: **primeiro o IRS Jovem, depois a
 * redução de 50 %**. O IRS Jovem isenta uma parte da retenção normal (com o seu limite mensal) e
 * só o valor que sobra é reduzido a metade — por isso [percentagemIrsJovem] é opcional aqui em
 * vez de ser aplicado depois, pelo chamador, sobre um valor já reduzido.
 *
 * @param mes mês do **pagamento** (o do recibo), não o mês das variáveis: é o pagamento que
 *        determina se a retenção cai em novembro de 2026.
 * @param percentagemIrsJovem percentagem do IRS Jovem a isentar (0–100), ou `null` quando o
 *        contrato não tem direito. A isenção incide sobre a retenção normal e não altera a
 *        elegibilidade à redução — esta compara a **base tributável** com o penúltimo escalão.
 */
fun calcularIRSMensal(
    baseTributavel: Long,
    tabelaNumero: Int,
    categoria: String,
    numDependentes: Int,
    ano: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>,
    mes: Int,
    percentagemIrsJovem: Int? = null
): Long {
    return calcularIRSMensalComDetalhe(
        baseTributavel = baseTributavel,
        tabelaNumero = tabelaNumero,
        categoria = categoria,
        numDependentes = numDependentes,
        ano = ano,
        regiao = regiao,
        tabelas = tabelas,
        mes = mes,
        percentagemIrsJovem = percentagemIrsJovem
    ).valorMil
}

/**
 * [calcularIRSMensal] com o escalão e a taxa à vista. O escalão é o da retenção **normal**: o IRS
 * Jovem e a redução de novembro mexem no valor, não no sítio onde a base caiu — por isso viajam
 * como bandeiras ([ResultadoIRS.percentagemIrsJovem], [ResultadoIRS.reducaoNovembro2026]) e não
 * como um escalão diferente.
 *
 * O IRS Jovem entra antes da redução, como em [calcularIRSMensal]: isenta parte da retenção normal
 * e só o que sobra é reduzido a metade.
 */
fun calcularIRSMensalComDetalhe(
    baseTributavel: Long,
    tabelaNumero: Int,
    categoria: String,
    numDependentes: Int,
    ano: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>,
    mes: Int,
    percentagemIrsJovem: Int? = null
): ResultadoIRS {
    val normal = calcularIRSComDetalhe(
        baseTributavel, tabelaNumero, categoria, numDependentes, ano, regiao, tabelas
    )
    val aposIrsJovem = if (percentagemIrsJovem != null) {
        aplicarIrsJovem(
            baseMil = baseTributavel,
            retencaoNormalMil = normal.valorMil,
            percentagem = percentagemIrsJovem,
            anoAtual = ano
        )
    } else {
        normal.valorMil
    }
    val valor = aplicarReducaoExcecionalNovembro2026(
        irs = aposIrsJovem,
        baseTributavel = baseTributavel,
        tabelaNumero = tabelaNumero,
        categoria = categoria,
        ano = ano,
        mes = mes,
        regiao = regiao,
        tabelas = tabelas
    )
    return normal.copy(
        valorMil = valor,
        // A taxa efetiva acompanha o valor: em novembro é metade da de dezembro para a mesma base.
        taxaEfetivaBasisPoints = taxaEfetivaBasisPoints(valor, baseTributavel),
        // A bandeira vem da elegibilidade, não de "o valor mudou": uma retenção de 0,0001 € fica
        // igual depois de reduzida, mas a redução aplicou-se na mesma.
        reducaoNovembro2026 = temReducaoExcecionalNovembro2026(
            baseTributavel, tabelaNumero, categoria, ano, mes, regiao, tabelas
        ),
        percentagemIrsJovem = percentagemIrsJovem
    )
}

/**
 * Aplica a redução de 50 % do Despacho n.º 12523-A/2026 a uma retenção já calculada (e já com o
 * IRS Jovem, se for o caso). Devolve [irs] intacto fora de novembro de 2026, fora do Continente
 * ou quando a base tributável passa o limite do penúltimo escalão da tabela do contrato.
 */
fun aplicarReducaoExcecionalNovembro2026(
    irs: Long,
    baseTributavel: Long,
    tabelaNumero: Int,
    categoria: String,
    ano: Int,
    mes: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>
): Long {
    if (!temReducaoExcecionalNovembro2026(
            baseTributavel, tabelaNumero, categoria, ano, mes, regiao, tabelas
        )
    ) {
        return irs
    }
    return dividirArredondando(irs * PERCENTAGEM_REDUCAO_EXCECIONAL_IRS, DENOMINADOR_PERCENTAGEM)
}

/**
 * Condições do Despacho n.º 12523-A/2026: novembro de 2026, Continente, e base tributável até ao
 * **limite superior do penúltimo escalão** da tabela concreta do contrato.
 *
 * O limite não é constante: cada tabela (I a VII, e as de pensões) tem o seu penúltimo escalão.
 * Vem dos escalões filtrados, por isso acompanha automaticamente uma tabela nova — não há aqui
 * nenhum valor de 2026 escrito à mão.
 */
fun temReducaoExcecionalNovembro2026(
    baseTributavel: Long,
    tabelaNumero: Int,
    categoria: String,
    ano: Int,
    mes: Int,
    regiao: RegiaoIRS,
    tabelas: List<EscalaoIRS>
): Boolean {
    if (ano != ANO_REDUCAO_EXCECIONAL_IRS ||
        mes != MES_REDUCAO_EXCECIONAL_IRS ||
        regiao != RegiaoIRS.CONTINENTE
    ) {
        return false
    }
    val escaloes = escaloesIRSDe(tabelas, ano, regiao, categoria, tabelaNumero)
    // Com menos de dois escalões não há penúltimo: nunca se aplica.
    if (escaloes.size < 2) return false
    return baseTributavel <= escaloes[escaloes.size - 2].limiteAte
}

/** Escalões do ano/região/categoria/tabela, por [EscalaoIRS.limiteAte] crescente. */
private fun escaloesIRSDe(
    tabelas: List<EscalaoIRS>,
    ano: Int,
    regiao: RegiaoIRS,
    categoria: String,
    tabelaNumero: Int
): List<EscalaoIRS> {
    val escaloes = tabelas
        .filter {
            it.ano == ano && it.regiao == regiao &&
                it.tabelaNumero == tabelaNumero && it.categoria == categoria
        }
        .sortedBy { it.limiteAte }

    require(escaloes.isNotEmpty()) {
        "sem escalões para $ano/${regiao.name}/$categoria/tabela $tabelaNumero"
    }
    return escaloes
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
