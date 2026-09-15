package pt.haconnect.predit.domain.model

data class PlanejamentoMes(
    val anoMes: String,           // "2026-09"
    val totalMinutos: Int,
    val numTurnos: Int = 0,       // NOVO
    val numFolgas: Int = 0,       // NOVO
    val contratoMinutos: Int?,
    val dataImportacao: Long
)
