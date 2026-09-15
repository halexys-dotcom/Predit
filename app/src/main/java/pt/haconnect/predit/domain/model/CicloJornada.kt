package pt.haconnect.predit.domain.model

data class CicloJornada(
    val id: String,                  // "2026-S1" ou "2026-S2"
    val inicio: Long,                // epochDay
    val fim: Long,                   // epochDay
    val realTotalMinutos: Int,
    val extrasPagosMinutos: Int,
    val saldoFinalMinutos: Int,      // ≤ 0, o défice que foi limpo
    val dataFecho: Long
)
