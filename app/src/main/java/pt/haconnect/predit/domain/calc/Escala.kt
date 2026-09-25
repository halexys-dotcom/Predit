package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

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

// ---------------------------------------------------------------------------------------------
// Fase 19 — modo de escala por PDF mensal
// ---------------------------------------------------------------------------------------------

/** O ciclo da rotação projeta os meses (comportamento de sempre). */
const val TIPO_ESCALA_ROTACAO = "ROTACAO"

/** Os chips do calendário vêm dos dia_real importados do PDF da empresa. */
const val TIPO_ESCALA_PDF_MENSAL = "PDF_MENSAL"

/** Valor de origem dos dias que vieram da importação do PDF. */
const val ORIGEM_PDF = "PDF"

/**
 * Dias do PDF dentro da janela pedida, no formato da projeção — para o calendário tratar os
 * dois modos da mesma maneira. Os dias de registo manual não entram: no modo PDF a grelha é a
 * do PDF. O tipo pode vir null (o importador não o guarda): o chip é derivado em
 * [tipoDerivadoDoPdf].
 */
fun diasDoPdf(
    diasReais: List<DiaReal>,
    deEpochDay: Long,
    ateEpochDay: Long
): List<DiaProjetado> =
    diasReais
        .filter { it.origem == ORIGEM_PDF }
        .filter { it.data in deEpochDay..ateEpochDay }
        .map { dr -> DiaProjetado(epochDay = dr.data, tipoTurnoId = dr.tipoTurnoId) }

/**
 * Fonte dos chips da grelha: no modo PDF vem do PDF importado; nos restantes, da projeção da
 * rotação (vazia quando não há nenhuma aplicação em vigor).
 */
fun diasProjetadosPara(
    tipoEscala: String,
    diasReais: List<DiaReal>,
    deEpochDay: Long,
    ateEpochDay: Long,
    aplicacoes: List<AplicacaoVigente>
): List<DiaProjetado> = when (tipoEscala) {
    TIPO_ESCALA_PDF_MENSAL -> diasDoPdf(diasReais, deEpochDay, ateEpochDay)
    else -> if (aplicacoes.isEmpty()) {
        emptyList()
    } else {
        projetarIntervalo(deEpochDay, ateEpochDay, aplicacoes)
    }
}

/**
 * Há escala para mostrar neste mês? No modo PDF conta um dia importado do PDF dentro do mês
 * visível; no modo rotação, ter uma aplicação em vigor (é o que já era: a projeção vale para
 * todos os meses).
 */
fun temEscalaAplicada(
    tipoEscala: String,
    diasReais: List<DiaReal>,
    aplicacoes: List<AplicacaoVigente>,
    mes: YearMonth
): Boolean = when (tipoEscala) {
    TIPO_ESCALA_PDF_MENSAL -> diasReais.any { dr ->
        dr.origem == ORIGEM_PDF &&
            LocalDate.ofEpochDay(dr.data).year == mes.year &&
            LocalDate.ofEpochDay(dr.data).monthValue == mes.monthValue
    }
    else -> aplicacoes.isNotEmpty()
}

/**
 * Tipo de turno a mostrar no chip de um dia importado do PDF.
 *
 * O dia_real do PDF guarda só inputs (horas e posto), nunca o tipo: derivá-lo aqui evita
 * guardar um valor calculado que ficaria obsoleto se o tipo de turno fosse editado, e funciona
 * com os dias já importados. Sem horas conta como folga; com horas procura o tipo de trabalho
 * com as mesmas horas e, se não existir, usa o primeiro de trabalho. Null quando não há tipos
 * dessa categoria — nesse caso o chip fica vazio.
 */
fun tipoDerivadoDoPdf(
    diaReal: DiaReal,
    tiposTurno: List<TipoTurno>
): TipoTurno? {
    val duracao = duracaoMinutos(diaReal.inicioMin, diaReal.fimMin, diaReal.pausaMin)
    return when {
        duracao <= 0 ->
            tiposTurno.firstOrNull { it.categoria == CategoriaTurno.FOLGA }
        else ->
            tiposTurno.firstOrNull {
                it.categoria == CategoriaTurno.TRABALHO &&
                    it.inicioMin == diaReal.inicioMin &&
                    it.fimMin == diaReal.fimMin
            } ?: tiposTurno.firstOrNull { it.categoria == CategoriaTurno.TRABALHO }
    }
}
