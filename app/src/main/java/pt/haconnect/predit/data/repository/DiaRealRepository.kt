package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.DiaRealDao
import pt.haconnect.predit.data.local.paraEntidade
import pt.haconnect.predit.data.local.paraModelo
import pt.haconnect.predit.domain.model.DiaReal

class DiaRealRepository(private val dao: DiaRealDao) {

    fun observarTodos(): Flow<List<DiaReal>> =
        dao.observarTodos().map { list -> list.map { it.paraModelo() } }

    fun observarNoIntervalo(de: Long, ate: Long): Flow<List<DiaReal>> =
        dao.observarNoIntervalo(de, ate).map { list -> list.map { it.paraModelo() } }

    suspend fun obterNoIntervalo(de: Long, ate: Long): List<DiaReal> =
        dao.obterNoIntervalo(de, ate).map { it.paraModelo() }

    suspend fun obterPorData(epochDay: Long): DiaReal? =
        dao.obterPorData(epochDay)?.paraModelo()

    suspend fun guardar(dia: DiaReal) {
        dao.upsert(dia.paraEntidade())
    }

    suspend fun apagar(id: Long) {
        dao.apagar(id)
    }
}
