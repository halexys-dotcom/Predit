package pt.haconnect.predit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MunicipioDao {

    @Query("SELECT * FROM municipio ORDER BY regiao, distrito, nome")
    fun observarTodos(): Flow<List<MunicipioEntity>>

    @Query("SELECT * FROM municipio WHERE id = :id")
    suspend fun obterPorId(id: Int): MunicipioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(municipios: List<MunicipioEntity>)

    @Query("SELECT COUNT(*) FROM municipio")
    suspend fun contar(): Int
}
