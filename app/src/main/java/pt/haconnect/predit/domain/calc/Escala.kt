package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.Ausencia

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
    val ausencia: Ausencia? = null
) {
    val tipoTurnoEfetivoId: Long? get() = ausencia?.tipoTurnoId ?: tipoTurnoProjetadoId
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
    ausencias: List<Ausencia>
): List<DiaComEstado> {
    return dias.map { dia ->
        val ausenciaNoDia = ausencias.firstOrNull { ap ->
            dia.epochDay >= ap.dataInicio && dia.epochDay <= ap.dataFim
        }
        DiaComEstado(
            epochDay = dia.epochDay,
            tipoTurnoProjetadoId = dia.tipoTurnoId,
            ausencia = ausenciaNoDia
        )
    }
}
