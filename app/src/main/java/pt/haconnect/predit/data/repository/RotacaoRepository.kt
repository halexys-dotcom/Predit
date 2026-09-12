package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.*
import pt.haconnect.predit.domain.model.*

class RotacaoRepository(private val dao: RotacaoDao) {

    fun observarTodas(): Flow<List<Rotacao>> {
        return dao.observarTodas().map { lista -> lista.map { it.paraModelo() } }
    }

    fun observarTodasDetalhadas(): Flow<List<RotacaoDetalhada>> {
        return dao.observarTodasComSlots().map { lista -> lista.map { it.paraModeloDetalhado() } }
    }

    fun observarPorId(id: Long): Flow<RotacaoDetalhada?> {
        return dao.observarPorId(id).map { it?.paraModeloDetalhado() }
    }

    suspend fun porId(id: Long): RotacaoDetalhada? {
        return dao.porId(id)?.paraModeloDetalhado()
    }

    suspend fun salvarRotacaoComSlots(rotacao: Rotacao, slots: List<RotacaoSlot>): Long {
        val entidadeRotacao = rotacao.paraEntidade()
        val entidadesSlots = slots.map { it.paraEntidade() }
        return dao.salvarRotacaoComSlots(entidadeRotacao, entidadesSlots)
    }

    suspend fun apagarRotacao(id: Long) {
        dao.apagarRotacao(id)
    }

    fun observarAplicacaoVigente(epochDay: Long): Flow<AplicacaoRotacao?> {
        return dao.observarAplicacaoVigente(epochDay).map { it?.paraModelo() }
    }

    suspend fun aplicarNovaRotacao(rotacaoId: Long, dataAncora: Long, validoDe: Long): Long {
        return dao.aplicarNovaRotacao(rotacaoId, dataAncora, validoDe)
    }

    fun observarAplicacoesVigentes(): Flow<List<pt.haconnect.predit.domain.calc.AplicacaoVigente>> {
        return dao.observarTodasAplicacoes().map { lista ->
            lista.mapNotNull { ap ->
                val detalhe = dao.porId(ap.rotacaoId)
                if (detalhe != null) {
                    val slotsOrdenados = detalhe.slots.sortedBy { it.posicao }.map { it.tipoTurnoId }
                    pt.haconnect.predit.domain.calc.AplicacaoVigente(
                        validoDe = ap.validoDe,
                        validoAte = ap.validoAte,
                        dataAncora = ap.dataAncora,
                        slots = slotsOrdenados
                    )
                } else null
            }
        }
    }

    suspend fun obterAplicacoesVigentes(): List<pt.haconnect.predit.domain.calc.AplicacaoVigente> {
        val lista = dao.obterTodasAplicacoes()
        return lista.mapNotNull { ap ->
            val detalhe = dao.porId(ap.rotacaoId)
            if (detalhe != null) {
                val slotsOrdenados = detalhe.slots.sortedBy { it.posicao }.map { it.tipoTurnoId }
                pt.haconnect.predit.domain.calc.AplicacaoVigente(
                    validoDe = ap.validoDe,
                    validoAte = ap.validoAte,
                    dataAncora = ap.dataAncora,
                    slots = slotsOrdenados
                )
            } else null
        }
    }

    private fun RotacaoEntity.paraModelo() = Rotacao(
        id = id,
        nome = nome,
        comprimentoCiclo = comprimentoCiclo
    )

    private fun Rotacao.paraEntidade() = RotacaoEntity(
        id = id,
        nome = nome,
        comprimentoCiclo = comprimentoCiclo
    )

    private fun RotacaoSlotEntity.paraModelo() = RotacaoSlot(
        rotacaoId = rotacaoId,
        posicao = posicao,
        tipoTurnoId = tipoTurnoId
    )

    private fun RotacaoSlot.paraEntidade() = RotacaoSlotEntity(
        rotacaoId = rotacaoId,
        posicao = posicao,
        tipoTurnoId = tipoTurnoId
    )

    private fun RotacaoComSlots.paraModeloDetalhado() = RotacaoDetalhada(
        rotacao = rotacao.paraModelo(),
        slots = slots.map { it.paraModelo() }
    )

    private fun AplicacaoRotacaoEntity.paraModelo() = AplicacaoRotacao(
        id = id,
        rotacaoId = rotacaoId,
        dataAncora = dataAncora,
        validoDe = validoDe,
        validoAte = validoAte
    )
}
