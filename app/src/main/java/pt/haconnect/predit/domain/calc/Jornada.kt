package pt.haconnect.predit.domain.calc

import java.time.LocalDate

/**
 * Jornada mensal do CCT (cláusula 79.ª): 173,33 h.
 * 173,33 × 60 = 10 399,8 → truncado: 10 399 min = 173:19.
 *
 * Nota de registo: a base legal de 40 h/semana (40 × 52 ÷ 12 = 173,33 h) daria
 * 173:20 (10 400 min). Fica 173:19 — diferença de 1 min por mês, 6 min por semestre.
 */
const val JORNADA_MENSAL_MINUTOS = 10_399          // 173:19
const val MESES_POR_CICLO = 6
const val JORNADA_CICLO_MINUTOS = 62_394           // 1039:54

data class MesCalculado(
    val anoMes: String,             // "2026-07"
    val realMinutos: Int,
    val jornadaMinutos: Int,        // sempre 10_399
    val deltaMinutos: Int,          // real − jornada
    val abateAoDeficeMinutos: Int,  // quanto do delta foi a cobrir défice
    val extraPagoMinutos: Int,      // o que sobra e é pago
    val saldoAcumuladoMinutos: Int  // saldo após este mês (≤ 0 durante o ciclo)
)

data class ResultadoCiclo(
    val meses: List<MesCalculado>,
    val realTotalMinutos: Int,
    val jornadaTotalMinutos: Int,       // 62_394
    val extrasPagosTotalMinutos: Int,
    val saldoFinalMinutos: Int          // ≤ 0 se ciclo fechou em défice
)

fun calcularCiclo(
    reaisPorMes: Map<String, Int>,   // "2026-07" → real
    mesesDoCiclo: List<String>,      // 6 elementos, ordenados
    saldoEntradaMinutos: Int = 0     // sempre 0 no regime definido
): ResultadoCiclo {
    var saldoAcumulado = saldoEntradaMinutos   // sempre 0
    var extrasPagosTotal = 0
    val meses = mutableListOf<MesCalculado>()

    for (mes in mesesDoCiclo) {
        val real = reaisPorMes[mes] ?: 0
        val delta = real - JORNADA_MENSAL_MINUTOS
        val novoSaldo = saldoAcumulado + delta

        val extraPago: Int
        val abate: Int

        if (novoSaldo > 0) {
            extraPago = novoSaldo
            abate = delta - extraPago
            saldoAcumulado = 0
        } else {
            extraPago = 0
            abate = delta
            saldoAcumulado = novoSaldo
        }

        extrasPagosTotal += extraPago
        meses.add(
            MesCalculado(
                anoMes = mes,
                realMinutos = real,
                jornadaMinutos = JORNADA_MENSAL_MINUTOS,
                deltaMinutos = delta,
                abateAoDeficeMinutos = abate,
                extraPagoMinutos = extraPago,
                saldoAcumuladoMinutos = saldoAcumulado
            )
        )
    }

    val realTotal = meses.sumOf { it.realMinutos }
    return ResultadoCiclo(
        meses = meses,
        realTotalMinutos = realTotal,
        jornadaTotalMinutos = JORNADA_CICLO_MINUTOS,
        extrasPagosTotalMinutos = extrasPagosTotal,
        saldoFinalMinutos = saldoAcumulado
    )
}

fun cicloDoAno(ano: Int, semestre: Int): Pair<LocalDate, LocalDate> {
    val inicio = if (semestre == 1) LocalDate.of(ano, 1, 1) else LocalDate.of(ano, 7, 1)
    val fim = if (semestre == 1) LocalDate.of(ano, 6, 30) else LocalDate.of(ano, 12, 31)
    return inicio to fim
}

fun mesesDoCiclo(ano: Int, semestre: Int): List<String> =
    if (semestre == 1) (1..6).map { "%04d-%02d".format(ano, it) }
    else (7..12).map { "%04d-%02d".format(ano, it) }

fun semestreDe(data: LocalDate): Pair<Int, Int> =
    data.year to if (data.monthValue <= 6) 1 else 2
