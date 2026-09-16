package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RubricaDao {

    @Query("SELECT COUNT(*) FROM rubrica")
    suspend fun contar(): Int

    /** Contagem por código — a semente usa isto para inserir só o que falta. */
    @Query("SELECT COUNT(*) FROM rubrica WHERE codigo = :codigo")
    suspend fun contarPorCodigo(codigo: String): Int

    @Query("SELECT * FROM rubrica ORDER BY ordem ASC")
    fun observarTodas(): Flow<List<RubricaEntity>>

    @Query("SELECT * FROM rubrica WHERE codigo = :codigo LIMIT 1")
    suspend fun obterPorCodigo(codigo: String): RubricaEntity?

    @Insert
    suspend fun inserir(rubrica: RubricaEntity): Long

    /** Apagar por id — os testes do recibo provam aqui o FK RESTRICT de recibo_linha. */
    @Query("DELETE FROM rubrica WHERE id = :id")
    suspend fun apagar(id: Long)
}
