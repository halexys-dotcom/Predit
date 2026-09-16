package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.dividirArredondando

/**
 * Motor de cálculo do CCT. Funções puras — sem Android, sem Room.
 * Todos os valores estão em unidades de 1/10000 € (ver domain/model/Dinheiro.kt).
 *
 * Regra: multiplicar antes de dividir e arredondar só no fim, com
 * [dividirArredondando]. 65 653 × 35 ÷ 16 = 143 615,9375 → 143 616 (14,36 €),
 * e nunca 14,37 €.
 */
data class ContextoCalculo(
    val vencimentoBaseMil: Int,
    val horarioSemanalH: Int,
    val janelaNoturna: Pair<Int, Int>,   // de janelaNoturna() em Contrato.kt
    val diasUteisMes: Int,               // de DiaReal / calendário
    val numDependentes: Int              // do contrato
)

/**
 * Valor/hora em unidades de 1/10000 €:
 *   (vencimentoBaseMil × 12) ÷ (52 × horarioSemanalH)
 *
 * 2026, 40h: (11 379 800 × 12) ÷ 2080 = 65 652,6923… → 65 653 (6,5653 €/h)
 * 2025, 40h: (10 760 000 × 12) ÷ 2080 = 62 076,9230… → 62 077
 */
fun valorHoraMil(ctx: ContextoCalculo): Int {
    require(ctx.horarioSemanalH > 0) { "horarioSemanalH tem de ser positivo" }
    val numerador = ctx.vencimentoBaseMil.toLong() * 12L
    val denominador = 52L * ctx.horarioSemanalH
    return dividirArredondando(numerador, denominador).toInt()
}

/** Valor/dia de 8h em unidades de 1/10000 €: 65 653 × 8 = 525 224 (52,52 €). */
fun valorDiaMil(ctx: ContextoCalculo): Int = valorHoraMil(ctx) * 8

/**
 * Multiplicadores da CCT (handover, secção 4), em fracções exatas — nunca Double:
 * 1,25 = 5/4 · 1,5 = 3/2 · 2,1875 = 35/16 · 3 = 3/1 · 4,375 = 35/8 · 4,5 = 9/2 · 5,25 = 21/4.
 */
enum class TipoHora(val multiplicadorNumerador: Int, val multiplicadorDenominador: Int) {
    NORMAL(1, 1),                    // 1.0
    NOTURNA(5, 4),                   // 1.25
    SUP_DIURNO_NORMAL(3, 2),         // 1.5
    SUP_NOTURNO_NORMAL(35, 16),      // 2.1875
    SUP_DIURNO_FERIADO(3, 1),        // 3.0
    SUP_NOTURNO_FERIADO(35, 8),      // 4.375
    SUP_DIURNO_DESCANSO(9, 2),       // 4.5
    SUP_NOTURNO_DESCANSO(21, 4)      // 5.25
}

/**
 * Valor de uma hora do tipo indicado, em unidades de 1/10000 €.
 *
 * Exemplo do handover (sup. noturno em dia normal, 2026):
 *   65 653 × 35 ÷ 16 = 2 297 855 ÷ 16 = 143 615,9375 → 143 616 → 14,36 €
 */
fun valorHoraTipo(ctx: ContextoCalculo, tipo: TipoHora): Int {
    val numerador = valorHoraMil(ctx).toLong() * tipo.multiplicadorNumerador
    return dividirArredondando(numerador, tipo.multiplicadorDenominador.toLong()).toInt()
}
