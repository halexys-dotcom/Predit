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
    @Query("SELECT * FROM rotacao ORDER BY nome")
    fun observarTodasComSlots(): Flow<List<RotacaoComSlots>>

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
    @Query("SELECT * FROM aplicacao_rotacao ORDER BY validoDe ASC")
    fun observarTodasAplicacoes(): Flow<List<AplicacaoRotacaoEntity>>

    @Query("SELECT * FROM aplicacao_rotacao ORDER BY validoDe ASC")
    suspend fun obterTodasAplicacoes(): List<AplicacaoRotacaoEntity>

    @Query("SELECT * FROM aplicacao_rotacao WHERE validoAte IS NULL OR validoAte >= :epochDay ORDER BY validoDe DESC LIMIT 1")
    fun observarAplicacaoVigente(epochDay: Long): Flow<AplicacaoRotacaoEntity?>

    @Query("SELECT * FROM aplicacao_rotacao WHERE validoAte IS NULL ORDER BY validoDe DESC LIMIT 1")
    suspend fun obterAplicacaoAtual(): AplicacaoRotacaoEntity?

    @Query("SELECT * FROM aplicacao_rotacao WHERE validoAte IS NULL")
    suspend fun obterTodasAbertas(): List<AplicacaoRotacaoEntity>

    @Insert
    suspend fun inserirAplicacao(aplicacao: AplicacaoRotacaoEntity): Long

    @Update
    suspend fun atualizarAplicacao(aplicacao: AplicacaoRotacaoEntity)

    /**
     * 12e A: aplica uma rotação à escala.
     *  - Recusa datas anteriores à aplicação aberta mais recente: sem isto, uma aplicação antiga
     *    ficava aberta para sempre e a escolha do tipo de cada dia passava a depender da ordem
     *    em que a query devolvia as linhas (empate de validoDe).
     *  - Encerra TODAS as aplicações abertas que começam até ao novo validoDe; antes encerrava
     *    só a "atual", o que deixava órfãs quando havia mais do que uma aberta.
     */
    @Transaction
    suspend fun aplicarNovaRotacao(rotacaoId: Long, dataAncora: Long, validoDe: Long): Long {
        val abertas = obterTodasAbertas()
        val maisRecente = abertas.maxByOrNull { it.validoDe }

        if (maisRecente != null && maisRecente.validoDe > validoDe) {
            throw IllegalStateException(
                "Já existe uma rotação em vigor a partir de uma data posterior. " +
                    "Escolhe uma data igual ou posterior."
            )
        }

        abertas.filter { it.validoDe <= validoDe }.forEach { aberta ->
            atualizarAplicacao(aberta.copy(validoAte = validoDe - 1))
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
