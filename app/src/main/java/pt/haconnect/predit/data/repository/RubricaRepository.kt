package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.RubricaDao
import pt.haconnect.predit.data.local.RubricaEntity
import pt.haconnect.predit.domain.model.Rubrica

/**
 * Ponte entre o catálogo de rubricas (Room) e o domínio.
 *
 * O ecrã de conferência precisa do catálogo com `ordem` e `ativaConferencia`, coisas que
 * a [pt.haconnect.predit.domain.calc.RubricaEstimada] não traz — daí esta listagem.
 */
class RubricaRepository(private val dao: RubricaDao) {

    fun observarTodas(): Flow<List<Rubrica>> =
        dao.observarTodas().map { lista -> lista.map { it.paraModelo() } }

    private fun RubricaEntity.paraModelo(): Rubrica = Rubrica(
        id = id,
        codigo = codigo,
        nome = nome,
        incideSS = incideSS,
        incideIRS = incideIRS,
        incideSindicato = incideSindicato,
        tipoCalculo = tipoCalculo,
        ativaConferencia = ativaConferencia,
        ordem = ordem
    )
}
