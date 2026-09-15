package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CicloJornadaDao {
    @Query("SELECT * FROM ciclo_jornada ORDER BY inicio ASC")
    fun observarTodos(): Flow<List<CicloJornadaEntity>>

    @Query("SELECT * FROM ciclo_jornada WHERE id = :id LIMIT 1")
    suspend fun obterPorId(id: String): CicloJornadaEntity?

    @Upsert
    suspend fun upsert(c: CicloJornadaEntity)
}
