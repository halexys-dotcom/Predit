package pt.haconnect.predit.domain.calc

import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.TipoTurno
import pt.haconnect.predit.domain.model.dividirArredondando
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Motor de cálculo do CCT. Funções puras — sem Android, sem Room.
 * Todos os valores estão em unidades de 1/10000 € (ver domain/model/Dinheiro.kt).
 *
 * Regra: multiplicar antes de dividir e arredondar só no fim, com
 * [dividirArredondando]. 65 653 × 35 ÷ 16 = 143 615,9375 → 143 616 (14,36 €),
 * e nunca 14,37 €.
 */
data class ContextoCalculo(
    val vencimentoBaseMil: Int,
    val horarioSemanalH: Int,
    val janelaNoturna: Pair<Int, Int>,   // de janelaNoturna() em Contrato.kt
    val diasUteisMes: Int,               // de DiaReal / calendário
    val numDependentes: Int              // do contrato
)

/**
 * Valor/hora em unidades de 1/10000 EUR, arredondado ao centimo para bater com o recibo:
 *   (vencimentoBaseMil x 12) / (52 x horarioSemanalH), arredondado aos 100 unidades.
 *
 * 2026, 40h: 65 700 (6,57 EUR/h). O recibo da empresa imprime 6,57.
 * 2025, 40h: 62 100 (6,21 EUR/h).
 */
fun valorHoraMil(ctx: ContextoCalculo): Int {
    require(0 < ctx.horarioSemanalH) { "horarioSemanalH tem de ser positivo" }
    val numerador = ctx.vencimentoBaseMil.toLong() * 12L
    val denominador = 52L * ctx.horarioSemanalH
    return dividirArredondando(numerador, denominador * 100L).toInt() * 100
}

/** Valor/dia de 8h em unidades de 1/10000 EUR: 65 700 x 8 = 525 600 (52,56 EUR). */
fun valorDiaMil(ctx: ContextoCalculo): Int = valorHoraMil(ctx) * 8

/** Valor/minuto em unidades de 1/10000 EUR: valor/hora / 60 (65 700 / 60 = 1 095). */
fun valorMinutoMil(ctx: ContextoCalculo): Int =
    dividirArredondando(valorHoraMil(ctx).toLong(), 60L).toInt()

/**
 * Multiplicadores da CCT (handover, secção 4), em fracções exatas — nunca Double:
 * 1,25 = 5/4 · 1,5 = 3/2 · 2,1875 = 35/16 · 3 = 3/1 · 4,375 = 35/8 · 4,5 = 9/2 · 5,25 = 21/4.
 *
 * Duas leituras, e é preciso não as trocar:
 *  - as SUP_* pagam a hora por inteiro (a hora extra não está no vencimento mensal, que já
 *    paga as horas normais do contrato), por isso o multiplicador é o total da CCT;
 *  - [NOTURNA_ACRESCIMO] é só o acréscimo sobre a hora normal (0,25 = 1/4): a hora noturna
 *    é uma hora da jornada, essa já está paga no VENC, e a empresa só soma os 25% — é o que
 *    o recibo ICTS confirma ("H01 Horas nocturnas 25% — 1,64 €/h").
 */
enum class TipoHora(val multiplicadorNumerador: Int, val multiplicadorDenominador: Int) {
    NORMAL(1, 1),                    // 1.0
    NOTURNA_ACRESCIMO(1, 4),         // 0.25 (acréscimo: 1,64 €/h em 2026)
    SUP_DIURNO_NORMAL(3, 2),         // 1.5
    SUP_NOTURNO_NORMAL(35, 16),      // 2.1875
    SUP_DIURNO_FERIADO(3, 1),        // 3.0
    SUP_NOTURNO_FERIADO(35, 8),      // 4.375
    SUP_DIURNO_DESCANSO(9, 2),       // 4.5
    SUP_NOTURNO_DESCANSO(21, 4)      // 5.25
}

/**
 * Valor de uma hora do tipo indicado, em unidades de 1/10000 €.
 *
 * Exemplo do handover (sup. noturno em dia normal, 2026):
 *   65 653 × 35 ÷ 16 = 2 297 855 ÷ 16 = 143 615,9375 → 143 616 → 14,36 €
 */
fun valorHoraTipo(ctx: ContextoCalculo, tipo: TipoHora): Int {
    val numerador = valorHoraMil(ctx).toLong() * tipo.multiplicadorNumerador
    return dividirArredondando(numerador, tipo.multiplicadorDenominador.toLong()).toInt()
}

/** Meses de um ano civil — serve para normalizar o mês do pagamento. */
private const val MESES_DO_ANO = 12

