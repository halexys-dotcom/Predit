package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.MunicipioDao
import pt.haconnect.predit.data.local.MunicipioEntity
import pt.haconnect.predit.domain.model.Municipio

/**
 * Ponte entre a tabela `municipio` (Room) e o domínio: [Municipio]. O catálogo é semeado em
 * PreditApplication, por isso este repositório só lê.
 */
class MunicipioRepository(private val dao: MunicipioDao) {

    fun observarTodos(): Flow<List<Municipio>> =
        dao.observarTodos().map { lista -> lista.map { it.paraModelo() } }

    suspend fun obterPorId(id: Int): Municipio? = dao.obterPorId(id)?.paraModelo()
}

private fun MunicipioEntity.paraModelo(): Municipio = Municipio(
    id = id,
    nome = nome,
    distrito = distrito,
    regiao = regiao,
    feriadoDia = feriadoDia,
    feriadoMes = feriadoMes,
    feriadoNome = feriadoNome,
    verificado = verificado
)
