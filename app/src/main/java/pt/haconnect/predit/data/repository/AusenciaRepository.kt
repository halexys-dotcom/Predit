package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.AusenciaDao
import pt.haconnect.predit.data.local.AusenciaEntity
import pt.haconnect.predit.domain.model.Ausencia

class AusenciaRepository(private val dao: AusenciaDao) {

    fun observarTodas(): Flow<List<Ausencia>> {
        return dao.observarTodas().map { lista -> lista.map { it.paraModelo() } }
    }

    fun observarNoIntervalo(deEpochDay: Long, ateEpochDay: Long): Flow<List<Ausencia>> {
        return dao.observarNoIntervalo(deEpochDay, ateEpochDay).map { lista -> lista.map { it.paraModelo() } }
    }

    suspend fun obterNoIntervalo(deEpochDay: Long, ateEpochDay: Long): List<Ausencia> {
        return dao.obterNoIntervalo(deEpochDay, ateEpochDay).map { it.paraModelo() }
    }

    suspend fun salvar(ausencia: Ausencia): Long {
        val entidade = ausencia.paraEntidade()
        return if (ausencia.id == 0L) {
            dao.inserir(entidade)
        } else {
            dao.atualizar(entidade)
            ausencia.id
        }
    }

    suspend fun apagar(id: Long) {
        dao.apagar(id)
    }

    private fun AusenciaEntity.paraModelo() = Ausencia(
        id = id,
        tipoTurnoId = tipoTurnoId,
        dataInicio = dataInicio,
        dataFim = dataFim,
        nota = nota
    )

    private fun Ausencia.paraEntidade() = AusenciaEntity(
        id = id,
        tipoTurnoId = tipoTurnoId,
        dataInicio = dataInicio,
        dataFim = dataFim,
        nota = nota
    )
}
