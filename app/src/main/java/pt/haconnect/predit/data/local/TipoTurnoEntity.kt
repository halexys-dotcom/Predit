package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import pt.haconnect.predit.domain.model.CategoriaTurno

@Entity(tableName = "tipo_turno")
data class TipoTurnoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val abreviatura: String,
    val cor: Long,                  // ARGB
    val emoji: String?,
    val inicioMin: Int,             // minutos desde a meia-noite
    val fimMin: Int,
    val pausaMin: Int,
    val categoria: CategoriaTurno,
    val ativo: Boolean = true
)
