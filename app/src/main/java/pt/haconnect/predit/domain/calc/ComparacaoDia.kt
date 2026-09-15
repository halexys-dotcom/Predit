package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.TipoTurno

data class ComparacaoDia(
    val minutosProjetados: Int,   // 0 se não havia projeção
    val minutosReais: Int,        // 0 se não havia registo
    val temProjecao: Boolean,
    val temRegisto: Boolean
) {
    val diferenca: Int get() = minutosReais - minutosProjetados
}

fun compararProjetadoReal(
    projetado: TipoTurno?,
    real: DiaReal?
): ComparacaoDia {
    val temProjecao = projetado != null
    val temRegisto = real != null

    val minutosProjetados = if (projetado != null && projetado.categoria == CategoriaTurno.TRABALHO) {
        duracaoMinutos(projetado.inicioMin, projetado.fimMin, projetado.pausaMin)
    } else 0

    val minutosReais = if (real != null) {
        duracaoMinutos(real.inicioMin, real.fimMin, real.pausaMin)
    } else 0

    return ComparacaoDia(
        minutosProjetados = minutosProjetados,
        minutosReais = minutosReais,
        temProjecao = temProjecao,
        temRegisto = temRegisto
    )
}
