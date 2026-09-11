package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.TipoTurno

/**
 * Gera uma representação resumida em texto do padrão de um ciclo de rotação.
 * Exemplo: 4x Tarde 8h + 2x Folga + 4x Tarde 8h + 1x Folga -> "4T08H · 2F · 4T08H · 2F · 4T08H · F"
 */
fun gerarResumoPadrao(slots: List<Long>, mapaTipos: Map<Long, TipoTurno>): String {
    if (slots.isEmpty()) return "Ciclo vazio"

    val blocos = mutableListOf<Pair<String, Int>>()
    var tipoAtualId: Long? = null
    var contagemAtual = 0

    for (tipoId in slots) {
        if (tipoId == tipoAtualId) {
            contagemAtual++
        } else {
            if (tipoAtualId != null) {
                val abreviatura = mapaTipos[tipoAtualId]?.abreviatura ?: "?"
                blocos.add(abreviatura to contagemAtual)
            }
            tipoAtualId = tipoId
            contagemAtual = 1
        }
    }

    if (tipoAtualId != null) {
        val abreviatura = mapaTipos[tipoAtualId]?.abreviatura ?: "?"
        blocos.add(abreviatura to contagemAtual)
    }

    return blocos.joinToString(" · ") { (abrev, count) ->
        if (count > 1) "${count}${abrev}" else abrev
    }
}
