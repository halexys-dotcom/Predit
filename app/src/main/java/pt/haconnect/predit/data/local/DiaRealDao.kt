package pt.haconnect.predit.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaRealDao {

    @Query("SELECT * FROM dia_real ORDER BY data ASC")
    fun observarTodos(): Flow<List<DiaRealEntity>>

    @Query("SELECT * FROM dia_real WHERE data >= :de AND data <= :ate ORDER BY data ASC")
    fun observarNoIntervalo(de: Long, ate: Long): Flow<List<DiaRealEntity>>

    @Query("SELECT * FROM dia_real WHERE data >= :de AND data <= :ate ORDER BY data ASC")
    suspend fun obterNoIntervalo(de: Long, ate: Long): List<DiaRealEntity>

    @Query("SELECT * FROM dia_real WHERE data = :epochDay LIMIT 1")
    suspend fun obterPorData(epochDay: Long): DiaRealEntity?

    @Upsert
    suspend fun upsert(dia: DiaRealEntity)

    @Query("DELETE FROM dia_real WHERE id = :id")
    suspend fun apagar(id: Long)
}
