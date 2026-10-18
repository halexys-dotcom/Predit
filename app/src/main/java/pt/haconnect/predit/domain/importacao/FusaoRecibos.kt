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
 * So funde folhas COMPLEMENTARES. Se duas folhas do mesmo fecho trazem ambas linhas que
 * escrevem valor (com rubrica, `codigoInterno != null`), sao recibos diferentes e ficam
 * separados: e o caso da PSG, que em Maio/Agosto de 2016 imprimiu o suplemento do subsidio
 * de Natal com o mesmo fecho 31-05/31-08 da folha principal. Sem esta guarda, o suplemento
 * (54,30) ganhava ao recibo do mes (801,82) e so esse valor era importado. As linhas
 * informativas nao contam: a 2.a folha da Fundacao so traz F106I/F35/F40.
 *
 * A lista sai pela ordem do PDF ([ReciboImportado.pagina]) — e a ordem em que o utilizador
 * ve as folhas, e por isso a mais natural no seletor de recibos.
 */
internal fun fundirRecibos(recibos: List<ReciboImportado>): List<ReciboImportado> {
    return recibos.groupBy { Triple(it.empresaEmissora, it.mesReferencia, it.dataFecho) }
        .flatMap { (_, grupo) ->
            val comValor = grupo.count { recibo -> recibo.linhas.any { it.codigoInterno != null } }
            if (grupo.size == 1 || comValor > 1) return@flatMap grupo

            val comLinhas = grupo.firstOrNull { it.linhas.isNotEmpty() } ?: grupo.first()
            val comTotais = grupo.firstOrNull { it.totalAbonos > 0L } ?: comLinhas

            listOf(
                comLinhas.copy(
                    totalAbonos = comTotais.totalAbonos,
                    totalDescontos = comTotais.totalDescontos,
                    liquido = comTotais.liquido,
                    codigosDesconhecidos = grupo.flatMap { it.codigosDesconhecidos }.distinct(),
                    avisos = grupo.flatMap { it.avisos }.distinct()
                )
            )
        }
        .sortedBy { it.pagina }
}
