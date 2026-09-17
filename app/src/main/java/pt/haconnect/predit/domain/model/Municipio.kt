package pt.haconnect.predit.domain.model

/**
 * Município do catálogo (Fase 10). É o que a UI precisa de mostrar (nome, distrito, região)
 * e o que o recibo precisa de saber: o feriado municipal em dia/mês.
 *
 * [verificado] = false são dados por confirmar — a UI mostra o aviso. O nome do feriado
 * desses leva "(por verificar)" no catálogo semeado (PreditApplication.garantirMunicipios).
 */
data class Municipio(
    val id: Int,
    val nome: String,
    val distrito: String,
    val regiao: String,          // CONTINENTE | ACORES | MADEIRA
    val feriadoDia: Int,         // 0 = feriado móvel/não definido
    val feriadoMes: Int,
    val feriadoNome: String,
    val verificado: Boolean
)
