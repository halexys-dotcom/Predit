package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReciboLinhaDao {

    @Query("SELECT * FROM recibo_linha WHERE reciboMesId = :anoMes ORDER BY ordem")
    fun observarPorMes(anoMes: String): Flow<List<ReciboLinhaEntity>>

    @Query("SELECT * FROM recibo_linha WHERE reciboMesId = :anoMes ORDER BY ordem")
    suspend fun obterPorMes(anoMes: String): List<ReciboLinhaEntity>

    /** Upsert pela chave composta (reciboMesId, rubricaId). */
    @Upsert
    suspend fun upsert(linha: ReciboLinhaEntity)

    @Upsert
    suspend fun upsertTodas(linhas: List<ReciboLinhaEntity>)

    @Query("DELETE FROM recibo_linha WHERE reciboMesId = :anoMes")
    suspend fun apagarPorMes(anoMes: String)
}
