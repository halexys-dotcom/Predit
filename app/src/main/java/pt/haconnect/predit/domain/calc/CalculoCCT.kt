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
 * Valor/hora em unidades de 1/10000 EUR, arredondado ao centimo para bater com o recibo:
 *   (vencimentoBaseMil x 12) / (52 x horarioSemanalH), arredondado aos 100 unidades.
 *
 * 2026, 40h: 65 700 (6,57 EUR/h). O recibo da empresa imprime 6,57.
 * 2025, 40h: 62 100 (6,21 EUR/h).
 */
fun valorHoraMil(ctx: ContextoCalculo): Int {
    require(0 < ctx.horarioSemanalH) { "horarioSemanalH tem de ser positivo" }
    val numerador = ctx.vencimentoBaseMil.toLong() * 12L
    val denominador = 52L * ctx.horarioSemanalH
    return dividirArredondando(numerador, denominador * 100L).toInt() * 100
}

/** Valor/dia de 8h em unidades de 1/10000 EUR: 65 700 x 8 = 525 600 (52,56 EUR). */
fun valorDiaMil(ctx: ContextoCalculo): Int = valorHoraMil(ctx) * 8

/** Valor/minuto em unidades de 1/10000 EUR: valor/hora / 60 (65 700 / 60 = 1 095). */
fun valorMinutoMil(ctx: ContextoCalculo): Int =
    dividirArredondando(valorHoraMil(ctx).toLong(), 60L).toInt()

/**
 * Multiplicadores da CCT (handover, secção 4), em fracções exatas — nunca Double:
 * 1,25 = 5/4 · 1,5 = 3/2 · 2,1875 = 35/16 · 3 = 3/1 · 4,375 = 35/8 · 4,5 = 9/2 · 5,25 = 21/4.
 *
 * Duas leituras, e é preciso não as trocar:
 *  - as SUP_* pagam a hora por inteiro (a hora extra não está no vencimento mensal, que já
 *    paga as horas normais do contrato), por isso o multiplicador é o total da CCT;
 *  - [NOTURNA_ACRESCIMO] é só o acréscimo sobre a hora normal (0,25 = 1/4): a hora noturna
 *    é uma hora da jornada, essa já está paga no VENC, e a empresa só soma os 25% — é o que
 *    o recibo ICTS confirma ("H01 Horas nocturnas 25% — 1,64 €/h").
 */
enum class TipoHora(val multiplicadorNumerador: Int, val multiplicadorDenominador: Int) {
    NORMAL(1, 1),                    // 1.0
    NOTURNA_ACRESCIMO(1, 4),         // 0.25 (acréscimo: 1,64 €/h em 2026)
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
