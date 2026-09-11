package pt.haconnect.predit.domain.calc

const val MINUTOS_POR_DIA = 1440

/**
 * Duração efetiva de um turno, em minutos, já descontada a pausa.
 * Turnos que atravessam a meia-noite são tratados por aritmética modular.
 */
fun duracaoMinutos(inicioMin: Int, fimMin: Int, pausaMin: Int): Int {
    val bruta = Math.floorMod(fimMin - inicioMin, MINUTOS_POR_DIA)
    return bruta - pausaMin
}

fun formatarHoraMin(minutos: Int): String {
    val h = minutos / 60
    val m = minutos % 60
    return "%02d:%02d".format(h, m)
}