/**
 * Subsídio de Natal ou de férias, em unidades de 1/10000 €.
 *
 * Regra do utilizador (2026-11-21): o subsídio vale 1 mês de **vencimento base** para quem
 * trabalhou o ano civil inteiro e é proporcional aos **dias de serviço** em todos os outros casos:
 *
 *     subsídio = base × dias de serviço ÷ dias do ano          (365, ou 366 em ano bissexto)
 *
 * Os dias de serviço vão do primeiro dia de serviço no ano — 1 de janeiro para quem já trabalhava
 * no ano anterior, a [dataAdmissao] para quem entrou a meio — até ao fim do mês do pagamento, e
 * descontam os dias cobertos por ausências que **não** contam como serviço prestado. Dessas, só a
 * BAIXA ([CategoriaTurno.BAIXA]) desconta — é o que a empresa faz no caso real do utilizador
 * (baixa de 18/07/2025 a 19/05/2026). As férias contam como serviço, como já contam no VENC, e as
 * folgas não são ausências registadas. Diuturnidades, subsídio de alimentação e horas extra não
 * entram na base: a base é o vencimento base do CCT, mais nada.
 *
 * Exemplos com 1 137,98 € de base, Natal pago em dezembro de 2026 (365 dias):
 *  - ano inteiro, sem baixa: 11 379 800 (o próprio vencimento base, sem divisão);
 *  - baixa de 1 de janeiro a 31 de maio (151 dias): 214 ÷ 365 → 667,20 € (≈ 7/12);
 *  - admitido a 1 de julho (184 dias de serviço): 184 ÷ 365 → 573,67 € (≈ metade).
 *
 * A admissão posterior ao mês do pagamento devolve 0. O mês do pagamento conta até ao fim — é o
 * que faz dezembro fechar exatamente o ano.
 */
fun calcularSubsidioProporcional(
    baseMil: Long,
    dataAdmissao: LocalDate,
    ano: Int,
    mesPagamento: Int,
    ausencias: List<Ausencia> = emptyList(),
    tiposTurno: List<TipoTurno> = emptyList()
): Long {
    val mes = mesPagamento.coerceIn(1, MESES_DO_ANO)
    val ultimoDiaDoAno = LocalDate.of(ano, 12, 31)
    val ultimoDiaDoPeriodo = minOf(
        LocalDate.of(ano, mes, 1).plusMonths(1).minusDays(1),
        ultimoDiaDoAno
    )
    // Admissão depois do período a que o subsídio diz respeito: ainda não há serviço nenhum.
    if (dataAdmissao > ultimoDiaDoPeriodo) return 0L

    val inicio = maxOf(dataAdmissao, LocalDate.of(ano, 1, 1))
    val diasDoAno = ultimoDiaDoAno.dayOfYear
    val diasDeServico = ChronoUnit.DAYS.between(inicio, ultimoDiaDoPeriodo).toInt() + 1
    val diasDeBaixa = diasDeAusenciaQueNaoContam(ausencias, tiposTurno, inicio, ultimoDiaDoPeriodo)
    val diasEfetivos = (diasDeServico - diasDeBaixa).coerceIn(0, diasDoAno)
    // Ano inteiro sem baixa: o subsídio é o próprio vencimento base, sem passar pela divisão.
    if (diasEfetivos >= diasDoAno) return baseMil
    return dividirArredondando(baseMil * diasEfetivos, diasDoAno.toLong())
}

/**
 * Dias de calendário, entre [inicio] e [fim] (inclusive), cobertos por ausências que **não** contam
 * como tempo de serviço para o subsídio. Só a BAIXA ([CategoriaTurno.BAIXA]) desconta: as férias
 * são tempo de serviço pago — é assim que o VENC as trata — e as folgas não são ausências
 * registadas. A união dos dias garante que baixas sobrepostas contam uma vez só, e o recorte ao
 * período garante que uma baixa a cavalo de dois anos só pesa no ano que lhe pertence.
 *
 * Sem catálogo de tipos não se desconta nada — melhor manter o subsídio do que cortá-lo por um id
 * que não se sabe o que é (mesma regra de [temFeriasNoMesOuSeguinte]).
 */
private fun diasDeAusenciaQueNaoContam(
    ausencias: List<Ausencia>,
    tiposTurno: List<TipoTurno>,
    inicio: LocalDate,
    fim: LocalDate
): Int {
    if (ausencias.isEmpty() || tiposTurno.isEmpty()) return 0
    val categoriasPorTipo = tiposTurno.associate { it.id to it.categoria }
    val primeiro = inicio.toEpochDay()
    val ultimo = fim.toEpochDay()
    val dias = mutableSetOf<Long>()
    for (ausencia in ausencias) {
        if (categoriasPorTipo[ausencia.tipoTurnoId] != CategoriaTurno.BAIXA) continue
        var dia = maxOf(ausencia.dataInicio, primeiro)
        val ate = minOf(ausencia.dataFim, ultimo)
        while (dia <= ate) {
            dias.add(dia)
            dia++
        }
    }
    return dias.size
}

