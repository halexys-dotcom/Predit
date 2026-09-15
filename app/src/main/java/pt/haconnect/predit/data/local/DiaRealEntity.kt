package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pt.haconnect.predit.domain.model.DiaReal

@Entity(
    tableName = "dia_real",
    foreignKeys = [
        ForeignKey(
            entity = TipoTurnoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tipoTurnoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["data"], unique = true),
        Index("tipoTurnoId")
    ]
)
data class DiaRealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,                 // epochDay, ÚNICA — um registo por dia
    val tipoTurnoId: Long?,         // nullable: registo pode não corresponder a tipo
    val inicioMin: Int,
    val fimMin: Int,
    val pausaMin: Int,
    val nota: String?,
    val origem: String,             // "PDF" | "MANUAL"
    val posto: String? = null       // NOVO
)

fun DiaRealEntity.paraModelo(): DiaReal = DiaReal(
    id = id,
    data = data,
    tipoTurnoId = tipoTurnoId,
    inicioMin = inicioMin,
    fimMin = fimMin,
    pausaMin = pausaMin,
    nota = nota,
    origem = origem,
    posto = posto
)

fun DiaReal.paraEntidade(): DiaRealEntity = DiaRealEntity(
    id = id,
    data = data,
    tipoTurnoId = tipoTurnoId,
    inicioMin = inicioMin,
    fimMin = fimMin,
    pausaMin = pausaMin,
    nota = nota,
    origem = origem,
    posto = posto
)
