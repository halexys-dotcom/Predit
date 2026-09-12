package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContratoUtilizadorDao {

    @Query("SELECT * FROM contrato_utilizador WHERE id = 1 LIMIT 1")
    fun observar(): Flow<ContratoUtilizadorEntity?>

    @Query("SELECT * FROM contrato_utilizador WHERE id = 1 LIMIT 1")
    suspend fun obter(): ContratoUtilizadorEntity?

    @Query("SELECT COUNT(*) FROM contrato_utilizador")
    suspend fun contar(): Int

    @Upsert
    suspend fun guardar(contrato: ContratoUtilizadorEntity)
}
