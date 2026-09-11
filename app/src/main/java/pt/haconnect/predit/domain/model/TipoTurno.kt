package pt.haconnect.predit.domain.model

data class TipoTurno(
    val id: Long = 0,
    val nome: String,
    val abreviatura: String,
    val cor: Long,
    val emoji: String? = null,
    val inicioMin: Int = 0,
    val fimMin: Int = 0,
    val pausaMin: Int = 0,
    val categoria: CategoriaTurno,
    val ativo: Boolean = true
)
