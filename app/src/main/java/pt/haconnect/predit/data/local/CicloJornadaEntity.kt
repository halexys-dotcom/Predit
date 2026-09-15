package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import pt.haconnect.predit.domain.model.CicloJornada

@Entity(tableName = "ciclo_jornada")
data class CicloJornadaEntity(
    @PrimaryKey val id: String,          // "2026-S1" ou "2026-S2"
    val inicio: Long,                    // epochDay
    val fim: Long,                       // epochDay
    val realTotalMinutos: Int,
    val extrasPagosMinutos: Int,
    val saldoFinalMinutos: Int,          // ≤ 0, o défice que foi limpo
    val dataFecho: Long
)

fun CicloJornadaEntity.paraModelo(): CicloJornada = CicloJornada(
    id = id,
    inicio = inicio,
    fim = fim,
    realTotalMinutos = realTotalMinutos,
    extrasPagosMinutos = extrasPagosMinutos,
    saldoFinalMinutos = saldoFinalMinutos,
    dataFecho = dataFecho
)

fun CicloJornada.paraEntidade(): CicloJornadaEntity = CicloJornadaEntity(
    id = id,
    inicio = inicio,
    fim = fim,
    realTotalMinutos = realTotalMinutos,
    extrasPagosMinutos = extrasPagosMinutos,
    saldoFinalMinutos = saldoFinalMinutos,
    dataFecho = dataFecho
)
