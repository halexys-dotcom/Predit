package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ausencia",
    foreignKeys = [
        ForeignKey(
            entity = TipoTurnoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tipoTurnoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("tipoTurnoId")]
)
data class AusenciaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipoTurnoId: Long,        // FER, BM, ou outro tipo
    val dataInicio: Long,         // epochDay
    val dataFim: Long,            // epochDay, inclusive
    val nota: String?
)
