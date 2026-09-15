package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.PlanejamentoMesDao
import pt.haconnect.predit.data.local.paraEntidade
import pt.haconnect.predit.data.local.paraModelo
import pt.haconnect.predit.domain.model.PlanejamentoMes

class PlanejamentoMesRepository(
    private val planejamentoMesDao: PlanejamentoMesDao
) {
    fun observarPorMes(anoMes: String): Flow<PlanejamentoMes?> {
        return planejamentoMesDao.observarPorMes(anoMes).map { it?.paraModelo() }
    }

    suspend fun obterPorMes(anoMes: String): PlanejamentoMes? {
        return planejamentoMesDao.obterPorMes(anoMes)?.paraModelo()
    }

    suspend fun upsert(p: PlanejamentoMes) {
        planejamentoMesDao.upsert(p.paraEntidade())
    }
}
