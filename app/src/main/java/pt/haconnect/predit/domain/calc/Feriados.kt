package pt.haconnect.predit.domain.calc

import java.time.LocalDate

/**
 * Feriados nacionais portugueses, fixos e móveis, por ano.
 *
 * Fixos (10): 1 jan, 25 abr, 1 mai, 10 jun, 15 ago, 5 out, 1 nov, 1 dez, 8 dez, 25 dez
 * Móveis (4): Carnaval (Páscoa − 47), Sexta-feira Santa (Páscoa − 2),
 *             Domingo de Páscoa, Corpo de Deus (Páscoa + 60)
 *
 * Domínio puro: epochDay, sem Room nem Android. Os feriados municipais vêm da tabela
 * `municipio` (data/local) e entram por [feriadoMunicipal] — este ficheiro não os conhece.
 */
fun feriadosNacionais(ano: Int): Set<Long> {
    val fixos = listOf(
        LocalDate.of(ano, 1, 1),
        LocalDate.of(ano, 4, 25),
        LocalDate.of(ano, 5, 1),
        LocalDate.of(ano, 6, 10),
        LocalDate.of(ano, 8, 15),
        LocalDate.of(ano, 10, 5),
        LocalDate.of(ano, 11, 1),
        LocalDate.of(ano, 12, 1),
        LocalDate.of(ano, 12, 8),
        LocalDate.of(ano, 12, 25)
    )
    val pascoa = calcularPascoa(ano)
    val moveis = listOf(
        pascoa.minusDays(47),   // Carnaval
        pascoa.minusDays(2),    // Sexta-feira Santa
        pascoa,                 // Domingo de Páscoa
        pascoa.plusDays(60)     // Corpo de Deus
    )
    return (fixos + moveis).map { it.toEpochDay() }.toSet()
}

/**
 * Cálculo da Páscoa pelo algoritmo de Meeus/Jones/Butcher (Gregoriano).
 */
fun calcularPascoa(ano: Int): LocalDate {
    val a = ano % 19
    val b = ano / 100
    val c = ano % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val mes = (h + l - 7 * m + 114) / 31
    val dia = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(ano, mes, dia)
}

/**
 * Feriado municipal para um ano. Se o município tiver feriado móvel
 * (dia = 0), devolve null — fica para futura extensão.
 */
fun feriadoMunicipal(ano: Int, dia: Int, mes: Int): Long? {
    if (dia <= 0 || mes <= 0) return null
    return try {
        LocalDate.of(ano, mes, dia).toEpochDay()
    } catch (_: Exception) {
        null
    }
}
