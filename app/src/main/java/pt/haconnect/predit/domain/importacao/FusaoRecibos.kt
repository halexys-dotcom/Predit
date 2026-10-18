package pt.haconnect.predit.domain.importacao

/**
 * Funde folhas duplicadas do mesmo recibo. A chave (empresa, mes, dataFecho) e o que
 * distingue duplicados verdadeiros (Fundacao: Original + Duplicado com a mesma data) de
 * suplementos do mesmo mes (ICTS Julho: fecho 31/07 vs 21/07).
 *
 * A PRIMAVERA so escreve os valores da linha "Total" na ultima folha: a 1.a traz as linhas
 * e a 2.a os totais. Ficamos com as linhas de uma e os totais da outra, e juntamos os
 * avisos e os codigos desconhecidos das duas, sem repeticoes.
 *
 * A lista sai pela ordem do PDF ([ReciboImportado.pagina]) — e a ordem em que o utilizador
 * ve as folhas, e por isso a mais natural no seletor de recibos.
 */
internal fun fundirRecibos(recibos: List<ReciboImportado>): List<ReciboImportado> {
    return recibos.groupBy { Triple(it.empresaEmissora, it.mesReferencia, it.dataFecho) }
        .map { (_, grupo) ->
            if (grupo.size == 1) return@map grupo.first()

            val comLinhas = grupo.firstOrNull { it.linhas.isNotEmpty() } ?: grupo.first()
            val comTotais = grupo.firstOrNull { it.totalAbonos > 0L } ?: comLinhas

            comLinhas.copy(
                totalAbonos = comTotais.totalAbonos,
                totalDescontos = comTotais.totalDescontos,
                liquido = comTotais.liquido,
                codigosDesconhecidos = grupo.flatMap { it.codigosDesconhecidos }.distinct(),
                avisos = grupo.flatMap { it.avisos }.distinct()
            )
        }
        .sortedBy { it.pagina }
}
