package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReciboMesDao {

    @Query("SELECT * FROM recibo_mes ORDER BY anoMes DESC")
    fun observarTodos(): Flow<List<ReciboMesEntity>>

    @Query("SELECT * FROM recibo_mes WHERE anoMes = :anoMes")
    fun observarPorMes(anoMes: String): Flow<ReciboMesEntity?>

    @Query("SELECT * FROM recibo_mes WHERE anoMes = :anoMes")
    suspend fun obterPorMes(anoMes: String): ReciboMesEntity?

    @Upsert
    suspend fun upsert(recibo: ReciboMesEntity)

    /** Apaga o mês; as linhas caem em cascata (FK ON DELETE CASCADE). */
    @Query("DELETE FROM recibo_mes WHERE anoMes = :anoMes")
    suspend fun apagar(anoMes: String)
}
