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
    val tipoTurnoId: Long?,      // null = sem rotação em vigor
    /**
     * Duração prevista do turno deste dia, em minutos, quando o modo de escala a sabe sem passar
     * pelo catálogo de tipos de turno. No modo PDF_MENSAL a escala é o próprio PDF: o dia importado
     * guarda as horas (o tipo é derivado) e — desde a fase 29 — o previsto sobrevive à edição do
     * dia, em [pt.haconnect.predit.domain.model.DiaReal.previstoInicioMin]. É essa duração que
     * serve de régua às horas extra e aos atrasos: um turno de 12h não pode ser medido contra um
     * tipo de 8h só porque é o primeiro de trabalho do catálogo.
     *
     * Null fora do modo PDF (rotação), onde a régua continua a ser o tipo projetado; é o default.
     * Preenche-se em [diasDoPdf] e em [projecaoParaEstimativa].
     */
    val minutosPrevistos: Int? = null
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

/**
 * Minutos que uma ausência efetiva soma ao total do rodapé da Escala: uma jornada de 8h.
 *
 * É um valor fixo, e não a duração do turno, porque é assim que a app conta desde a primeira
 * versão: férias e baixa valem um dia de trabalho, independentemente do turno que estava
 * previsto. Mudar isto mexia nos totais dos meses já validados pelo utilizador.
 */
private const val MINUTOS_DIA_COM_AUSENCIA = 8 * 60

/**
 * Minutos que um dia conta no total de horas do rodapé da Escala.
 *
 * O total é um resumo do mês, não o recibo: um dia sem ausência conta as horas registadas (ou,
 * sem registo, as do turno efetivo) e um dia com ausência efetiva vale uma jornada de 8h se era
 * para trabalhar — e 0 se já era folga. A conta vive aqui, e não no ViewModel da Escala, para
 * poder ser testada: o ViewModel só soma o que esta função devolve.
 *
 * [categoriaAusenciaEfetiva] é a categoria do tipo da ausência que [aplicarAusencias] deixou
 * efetiva neste dia (null quando não há ausência efetiva). Uma ausência do tipo FOLGA vale 0 mesmo
 * em cima de um dia de trabalho — é a regra "FOLGA manda sobre tudo".
 *
 * "Era para trabalhar" tem duas leituras, uma por modo de escala:
 * - modo PDF ([DiaReal.veioDoPdf]): a linha do PDF prevê horas para o dia. As folgas do PDF não
 *   têm linha nenhuma e um dia fora do PDF também não, por isso a ausência aí vale 0 em vez de
 *   inventar 8h de trabalho que a escala nunca teve;
 * - rotação, e dias sem linha do PDF no modo PDF: o tipo projetado é de categoria TRABALHO.
 *
 * Nos dias sem ausência efetiva nada muda: horas registadas, ou as do turno efetivo.
 */
fun minutosDoDiaNaEscala(
    categoriaAusenciaEfetiva: CategoriaTurno?,
    tipoProjetado: TipoTurno?,
    tipoEfetivo: TipoTurno?,
    diaReal: DiaReal?
): Int {
    if (categoriaAusenciaEfetiva != null) {
        if (categoriaAusenciaEfetiva == CategoriaTurno.FOLGA) return 0
        return if (eraDiaDeTrabalhoPrevisto(tipoProjetado, diaReal)) MINUTOS_DIA_COM_AUSENCIA else 0
    }

    return when {
        diaReal != null -> duracaoMinutos(diaReal.inicioMin, diaReal.fimMin, diaReal.pausaMin)
        tipoEfetivo?.categoria == CategoriaTurno.TRABALHO ->
            duracaoMinutos(tipoEfetivo.inicioMin, tipoEfetivo.fimMin, tipoEfetivo.pausaMin)
        else -> 0
    }
}

/**
 * A escala previa trabalho para este dia? A resposta está nas horas do PDF quando o dia veio de um
 * PDF ([horasPrevistasDoDia] — o previsto, não as horas que se acabaram por fazer) e no tipo
 * projetado nos restantes casos. Ver [minutosDoDiaNaEscala].
 */
