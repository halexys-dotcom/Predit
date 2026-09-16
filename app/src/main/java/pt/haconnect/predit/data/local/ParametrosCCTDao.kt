package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParametrosCCTDao {

    @Query("SELECT COUNT(*) FROM parametros_cct")
    suspend fun contar(): Int

    @Query("SELECT * FROM parametros_cct ORDER BY validoDe ASC")
    fun observarTodos(): Flow<List<ParametrosCCTEntity>>

    @Query("SELECT * FROM parametros_cct WHERE validoDe <= :epochDay ORDER BY validoDe DESC LIMIT 1")
    suspend fun obterVigente(epochDay: Long): ParametrosCCTEntity?

    @Insert
    suspend fun inserir(parametros: ParametrosCCTEntity): Long
}
