package pt.haconnect.predit.domain.calc

/**
 * Um escalão das tabelas de retenção na fonte, já em termos de domínio.
 *
 * Existe para domain/calc deixar de depender de Room: o repositório faz a ponte
 * TabelaIRSEntity → EscalaoIRS.
 *
 * Unidades: 1/10000 € (ver domain/model/Dinheiro.kt).
 */
data class EscalaoIRS(
    val ano: Int,
    val regiao: RegiaoIRS,
    val categoria: String,              // TRABALHO | PENSOES
    val tabelaNumero: Int,
    val ordemEscalao: Int,
    val limiteAte: Long,                // 1/10000 €
    val taxaBasisPoints: Int,
    val parcelaAbater: Long,
    val parcelaAdicionalDep: Long,
    val formulaComposta: Boolean,
    /** k × 10000 da fórmula composta (2,6 → 26 000); 0 nos escalões de parcela fixa. */
    val coeficienteK: Long = 0L,
    /** X em 1/10000 € da fórmula composta (1 273,85 € → 12_738_500); 0 fora dela. */
    val limiteX: Long = 0L
)

/**
 * Regiões fiscais. É a ÚNICA enum de região do projeto — o contrato usa esta
 * (domain/model/Contrato.kt) e o texto guardado na BD coincide com o nome da constante.
 */
enum class RegiaoIRS { CONTINENTE, ACORES, MADEIRA }
