package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rotacao")
data class RotacaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val comprimentoCiclo: Int
)
