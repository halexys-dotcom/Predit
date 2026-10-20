package pt.haconnect.predit.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.TabelaIRSDao
import pt.haconnect.predit.data.local.TabelaIRSEntity
import pt.haconnect.predit.domain.calc.EscalaoIRS
import pt.haconnect.predit.domain.calc.RegiaoIRS

/**
 * Escalões de IRS para um ano, e o ano de onde vieram de facto. [anoDasTabelas] difere do ano
 * pedido quando a app ainda não traz as tabelas desse ano (A1); é `null` sem tabelas nenhumas.
 */
data class EscaloesIRSDoAno(
    val escaloes: List<EscalaoIRS>,
    val anoDasTabelas: Int?
)

/**
 * Ponte entre a tabela_irs (Room) e o domínio: o motor do IRS só conhece [EscalaoIRS].
 */
class TabelaIRSRepository(private val dao: TabelaIRSDao) {

    fun observarPorAno(ano: Int): Flow<List<EscalaoIRS>> =
        dao.observarPorAno(ano).map { lista -> lista.map { it.paraEscalao() } }

    suspend fun listarPorAno(ano: Int): List<EscalaoIRS> =
        dao.listarPorAno(ano).map { it.paraEscalao() }

    /**
     * A1: os escalões do ano e, se a app não os tiver, os do ano carregado mais próximo — o
     * último antes dele ou, se não houver, o primeiro depois. Sem isto, janeiro de 2027 (ou um
     * mês de 2025) deixava o motor sem escalões e o ecrã preso em "a carregar".
     *
     * Os escalões de recurso saem com o [EscalaoIRS.ano] trocado pelo pedido, porque o
     * [pt.haconnect.predit.domain.calc.calcularIRS] filtra pelo ano do mês estimado. Quem chama
     * sabe que é recurso pelo [EscaloesIRSDoAno.anoDasTabelas] e deve avisar o utilizador.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarComRecurso(ano: Int): Flow<EscaloesIRSDoAno> =
        dao.observarAnos().flatMapLatest { anos ->
            val origem = anoDeRecurso(ano, anos)
            if (origem == null) {
                flowOf(EscaloesIRSDoAno(emptyList(), null))
            } else {
                dao.observarPorAno(origem).map { lista ->
                    EscaloesIRSDoAno(lista.map { it.paraEscalao().copy(ano = ano) }, origem)
                }
            }
        }
}

/**
 * O ano cujas tabelas servem para [ano]: o próprio, se existir; senão o último anterior; senão o
 * primeiro posterior. `null` sem tabelas nenhumas. Função livre para os testes a exercitarem sem DAO.
 */
fun anoDeRecurso(ano: Int, anosDisponiveis: List<Int>): Int? = when {
    ano in anosDisponiveis -> ano
    anosDisponiveis.any { it < ano } -> anosDisponiveis.filter { it < ano }.max()
    else -> anosDisponiveis.minOrNull()
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
