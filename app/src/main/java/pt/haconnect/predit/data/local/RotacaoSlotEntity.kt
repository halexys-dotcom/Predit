package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "rotacao_slot",
    primaryKeys = ["rotacaoId", "posicao"],
    foreignKeys = [
        ForeignKey(
            entity = RotacaoEntity::class,
            parentColumns = ["id"],
            childColumns = ["rotacaoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TipoTurnoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tipoTurnoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("rotacaoId"), Index("tipoTurnoId")]
)
data class RotacaoSlotEntity(
    val rotacaoId: Long,
    val posicao: Int,        // 0 .. comprimentoCiclo-1
    val tipoTurnoId: Long
)
