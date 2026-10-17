package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MapeamentoRubricaDao {

    /** Upsert pela chave composta (empresa, codigoOriginal). */
    @Upsert
    suspend fun guardar(mapeamento: MapeamentoRubricaEntity)

    /** O destino de um código original numa empresa, ou null se ainda não houver mapeamento. */
    @Query(
        "SELECT codigoInterno FROM mapeamento_rubrica " +
            "WHERE empresa = :empresa AND codigoOriginal = :codigoOriginal"
    )
    suspend fun obterCodigoInterno(empresa: String, codigoOriginal: String): String?

    @Query("SELECT * FROM mapeamento_rubrica ORDER BY empresa, codigoOriginal")
    suspend fun obterTodos(): List<MapeamentoRubricaEntity>
}
