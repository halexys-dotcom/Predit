package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.ParametrosCCTDao
import pt.haconnect.predit.data.local.ParametrosCCTEntity
import pt.haconnect.predit.domain.model.ParametrosCCT

/**
 * Ponte entre os parâmetros do CCT (Room) e o domínio.
 *
 * O motor do recibo quer UM conjunto de parâmetros (o vigente no mês), não a lista —
 * daí a [vigentePara], para a escolha não andar espalhada pela UI.
 */
class ParametrosCCTRepository(private val dao: ParametrosCCTDao) {

    fun observarTodos(): Flow<List<ParametrosCCT>> =
        dao.observarTodos().map { lista -> lista.map { it.paraModelo() } }

    /** A vigência que se aplica a um dia: a mais recente com `validoDe <= epochDay`. */
    fun vigentePara(lista: List<ParametrosCCT>, epochDay: Long): ParametrosCCT? =
        lista.filter { it.validoDe <= epochDay }.maxByOrNull { it.validoDe }

    private fun ParametrosCCTEntity.paraModelo(): ParametrosCCT = ParametrosCCT(
        id = id,
        validoDe = validoDe,
        vencimentoBaseMil = vencimentoBaseMil,
        subAlimentacaoDiaMil = subAlimentacaoDiaMil,
        subTransporteMesMil = subTransporteMesMil,
        horarioSemanalReferencia = horarioSemanalReferencia
    )
}
