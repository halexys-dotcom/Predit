package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import java.time.YearMonth

/**
 * O recibo do mês deve mostrar o subsídio de férias?
 *
 * Regra (v2.1.1): sim quando há uma ausência de férias que caia no próprio mês **ou no mês
 * seguinte**. Quem marca férias de 10 a 24 de agosto quer o subsídio à vista em julho (para o
 * prever/pagar) e continua a querê-lo em agosto (é o mês em que as goza); em setembro a linha
 * volta a desaparecer.
 *
 * A categoria vem do tipo de turno da ausência, não do dia: a [Ausencia] só guarda o
 * `tipoTurnoId` e é o tipo (Férias = [CategoriaTurno.FERIAS], Baixa médica =
 * [CategoriaTurno.BAIXA]) que diz de que ausência se trata. Sem catálogo de tipos não se mostra
 * nada — melhor não inventar um subsídio do que mostrá-lo por um id que não se sabe o que é.
 *
 * Datas inclusivas nas duas pontas e basta haver sobreposição: uma ausência que começa no último
 * dia do mês seguinte já conta.
 */
fun temFeriasNoMesOuSeguinte(
    ausencias: List<Ausencia>,
    categoriasPorTipo: Map<Long, CategoriaTurno>,
    anoMes: YearMonth
): Boolean {
    val primeiroDia = anoMes.atDay(1).toEpochDay()
    val ultimoDiaDoSeguinte = anoMes.plusMonths(1).atEndOfMonth().toEpochDay()
    return ausencias.any { ausencia ->
        categoriasPorTipo[ausencia.tipoTurnoId] == CategoriaTurno.FERIAS &&
            ausencia.dataInicio <= ultimoDiaDoSeguinte &&
            ausencia.dataFim >= primeiroDia
    }
}
