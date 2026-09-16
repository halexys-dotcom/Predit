package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.TabelaIRSDao
import pt.haconnect.predit.data.local.TabelaIRSEntity
import pt.haconnect.predit.domain.calc.EscalaoIRS
import pt.haconnect.predit.domain.calc.RegiaoIRS

/**
 * Ponte entre a tabela_irs (Room) e o domínio: o motor do IRS só conhece [EscalaoIRS].
 */
class TabelaIRSRepository(private val dao: TabelaIRSDao) {

    fun observarPorAno(ano: Int): Flow<List<EscalaoIRS>> =
        dao.observarPorAno(ano).map { lista -> lista.map { it.paraEscalao() } }

    suspend fun listarPorAno(ano: Int): List<EscalaoIRS> =
        dao.listarPorAno(ano).map { it.paraEscalao() }
}

fun TabelaIRSEntity.paraEscalao(): EscalaoIRS = EscalaoIRS(
    ano = ano,
    regiao = try { RegiaoIRS.valueOf(regiao) } catch (_: Exception) { RegiaoIRS.CONTINENTE },
    categoria = categoria,
    tabelaNumero = tabelaNumero,
    ordemEscalao = ordemEscalao,
    limiteAte = limiteAte,
    taxaBasisPoints = taxaBasisPoints,
    parcelaAbater = parcelaAbater,
    parcelaAdicionalDep = parcelaAdicionalDep,
    formulaComposta = formulaComposta
)
