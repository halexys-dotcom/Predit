package pt.haconnect.predit.domain.calc

import java.time.LocalDate

/**
 * Retorna o intervalo em minutos a partir da meia-noite para a janela noturna com base na data de admissão.
 * Se admissão < 15/07/2004: 20:00 (1200 min) às 07:00 (420 min).
 * Se admissão >= 15/07/2004: 21:00 (1260 min) às 06:00 (360 min).
 */
fun janelaNoturna(dataAdmissao: Long): Pair<Int, Int> =
    if (dataAdmissao < LocalDate.of(2004, 7, 15).toEpochDay())
        20 * 60 to 7 * 60
    else
        21 * 60 to 6 * 60
