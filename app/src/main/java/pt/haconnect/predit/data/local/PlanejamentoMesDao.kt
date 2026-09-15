package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanejamentoMesDao {
    @Query("SELECT * FROM planejamento_mes WHERE anoMes = :anoMes")
    fun observarPorMes(anoMes: String): Flow<PlanejamentoMesEntity?>

    @Query("SELECT * FROM planejamento_mes WHERE anoMes = :anoMes")
    suspend fun obterPorMes(anoMes: String): PlanejamentoMesEntity?

    @Upsert
    suspend fun upsert(p: PlanejamentoMesEntity)
}
