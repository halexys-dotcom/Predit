package pt.haconnect.predit.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate

fun diasConsumidos(
    inicio: Long,
    fim: Long,
    feriados: Set<Long>
): Int {
    var count = 0
    var curr = LocalDate.ofEpochDay(inicio)
    val fimDate = LocalDate.ofEpochDay(fim)
    while (!curr.isAfter(fimDate)) {
        val dow = curr.dayOfWeek
        val ehDiaUtil = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
        if (ehDiaUtil && curr.toEpochDay() !in feriados) {
            count++
        }
        curr = curr.plusDays(1)
    }
    return count
}
