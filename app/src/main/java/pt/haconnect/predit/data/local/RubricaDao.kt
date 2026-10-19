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

    /**
     * Renomeia uma rubrica já semeada. O nome pode mudar entre versões (a "Desconto de atraso"
     * passou a "Ausências" na 2.1.0) e o seeder não volta a inserir a linha: sem isto, quem
     * actualiza ficava com o nome antigo. Idempotente — só escreve quando o nome difere.
     */
    @Query("UPDATE rubrica SET nome = :nome WHERE codigo = :codigo AND nome <> :nome")
    suspend fun renomear(codigo: String, nome: String)

    /** Apagar por id — os testes do recibo provam aqui o FK RESTRICT de recibo_linha. */
    @Query("DELETE FROM rubrica WHERE id = :id")
    suspend fun apagar(id: Long)
}
