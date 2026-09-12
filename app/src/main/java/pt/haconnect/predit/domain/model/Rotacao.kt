package pt.haconnect.predit.domain.model

data class Rotacao(
    val id: Long = 0,
    val nome: String,
    val comprimentoCiclo: Int
)

data class RotacaoSlot(
    val rotacaoId: Long,
    val posicao: Int,
    val tipoTurnoId: Long
)

data class RotacaoDetalhada(
    val rotacao: Rotacao,
    val slots: List<RotacaoSlot>
) {
    val comprimentoReal: Int get() = slots.size
}

data class AplicacaoRotacao(
    val id: Long = 0,
    val rotacaoId: Long,
    val dataAncora: Long,
    val validoDe: Long,
    val validoAte: Long? = null
)
