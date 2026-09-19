package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TipoTurnoDao {

    @Query("SELECT * FROM tipo_turno WHERE ativo = 1 ORDER BY categoria, nome")
    fun observarAtivos(): Flow<List<TipoTurnoEntity>>

    @Query("SELECT * FROM tipo_turno ORDER BY ativo DESC, categoria, nome")
    fun observarTodos(): Flow<List<TipoTurnoEntity>>

    @Query("SELECT * FROM tipo_turno WHERE id = :id")
    suspend fun porId(id: Long): TipoTurnoEntity?

    @Insert
    suspend fun inserir(tipo: TipoTurnoEntity): Long

    @Update
    suspend fun atualizar(tipo: TipoTurnoEntity)

    @Query("UPDATE tipo_turno SET ativo = :ativo WHERE id = :id")
    suspend fun definirAtivo(id: Long, ativo: Boolean)

    /**
     * 12c C.4 — apaga definitivamente. Se o tipo estiver a ser usado em rotacao_slot, ausencia
     * ou dia_real (ON DELETE RESTRICT) o SQLite lança SQLiteConstraintException.
     */
    @Query("DELETE FROM tipo_turno WHERE id = :id")
    suspend fun apagar(id: Long)
}
