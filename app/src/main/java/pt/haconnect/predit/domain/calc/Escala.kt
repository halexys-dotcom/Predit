package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import java.time.DayOfWeek
import java.time.LocalDate

data class AplicacaoVigente(
    val validoDe: Long,
    val validoAte: Long?,
    val dataAncora: Long,
    val slots: List<Long>        // tipoTurnoId por posição do ciclo
)

data class DiaProjetado(
    val epochDay: Long,
    val tipoTurnoId: Long?       // null = sem rotação em vigor
)

data class DiaComEstado(
    val epochDay: Long,
    val tipoTurnoProjetadoId: Long?,
    val ausenciaBruta: Ausencia? = null,
    val ausenciaEfetiva: Ausencia? = null,
    val tipoTurnoEfetivoOverrideId: Long? = null,
    val diaReal: DiaReal? = null
) {
    val tipoTurnoEfetivoId: Long?
        get() = tipoTurnoEfetivoOverrideId ?: ausenciaEfetiva?.tipoTurnoId ?: tipoTurnoProjetadoId
    val tipoTurnoChipId: Long?
        get() = diaReal?.tipoTurnoId ?: tipoTurnoEfetivoId
}

fun aplicacaoPara(
    epochDay: Long,
    aplicacoes: List<AplicacaoVigente>
): AplicacaoVigente? =
    aplicacoes
        .filter { epochDay >= it.validoDe && (it.validoAte == null || epochDay <= it.validoAte) }
        .maxByOrNull { it.validoDe }

fun projetarDia(epochDay: Long, aplicacoes: List<AplicacaoVigente>): DiaProjetado {
    val ap = aplicacaoPara(epochDay, aplicacoes)
        ?: return DiaProjetado(epochDay, null)
    val pos = posicaoNoCiclo(epochDay, ap.dataAncora, ap.slots.size)
    return DiaProjetado(epochDay, ap.slots[pos])
}

fun projetarIntervalo(
    deEpochDay: Long,
    ateEpochDay: Long,
    aplicacoes: List<AplicacaoVigente>
): List<DiaProjetado> =
    (deEpochDay..ateEpochDay).map { projetarDia(it, aplicacoes) }

fun aplicarAusencias(
    dias: List<DiaProjetado>,
    ausencias: List<Ausencia>,
    categoriasPorTipo: Map<Long, CategoriaTurno>,
    diasReais: List<DiaReal> = emptyList()
): List<DiaComEstado> {
    val folgaTipoId = categoriasPorTipo.entries.firstOrNull { it.value == CategoriaTurno.FOLGA }?.key

    return dias.map { dia ->
        val ausenciaNoDia = ausencias.firstOrNull { ap ->
            dia.epochDay >= ap.dataInicio && dia.epochDay <= ap.dataFim
        }
        val realNoDia = diasReais.firstOrNull { it.data == dia.epochDay }
        val categoriaProjetada = dia.tipoTurnoId?.let { categoriasPorTipo[it] }
        val categoriaAusencia = ausenciaNoDia?.tipoTurnoId?.let { categoriasPorTipo[it] }

        if (ausenciaNoDia != null) {
            if (categoriaAusencia == CategoriaTurno.FERIAS) {
                val localDate = LocalDate.ofEpochDay(dia.epochDay)
                val ehFimDeSemana = localDate.dayOfWeek == DayOfWeek.SATURDAY ||
                        localDate.dayOfWeek == DayOfWeek.SUNDAY

                if (categoriaProjetada == CategoriaTurno.FERIADO) {
                    DiaComEstado(
                        epochDay = dia.epochDay,
                        tipoTurnoProjetadoId = dia.tipoTurnoId,
                        ausenciaBruta = ausenciaNoDia,
                        ausenciaEfetiva = null,
                        diaReal = realNoDia
                    )
                } else if (ehFimDeSemana) {
                    DiaComEstado(
                        epochDay = dia.epochDay,
                        tipoTurnoProjetadoId = dia.tipoTurnoId,
                        ausenciaBruta = ausenciaNoDia,
                        ausenciaEfetiva = null,
                        tipoTurnoEfetivoOverrideId = folgaTipoId ?: dia.tipoTurnoId,
                        diaReal = realNoDia
                    )
                } else {
                    DiaComEstado(
                        epochDay = dia.epochDay,
                        tipoTurnoProjetadoId = dia.tipoTurnoId,
                        ausenciaBruta = ausenciaNoDia,
                        ausenciaEfetiva = ausenciaNoDia,
                        diaReal = realNoDia
                    )
                }
            } else {
                val eSubstituivel = dia.tipoTurnoId == null ||
                        categoriaProjetada == CategoriaTurno.TRABALHO

                val ausenciaEfetiva = if (eSubstituivel) ausenciaNoDia else null

                DiaComEstado(
                    epochDay = dia.epochDay,
                    tipoTurnoProjetadoId = dia.tipoTurnoId,
                    ausenciaBruta = ausenciaNoDia,
                    ausenciaEfetiva = ausenciaEfetiva,
                    diaReal = realNoDia
                )
            }
        } else {
            DiaComEstado(
                epochDay = dia.epochDay,
                tipoTurnoProjetadoId = dia.tipoTurnoId,
                ausenciaBruta = null,
                ausenciaEfetiva = null,
                diaReal = realNoDia
            )
        }
    }
}
