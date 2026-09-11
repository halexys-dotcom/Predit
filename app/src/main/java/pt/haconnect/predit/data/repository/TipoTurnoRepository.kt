package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.TipoTurnoDao
import pt.haconnect.predit.data.local.TipoTurnoEntity
import pt.haconnect.predit.domain.model.TipoTurno

class TipoTurnoRepository(private val dao: TipoTurnoDao) {

    fun observarTodos(): Flow<List<TipoTurno>> {
        return dao.observarTodos().map { lista -> lista.map { it.paraModelo() } }
    }

    fun observarAtivos(): Flow<List<TipoTurno>> {
        return dao.observarAtivos().map { lista -> lista.map { it.paraModelo() } }
    }

    suspend fun porId(id: Long): TipoTurno? {
        return dao.porId(id)?.paraModelo()
    }

    suspend fun salvar(tipo: TipoTurno): Long {
        val entidade = tipo.paraEntidade()
        return if (tipo.id == 0L) {
            dao.inserir(entidade)
        } else {
            dao.atualizar(entidade)
            tipo.id
        }
    }

    suspend fun definirAtivo(id: Long, ativo: Boolean) {
        dao.definirAtivo(id, ativo)
    }

    private fun TipoTurnoEntity.paraModelo() = TipoTurno(
        id = id,
        nome = nome,
        abreviatura = abreviatura,
        cor = cor,
        emoji = emoji,
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = pausaMin,
        categoria = categoria,
        ativo = ativo
    )

    private fun TipoTurno.paraEntidade() = TipoTurnoEntity(
        id = id,
        nome = nome,
        abreviatura = abreviatura,
        cor = cor,
        emoji = emoji,
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = pausaMin,
        categoria = categoria,
        ativo = ativo
    )
}
