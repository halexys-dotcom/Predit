package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "aplicacao_rotacao",
    foreignKeys = [
        ForeignKey(
            entity = RotacaoEntity::class,
            parentColumns = ["id"],
            childColumns = ["rotacaoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("rotacaoId")]
)
data class AplicacaoRotacaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rotacaoId: Long,
    val dataAncora: Long,    // epochDay do dia que corresponde à posição 0
    val validoDe: Long,      // epochDay
    val validoAte: Long?     // null = ainda em vigor
)
