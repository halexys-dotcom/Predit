package pt.haconnect.predit.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class RotacaoComSlots(
    @Embedded val rotacao: RotacaoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "rotacaoId"
    )
    val slots: List<RotacaoSlotEntity>
)

@Dao
interface RotacaoDao {

    @Query("SELECT * FROM rotacao ORDER BY nome")
    fun observarTodas(): Flow<List<RotacaoEntity>>

    @Transaction
    @Query("SELECT * FROM rotacao WHERE id = :id")
    fun observarPorId(id: Long): Flow<RotacaoComSlots?>

    @Transaction
    @Query("SELECT * FROM rotacao WHERE id = :id")
    suspend fun porId(id: Long): RotacaoComSlots?

    @Insert
    suspend fun inserirRotacao(rotacao: RotacaoEntity): Long

    @Update
    suspend fun atualizarRotacao(rotacao: RotacaoEntity)

    @Query("DELETE FROM rotacao WHERE id = :id")
    suspend fun apagarRotacao(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirSlots(slots: List<RotacaoSlotEntity>)

    @Query("DELETE FROM rotacao_slot WHERE rotacaoId = :rotacaoId")
    suspend fun apagarSlotsDaRotacao(rotacaoId: Long)

    @Transaction
    suspend fun salvarRotacaoComSlots(rotacao: RotacaoEntity, slots: List<RotacaoSlotEntity>): Long {
        val id = if (rotacao.id == 0L) {
            inserirRotacao(rotacao)
        } else {
            atualizarRotacao(rotacao)
            apagarSlotsDaRotacao(rotacao.id)
            rotacao.id
        }
        val slotsComId = slots.map { it.copy(rotacaoId = id) }
        inserirSlots(slotsComId)
        return id
    }

    // Aplicação da rotação
    @Query("SELECT * FROM aplicacao_rotacao WHERE validoAte IS NULL OR validoAte >= :epochDay ORDER BY validoDe DESC LIMIT 1")
    fun observarAplicacaoVigente(epochDay: Long): Flow<AplicacaoRotacaoEntity?>

    @Query("SELECT * FROM aplicacao_rotacao WHERE validoAte IS NULL ORDER BY validoDe DESC LIMIT 1")
    suspend fun obterAplicacaoAtual(): AplicacaoRotacaoEntity?

    @Insert
    suspend fun inserirAplicacao(aplicacao: AplicacaoRotacaoEntity): Long

    @Update
    suspend fun atualizarAplicacao(aplicacao: AplicacaoRotacaoEntity)

    @Transaction
    suspend fun aplicarNovaRotacao(rotacaoId: Long, dataAncora: Long, validoDe: Long): Long {
        val atual = obterAplicacaoAtual()
        if (atual != null && atual.validoDe < validoDe) {
            atualizarAplicacao(atual.copy(validoAte = validoDe - 1))
        }
        return inserirAplicacao(
            AplicacaoRotacaoEntity(
                rotacaoId = rotacaoId,
                dataAncora = dataAncora,
                validoDe = validoDe,
                validoAte = null
            )
        )
    }
}
