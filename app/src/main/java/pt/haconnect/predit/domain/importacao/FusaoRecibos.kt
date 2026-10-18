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
 * A ordem da lista é cronológica pela data de fecho ([ReciboImportado.dataFecho]) — é a ordem
 * em que os meses acontecem, e é essa que o seletor de recibos mostra. Folhas com o mesmo
 * fecho (duplicados, suplementos) mantêm a ordem do PDF, porque `sortedBy` é estável. Um
 * recibo sem fecho reconhecível não tem lugar na cronologia: vai para o fim em vez de saltar
 * para a frente, para não parecer o primeiro do PDF.
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
        // 28g: cronológica pelo fecho. `sortedBy` é estável, por isso as folhas do mesmo fecho
        // (duplicados e suplementos) ficam pela ordem do PDF. O `?: Long.MAX_VALUE` põe no fim
        // os recibos sem fecho legível — sem isto o `null` ordenava à frente de todas as datas.
        .sortedBy { it.dataFecho ?: Long.MAX_VALUE }
}
