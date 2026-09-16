package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TabelaIRSDao {

    @Query("SELECT COUNT(*) FROM tabela_irs WHERE ano = :ano")
    suspend fun contarPorAno(ano: Int): Int

    @Query("SELECT * FROM tabela_irs WHERE ano = :ano ORDER BY regiao, categoria, tabelaNumero, ordemEscalao")
    suspend fun listarPorAno(ano: Int): List<TabelaIRSEntity>

    @Query("SELECT * FROM tabela_irs WHERE ano = :ano ORDER BY regiao, categoria, tabelaNumero, ordemEscalao")
    fun observarPorAno(ano: Int): Flow<List<TabelaIRSEntity>>

    @Query("SELECT COUNT(*) FROM tabela_irs WHERE regiao = :regiao AND ano = :ano")
    suspend fun contarPorRegiao(regiao: String, ano: Int): Int

    @Insert
    suspend fun inserir(tabela: TabelaIRSEntity): Long

    /** Inserção em bloco: o Room faz tudo numa só transacção (rápido e atómico). */
    @Insert
    suspend fun inserirTodas(tabelas: List<TabelaIRSEntity>)
}
