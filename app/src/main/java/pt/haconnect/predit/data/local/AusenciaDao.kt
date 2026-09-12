package pt.haconnect.predit.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AusenciaDao {

    @Query("SELECT * FROM ausencia ORDER BY dataInicio ASC")
    fun observarTodas(): Flow<List<AusenciaEntity>>

    @Query("SELECT * FROM ausencia WHERE dataFim >= :deEpochDay AND dataInicio <= :ateEpochDay ORDER BY dataInicio ASC")
    fun observarNoIntervalo(deEpochDay: Long, ateEpochDay: Long): Flow<List<AusenciaEntity>>

    @Query("SELECT * FROM ausencia WHERE dataFim >= :deEpochDay AND dataInicio <= :ateEpochDay ORDER BY dataInicio ASC")
    suspend fun obterNoIntervalo(deEpochDay: Long, ateEpochDay: Long): List<AusenciaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(ausencia: AusenciaEntity): Long

    @Update
    suspend fun atualizar(ausencia: AusenciaEntity)

    @Query("DELETE FROM ausencia WHERE id = :id")
    suspend fun apagar(id: Long)
}
