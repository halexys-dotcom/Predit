package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Uma linha do recibo: uma rubrica do catálogo, com o valor estimado pela app e o
 * valor que o recibo traz.
 *
 * A chave é composta (reciboMesId, rubricaId) — garante uma linha por rubrica por mês,
 * sem id auto-gerado. Reintroduzir o recibo do mesmo mês substitui as linhas.
 *
 * Foreign keys: o mês apaga as linhas em cascata; a rubrica não se pode apagar
 * enquanto estiver usada numa linha (RESTRICT).
 *
 * Unidades: 1/10000 €.
 */
@Entity(
    tableName = "recibo_linha",
    primaryKeys = ["reciboMesId", "rubricaId"],
    foreignKeys = [
        ForeignKey(
            entity = ReciboMesEntity::class,
            parentColumns = ["anoMes"],
            childColumns = ["reciboMesId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RubricaEntity::class,
            parentColumns = ["id"],
            childColumns = ["rubricaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("rubricaId")]
)
data class ReciboLinhaEntity(
    val reciboMesId: String,
    val rubricaId: Long,
    val valorEstimado: Long,             // 1/10000 €
    val valorReal: Long,                 // 1/10000 €
    val natureza: String,                // NaturezaRubrica: "ABONO" | "DESCONTO"
    val ordem: Int
)
