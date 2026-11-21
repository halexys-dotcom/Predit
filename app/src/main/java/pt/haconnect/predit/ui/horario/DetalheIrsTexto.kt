package pt.haconnect.predit.ui.horario

import pt.haconnect.predit.domain.calc.ResultadoIRS

/**
 * Título da rubrica de IRS na conferência do recibo (v2.2.1): a taxa **efetiva** do mês entra no
 * nome, como o catálogo já faz com a Segurança Social ("Segurança Social (11%)") — o utilizador lê
 * "IRS (4,31%)" e o valor da linha logo por baixo, sem uma segunda linha a explicar escalões.
 *
 * Função pura (sem Compose), para o texto ser testável em JUnit, como [linhasVisiveis]. Regras:
 * - a taxa mostrada é a **efetiva** ([ResultadoIRS.taxaEfetivaBasisPoints]), a que o recibo em papel
 *   imprime ("IRS (Venc. 4,29%)"); a marginal do escalão (24,1 %) confundia o utilizador, e o
 *   número do escalão não lhe dizia nada — os dois deixaram de se mostrar (v2.2.1);
 * - com duas casas fixas e vírgula decimal ([textoDaTaxaEfetivaDuasCasas]): 431 → "(4,31%)";
 * - sem escalão (`numeroEscalao = 0` — a base passou o último limite da tabela) não há taxa a
 *   anunciar: fica o nome do catálogo, e a nota "Fora da tabela" vai em [notasIrs];
 * - escalão de taxa zero é o de isenção da tabela (até 920,00 € na tabela I de 2026): diz
 *   "(Isento)" — não "(0,00%)", que parece uma retenção que falhou;
 * - sem detalhe (mês sem estimativa, ou rubrica que não é o IRS) o título é o nome do catálogo.
 */
internal fun tituloRubricaIrs(nome: String, detalhe: ResultadoIRS?): String {
    if (detalhe == null || detalhe.numeroEscalao <= 0) return nome
    if (detalhe.isento) return "$nome (Isento)"
    return "$nome (${textoDaTaxaEfetivaDuasCasas(detalhe.taxaEfetivaBasisPoints)}%)"
}

/**
 * Notas da linha do IRS, por baixo do título — só quando há o que explicar; `null` nos meses sem
 * nada de especial, e aí a linha do IRS pesa o mesmo que as outras rubricas.
 *
 * - IRS Jovem: a taxa do título já reflecte a isenção, mas sem esta nota um valor baixo parecia um
 *   erro de conta;
 * - redução de novembro de 2026 (Despacho n.º 12523-A/2026): a retenção foi a metade, e a taxa do
 *   título é metade da habitual — sem a nota parece que a tabela mudou;
 * - IRS autónomo dos subsídios (Natal, férias): o D02 inclui um IRS que não passou pela base
 *   mensal, por isso o valor da linha não é a taxa × base (era o 92,35 € de dezembro contra os
 *   4,31 % do mês) — a nota diz o que o valor leva a mais;
 * - base acima do último limite da tabela: não há escalão nem taxa a anunciar, só a retenção zero.
 *
 * As notas vão entre parênteses, separadas por espaço, na ordem: fora da tabela → IRS Jovem →
 * redução de novembro → IRS dos subsídios.
 */
internal fun notasIrs(detalhe: ResultadoIRS?): String? {
    if (detalhe == null) return null
    val notas = buildList {
        if (detalhe.numeroEscalao <= 0) add("Fora da tabela")
        // `irsAutonomoSubsidiosMil > 0` e não "o mês tem subsídios": só quando há mesmo IRS autónomo
        // somado ao D02 é que a taxa da base mensal deixa de explicar o valor da linha.
        detalhe.percentagemIrsJovem?.let { add("IRS Jovem $it%") }
        if (detalhe.reducaoNovembro2026) add("Redução 50% Nov/2026")
        if (detalhe.irsAutonomoSubsidiosMil > 0L) add("inclui IRS autónomo de subsídios")
    }
    return notas.joinToString(separator = " ") { "($it)" }.ifEmpty { null }
}

/**
 * Taxa efetiva em percentagem com **duas casas fixas** e vírgula decimal, como o título mostra:
 * 509 → "5,09" · 431 → "4,31" · 400 → "4,00" · 0 → "0,00".
 *
 * [ResultadoIRS.taxaEfetivaBasisPoints] conta em 0,01 ponto percentual (4,31 % → 431), por isso a
 * percentagem é `basisPoints ÷ 100` e as casas saem do resto. Sem `String.format`: não depende do
 * locale do telefone (com locale inglês sairia "4.31").
 */
internal fun textoDaTaxaEfetivaDuasCasas(taxaEfetivaBasisPoints: Int): String {
    val inteiros = taxaEfetivaBasisPoints / 100
    val centesimos = taxaEfetivaBasisPoints % 100
    return "$inteiros,${centesimos.toString().padStart(2, '0')}"
}
