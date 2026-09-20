package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.ParametrosCCTDao
import pt.haconnect.predit.data.local.ParametrosCCTEntity
import pt.haconnect.predit.domain.model.CATEGORIA_CCT_PADRAO
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

    /**
     * A vigência da categoria do contrato para um dia. Sem valores para essa categoria, cai na
     * [CATEGORIA_CCT_PADRAO] — um contrato com categoria nova (ou sem tabela no ano do mês a
     * estimar) tem de continuar a dar recibo.
     *
     * A lista vem do fluxo que a UI já observa: a escolha é aqui e não na UI para não andar
     * repetida por cada ViewModel que precise dos parâmetros.
     */
    fun paraCategoria(
        lista: List<ParametrosCCT>,
        codigoCategoria: String,
        epochDay: Long
    ): ParametrosCCT? = parametrosDaCategoria(lista, codigoCategoria, epochDay)

    private fun ParametrosCCTEntity.paraModelo(): ParametrosCCT = ParametrosCCT(
        id = id,
        validoDe = validoDe,
        vencimentoBaseMil = vencimentoBaseMil,
        subAlimentacaoDiaMil = subAlimentacaoDiaMil,
        subTransporteMesMil = subTransporteMesMil,
        horarioSemanalReferencia = horarioSemanalReferencia,
        codigoCategoria = codigoCategoria,
        nivelCCT = nivelCCT,
        nomeCategoria = nomeCategoria,
        subsidioFuncaoMil = subsidioFuncaoMil
    )
}

/**
 * Escolha dos parâmetros do CCT para (categoria, dia): a linha mais recente da categoria com
 * `validoDe <= epochDay` e, se essa categoria não tiver nenhuma, a da [CATEGORIA_CCT_PADRAO].
 *
 * Função livre — e não um método do repositório — porque é lógica pura e é isto que os testes
 * exercitam sem precisar de DAO (ParametrosCCTLookupTest).
 */
fun parametrosDaCategoria(
    lista: List<ParametrosCCT>,
    codigoCategoria: String,
    epochDay: Long
): ParametrosCCT? {
    val daCategoria = lista
        .filter { it.codigoCategoria == codigoCategoria && it.validoDe <= epochDay }
        .maxByOrNull { it.validoDe }
    if (daCategoria != null) return daCategoria
    if (codigoCategoria == CATEGORIA_CCT_PADRAO) return null
    return lista
        .filter { it.codigoCategoria == CATEGORIA_CCT_PADRAO && it.validoDe <= epochDay }
        .maxByOrNull { it.validoDe }
}
