package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReciboLinhaDao {

    @Query("SELECT * FROM recibo_linha WHERE reciboMesId = :anoMes ORDER BY ordem")
    fun observarPorMes(anoMes: String): Flow<List<ReciboLinhaEntity>>

    @Query("SELECT * FROM recibo_linha WHERE reciboMesId = :anoMes ORDER BY ordem")
    suspend fun obterPorMes(anoMes: String): List<ReciboLinhaEntity>

    /**
     * Linhas de todos os meses de um ano ("2026" apanha 2026-01 a 2026-12).
     * O simulador de IRS anual soma os D02 dos meses do ano; para isso precisa do ano inteiro,
     * não de um mês. `||` é a concatenação do SQLite e o mês tem sempre o formato aaaa-mm.
     */
    @Query("SELECT * FROM recibo_linha WHERE reciboMesId LIKE :ano || '-%' ORDER BY reciboMesId, ordem")
    fun observarDoAno(ano: String): Flow<List<ReciboLinhaEntity>>

    /** Upsert pela chave composta (reciboMesId, rubricaId). */
    @Upsert
    suspend fun upsert(linha: ReciboLinhaEntity)

    @Upsert
    suspend fun upsertTodas(linhas: List<ReciboLinhaEntity>)

    @Query("DELETE FROM recibo_linha WHERE reciboMesId = :anoMes")
    suspend fun apagarPorMes(anoMes: String)
}
