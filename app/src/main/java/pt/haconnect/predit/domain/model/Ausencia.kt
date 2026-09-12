package pt.haconnect.predit.domain.model

data class Ausencia(
    val id: Long = 0,
    val tipoTurnoId: Long,        // FER, BM, ou outro tipo
    val dataInicio: Long,         // epochDay
    val dataFim: Long,            // epochDay, inclusive
    val nota: String? = null
)
