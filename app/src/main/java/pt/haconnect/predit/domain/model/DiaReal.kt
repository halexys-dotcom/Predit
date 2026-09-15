package pt.haconnect.predit.domain.model

data class DiaReal(
    val id: Long = 0,
    val data: Long,                 // epochDay
    val tipoTurnoId: Long?,         // nullable
    val inicioMin: Int,
    val fimMin: Int,
    val pausaMin: Int,
    val nota: String? = null,
    val origem: String = "MANUAL",   // "PDF" | "MANUAL"
    val posto: String? = null        // NOVO
)
