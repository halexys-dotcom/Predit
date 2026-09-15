package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.CicloJornadaDao
import pt.haconnect.predit.data.local.paraEntidade
import pt.haconnect.predit.data.local.paraModelo
import pt.haconnect.predit.domain.model.CicloJornada

class CicloJornadaRepository(
    private val cicloJornadaDao: CicloJornadaDao
) {
    fun observarTodos(): Flow<List<CicloJornada>> =
        cicloJornadaDao.observarTodos().map { list -> list.map { it.paraModelo() } }

    suspend fun obterPorId(id: String): CicloJornada? =
        cicloJornadaDao.obterPorId(id)?.paraModelo()

    suspend fun upsert(c: CicloJornada) {
        cicloJornadaDao.upsert(c.paraEntidade())
    }
}