private fun eraDiaDeTrabalhoPrevisto(tipoProjetado: TipoTurno?, diaReal: DiaReal?): Boolean =
    if (diaReal != null && diaReal.veioDoPdf()) {
        horasPrevistasDoDia(diaReal).duracaoMin > 0
    } else {
        tipoProjetado?.categoria == CategoriaTurno.TRABALHO
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
 * O dia guarda as horas que o PDF previa (colunas `previsto*`)? Verdadeiro em todos os dias
 * importados depois da fase 29 — incluindo os que o utilizador editou, porque a edição muda a
 * origem para MANUAL mas preserva o previsto. Nos dias importados antes da fase 29 as colunas
 * estão a null e o previsto só se conhece pelas horas da própria linha.
 */
fun DiaReal.temPrevistoDoPdf(): Boolean = previstoInicioMin != null && previstoFimMin != null

/**
 * O dia veio de um PDF da empresa — importado tal e qual ou importado e depois corrigido pelo
 * utilizador. É a marca que a grelha, o "há escala" e o estimador usam para reconhecer a escala
 * do PDF sem depender da origem, que a edição reescreve para MANUAL.
 */
fun DiaReal.veioDoPdf(): Boolean = origem == ORIGEM_PDF || temPrevistoDoPdf()

/** Horas de um turno, para não andar com triplos de inteiros à solta. */
private data class HorasDoDia(val inicioMin: Int, val fimMin: Int, val pausaMin: Int) {
    val duracaoMin: Int get() = duracaoMinutos(inicioMin, fimMin, pausaMin)
}

/**
 * Horas que a escala previa para o dia: as do PDF quando as colunas `previsto*` as têm; nos dias
 * importados antes da fase 29 só as da própria linha, que num dia do PDF são as do PDF.
 */
private fun horasPrevistasDoDia(diaReal: DiaReal): HorasDoDia =
    if (diaReal.temPrevistoDoPdf()) {
        HorasDoDia(
            inicioMin = diaReal.previstoInicioMin ?: 0,
            fimMin = diaReal.previstoFimMin ?: 0,
            pausaMin = diaReal.previstoPausaMin ?: 0
        )
    } else {
        HorasDoDia(diaReal.inicioMin, diaReal.fimMin, diaReal.pausaMin)
    }

/**
 * Dias do PDF dentro da janela pedida, no formato da projeção — para o calendário tratar os
 * dois modos da mesma maneira. Entram os dias importados do PDF e também os que o utilizador
 * editou depois ([DiaReal.veioDoPdf]): sem isso, editar um dia tirava-o da grelha e o mês ficava
 * com um dia a menos de escala. Um registo manual que nunca veio de um PDF continua fora — no
 * modo PDF a grelha é a do PDF.
 *
 * O `tipoTurnoId` é o da linha (null nos dias importados, o tipo escolhido pelo utilizador nos
 * dias editados): quem desenha o chip decide, e o estimador deriva o tipo do previsto em
 * [projecaoParaEstimativa]. `minutosPrevistos` vai já preenchido com as horas da escala do dia.
 */
fun diasDoPdf(
    diasReais: List<DiaReal>,
    deEpochDay: Long,
    ateEpochDay: Long
): List<DiaProjetado> =
    diasReais
        .filter { it.veioDoPdf() }
        .filter { it.data in deEpochDay..ateEpochDay }
        .map { dr ->
            DiaProjetado(
                epochDay = dr.data,
                tipoTurnoId = dr.tipoTurnoId,
                minutosPrevistos = horasPrevistasDoDia(dr).duracaoMin
            )
        }

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
 * A projeção que o estimador do recibo usa como régua, com o tipo de turno já resolvido.
 *
 * No modo PDF o dia importado guarda só horas e posto (`tipoTurnoId = null`) e o estimador só
 * conta como dia de trabalho os dias com um tipo de categoria TRABALHO. Sem derivar o tipo
 * aqui, um contrato PDF_MENSAL ficava com 0 dias úteis: o VENC não era proporcional aos dias
 * efetivos, os subsídios saíam a 0,00 € e a régua dos atrasos e dos suplementos ficava vazia.
 *
 * O tipo derivado serve só para saber se o dia é TRABALHO ou FOLGA: as horas previstas vão em
 * [DiaProjetado.minutosPrevistos] a partir da escala do dia (as colunas `previsto*` do PDF, ou as
 * horas da linha nos dados importados antes da fase 29). A duração do tipo não serve de régua —
 * sem isto, um turno de 08:00-20:00 sem tipo correspondente caía no primeiro tipo de trabalho (8h)
 * e o estimador inventava 4h de suplemento.
 *
 * O tipo vem **sempre** do previsto, nunca do tipo guardado na linha: num dia editado a linha é
 * MANUAL e o `tipoTurnoId` é o que o utilizador registou (tipicamente um turno de trabalho). Se
 * fosse esse a contar, uma folga do PDF trabalhada deixava de ser FOLGA e perdia os 200% do
 * DESC_DESC. Sem previsto, a régua são as horas da linha — como era antes da fase 29.
 *
 * **As folgas do PDF também entram.** O importador só cria linhas para os dias com horas (o
 * `parsePdfPlanner` ignora as folgas), por isso a projeção de um mês do PDF nunca tinha os dias
 * sem linha — e um registo MANUAL criado de raiz num desses dias (uma folga trabalhada, que é o
 * único caso em que o utilizador a registra) não era reconhecido como descanso: ficava fora da
 * grelha e sem os 200% do DESC_DESC. Aqui, e só aqui, cada mês do intervalo que tenha pelo menos
 * um dia vindo do PDF ganha uma entrada FOLGA (minutosPrevistos = 0) em todos os dias desse mês
 * que não tenham linha do PDF; os meses sem PDF ficam como estavam. O calendário não muda: quem
 * desenha os chips continua a usar [diasDoPdf], e esses dias ficam sem chip. Sem um tipo FOLGA no
 * catálogo, a entrada fica com `tipoTurnoId = null` e o estimador não a trata como descanso.
 *
 * Fora do modo PDF devolve a projeção da rotação sem lhe tocar — é o comportamento de sempre, e
 * `minutosPrevistos` fica null (a régua volta a ser o tipo projetado).
 */
fun projecaoParaEstimativa(
    tipoEscala: String,
    diasReais: List<DiaReal>,
    deEpochDay: Long,
    ateEpochDay: Long,
    aplicacoes: List<AplicacaoVigente>,
    tiposTurno: List<TipoTurno>
): List<DiaProjetado> {
    val projecao = diasProjetadosPara(tipoEscala, diasReais, deEpochDay, ateEpochDay, aplicacoes)
    if (tipoEscala != TIPO_ESCALA_PDF_MENSAL) return projecao

    val doPdf = diasReais.filter { it.veioDoPdf() }.associateBy { it.data }

    // Meses que o PDF cobre: são esses (e só esses) que ganham as folgas implícitas. Sai das
    // linhas todas, não só das da janela — a janela pode começar a meio do mês.
    val mesesComPdf = doPdf.keys.map { YearMonth.from(LocalDate.ofEpochDay(it)) }.toSet()
    val folgaIdDoCatalogo = tiposTurno.firstOrNull { it.categoria == CategoriaTurno.FOLGA }?.id
    val folgasImplicitas = (deEpochDay..ateEpochDay)
        .filter { epochDay -> epochDay !in doPdf }
        .filter { epochDay -> YearMonth.from(LocalDate.ofEpochDay(epochDay)) in mesesComPdf }
        .map { epochDay ->
            // minutosPrevistos = 0 diz o que o PDF diz: nada previsto. A régua das horas extra
            // de quem trabalha a folga cai então no tipo do registo ou nas 8h de sempre.
            DiaProjetado(epochDay = epochDay, tipoTurnoId = folgaIdDoCatalogo, minutosPrevistos = 0)
        }

    return (projecao + folgasImplicitas)
        .map { dia ->
            val real = doPdf[dia.epochDay] ?: return@map dia
            val horas = horasPrevistasDoDia(real)
            dia.copy(
                // O tipo só decide TRABALHO vs FOLGA (ehTrabalhoProjetado, ehDescanso) e sai do
                // previsto: previsto com 0h é folga, mesmo que a linha diga que se trabalhou.
                tipoTurnoId = tipoDerivadoDoPdf(horas.inicioMin, horas.fimMin, horas.pausaMin, tiposTurno)?.id,
                // A régua das horas extra e dos atrasos é a duração do turno previsto.
                minutosPrevistos = horas.duracaoMin
            )
        }
        .sortedBy { it.epochDay }
}

/**
 * Há escala para mostrar neste mês? No modo PDF conta um dia que veio do PDF dentro do mês
 * visível — importado ou editado depois ([DiaReal.veioDoPdf]): um mês em que o utilizador corrigiu
 * todos os dias continua a ter escala, só que com as horas dele. No modo rotação, ter uma
 * aplicação em vigor (é o que já era: a projeção vale para todos os meses).
 */
fun temEscalaAplicada(
    tipoEscala: String,
    diasReais: List<DiaReal>,
    aplicacoes: List<AplicacaoVigente>,
    mes: YearMonth
): Boolean = when (tipoEscala) {
    TIPO_ESCALA_PDF_MENSAL -> diasReais.any { dr ->
        dr.veioDoPdf() &&
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
 *
 * Esta variante olha para as horas dadas: o chip de um dia editado usa as horas reais que o
 * utilizador registou, e o estimador usa-a com as horas do previsto ([projecaoParaEstimativa]).
 */
fun tipoDerivadoDoPdf(
    inicioMin: Int,
    fimMin: Int,
    pausaMin: Int,
    tiposTurno: List<TipoTurno>
): TipoTurno? {
    val duracao = duracaoMinutos(inicioMin, fimMin, pausaMin)
    return when {
        duracao <= 0 ->
            tiposTurno.firstOrNull { it.categoria == CategoriaTurno.FOLGA }
        else ->
            tiposTurno.firstOrNull {
                it.categoria == CategoriaTurno.TRABALHO &&
                    it.inicioMin == inicioMin &&
                    it.fimMin == fimMin
            } ?: tiposTurno.firstOrNull { it.categoria == CategoriaTurno.TRABALHO }
    }
}

/** As horas do próprio registo. Ver a variante de (inicioMin, fimMin, pausaMin). */
fun tipoDerivadoDoPdf(
    diaReal: DiaReal,
    tiposTurno: List<TipoTurno>
): TipoTurno? = tipoDerivadoDoPdf(diaReal.inicioMin, diaReal.fimMin, diaReal.pausaMin, tiposTurno)
