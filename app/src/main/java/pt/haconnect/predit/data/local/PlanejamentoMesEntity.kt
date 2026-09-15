package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import pt.haconnect.predit.domain.model.PlanejamentoMes

@Entity(tableName = "planejamento_mes")
data class PlanejamentoMesEntity(
    @PrimaryKey val anoMes: String,   // "2026-09"
    val totalMinutos: Int,
    val numTurnos: Int = 0,           // NOVO
    val numFolgas: Int = 0,           // NOVO
    val contratoMinutos: Int?,
    val dataImportacao: Long
)

fun PlanejamentoMesEntity.paraModelo(): PlanejamentoMes = PlanejamentoMes(
    anoMes = anoMes,
    totalMinutos = totalMinutos,
    numTurnos = numTurnos,
    numFolgas = numFolgas,
    contratoMinutos = contratoMinutos,
    dataImportacao = dataImportacao
)

fun PlanejamentoMes.paraEntidade(): PlanejamentoMesEntity = PlanejamentoMesEntity(
    anoMes = anoMes,
    totalMinutos = totalMinutos,
    numTurnos = numTurnos,
    numFolgas = numFolgas,
    contratoMinutos = contratoMinutos,
    dataImportacao = dataImportacao
)
