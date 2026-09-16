package pt.haconnect.predit.domain.model

import kotlin.math.abs

/**
 * Dinheiro em unidades de 1/10000 de euro.
 *
 * Escala: 1/10000 €.
 *   6,5653 €/h = 65 653 unidades.
 *   (vencimentoBaseMil × 12) ÷ 2080 = valorHoraMil
 *   Arredondar APENAS na apresentação.
 *   Limiar de divergência: 100 unidades = 1 cêntimo.
 *
 * Porque não cêntimos nem Double:
 *   O valor/hora do CCT tem 4 casas — 6,5653 €/h. Em cêntimos seria 656,53 e perde-se
 *   a 4.ª casa; o total deixa então de bater com a CCT:
 *     6,5653 × 1,25 × 1,75 = 14,36 €   (certo)
 *     6,57   × 1,25 × 1,75 = 14,37 €   (errado)
 *   Em Double há ruído de vírgula flutuante e a comparação com o recibo deixa de ser exata.
 *
 * Regra: nunca arredondar a meio do cálculo. Guarda-se e calcula-se sempre em unidades;
 * o arredondamento ao cêntimo acontece só na apresentação e na comparação com o recibo.
 * Por isso os passos intermédios são [ValorExato] — fração exata, em Long — e o cêntimo
 * só aparece no fim.
 *
 * Nota de nomes: os campos das entidades e esta API usam o sufixo "Mil" por fidelidade
 * ao comando da Fase 8.1, mas a escala é 1/10000 — 65 653 = 6,5653 €, e não 65,653 €.
 *
 * Limite: a unidade é Int, logo o máximo é 214 748,36 € — folgado para um recibo mensal.
 * Os passos intermédios usam Long (11 379 800 × 12 já passa de 10^8; 13 630,40 × 7,63%
 * passa de 10^10).
 */
const val ESCALA_MONETARIA = 10_000L
const val UNIDADES_POR_CENTIMO = 100L

/** Uma diferença de até 1 cêntimo não conta como divergência. */
const val LIMIAR_DIVERGENCIA_UNIDADES = 100

/**
 * Fração exata, para coeficientes e taxas que não são inteiros.
 * Ex.: 1,25 = Fracao(5, 4) · 1,75 = Fracao(7, 4) · 4,29% = percentagem(429)
 */
data class Fracao(val numerador: Long, val denominador: Long = 1L) {
    init {
        require(denominador > 0L) { "denominador tem de ser positivo" }
    }
}

/** 7,63% → Fracao(763, 10000). */
fun percentagem(decimosDePorCento: Long): Fracao = Fracao(decimosDePorCento, 10_000L)

/**
 * Valor monetário exato, em unidades de 1/10000 €, guardado como fração para não
 * perder precisão a meio do cálculo.
 */
data class ValorExato(val numerador: Long, val denominador: Long = 1L) {
    init {
        require(denominador > 0L) { "denominador tem de ser positivo" }
    }

    operator fun times(fracao: Fracao): ValorExato =
        reduzir(ValorExato(numerador * fracao.numerador, denominador * fracao.denominador))

    operator fun div(fracao: Fracao): ValorExato {
        require(fracao.numerador != 0L) { "divisão por zero" }
        return reduzir(ValorExato(numerador * fracao.denominador, denominador * fracao.numerador))
    }

    operator fun plus(outro: ValorExato): ValorExato = reduzir(
        ValorExato(
            numerador * outro.denominador + outro.numerador * denominador,
            denominador * outro.denominador
        )
    )

    operator fun minus(outro: ValorExato): ValorExato = reduzir(
        ValorExato(
            numerador * outro.denominador - outro.numerador * denominador,
            denominador * outro.denominador
        )
    )

    /**
     * Único ponto em que o cálculo perde precisão: arredonda ao cêntimo (half-up,
     * afastando-se do zero) e devolve unidades de 1/10000 € — o valor que se
     * apresenta e se compara com o recibo.
     */
    fun arredondarParaCentimo(): Int {
        val centimos = dividirArredondando(numerador, denominador * UNIDADES_POR_CENTIMO)
        val unidades = centimos * UNIDADES_POR_CENTIMO
        require(unidades in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "valor fora do intervalo representável: $unidades unidades de 1/10000 €"
        }
        return unidades.toInt()
    }
}

/** Resultado da conferência de uma rubrica: estimado vs. recibo. */
data class Divergencia(
    val estimadoUnidades: Int,   // já arredondado ao cêntimo
    val realUnidades: Int,       // como veio no recibo
    val diferencaUnidades: Int,  // estimado − real
    val dentroDoLimiar: Boolean  // |diferença| ≤ limiar (1 cêntimo por omissão)
)

/**
 * Compara a estimativa (arredondada ao cêntimo) com o que veio no recibo.
 * Por omissão, diferenças de 1 cêntimo ficam dentro do limiar.
 */
fun compararComRecibo(
    estimado: ValorExato,
    realUnidades: Int,
    limiarUnidades: Int = LIMIAR_DIVERGENCIA_UNIDADES
): Divergencia {
    val est = estimado.arredondarParaCentimo()
    val diferenca = est - realUnidades
    return Divergencia(
        estimadoUnidades = est,
        realUnidades = realUnidades,
        diferencaUnidades = diferenca,
        dentroDoLimiar = abs(diferenca.toLong()) <= limiarUnidades
    )
}

/**
 * Lê dinheiro escrito como nos recibos: "6,5653" · "14,36" · "1 190,07" · "14.36".
 * Falha em voz alta se o texto não for válido (nunca mais de 4 casas decimais).
 */
fun deTexto(valor: String): ValorExato {
    val limpo = valor.trim()
        .removeSuffix("€").trim()
        .replace(" ", "")
        .replace("\u00A0", "")
    require(limpo.isNotEmpty()) { "valor vazio" }

    val negativo = limpo.startsWith("-")
    val corpo = limpo.removePrefix("-").replace(',', '.')
    require(corpo.isNotEmpty() && corpo.all { it.isDigit() || it == '.' }) { "valor inválido: $valor" }

    val partes = corpo.split('.')
    require(partes.size <= 2) { "valor inválido: $valor" }

    val inteiros = partes[0].ifEmpty { "0" }
    val decimais = if (partes.size == 2) partes[1] else ""
    require(decimais.length <= 4) { "máximo de 4 casas decimais: $valor" }

    val unidades = inteiros.toLong() * ESCALA_MONETARIA + decimais.padEnd(4, '0').toLong()
    return ValorExato(if (negativo) -unidades else unidades)
}

/**
 * Apresentação em euros, arredondando ao cêntimo: 11 379 800 → "1 137,98 €".
 * O separador de milhares é um espaço; o sinal é "-" (ASCII), que a UI pode trocar
 * pelo "−" que já usa.
 */
fun Long.milParaEuros(): String {
    val sinal = if (this < 0L) "-" else ""
    val centimos = abs(milParaCentimos())
    val centimosPorEuro = ESCALA_MONETARIA / UNIDADES_POR_CENTIMO
    return "%s%s,%02d €".format(sinal, agruparMilhares(centimos / centimosPorEuro), centimos % centimosPorEuro)
}

/** Arredonda para cêntimo e devolve o valor em cêntimos: 143 616 → 1436. */
fun Long.milParaCentimos(): Long =
    (abs(this) + UNIDADES_POR_CENTIMO / 2) / UNIDADES_POR_CENTIMO * (if (this < 0L) -1L else 1L)

/** Utilitário de UI: 525 224 → "52,52 €". */
fun Int.formatarMoeda(): String = this.toLong().milParaEuros()

/** Valor exato com 4 casas, sem símbolo, para diagnóstico: "6,5653" · "14,3616". */
fun formatarEurosExato(valor: ValorExato): String {
    val sinal = if (valor.numerador < 0L) "-" else ""
    val unidades = dividirArredondando(abs(valor.numerador), valor.denominador)
    return "%s%d,%04d".format(sinal, unidades / ESCALA_MONETARIA, unidades % ESCALA_MONETARIA)
}

/**
 * Divisão inteira com arredondamento half-up, afastando-se do zero (denominador > 0).
 * É a única regra de arredondamento do projeto — usada pelo cêntimo e pelo motor do CCT.
 */
fun dividirArredondando(numerador: Long, denominador: Long): Long {
    val sinal = if (numerador < 0L) -1L else 1L
    return sinal * ((abs(numerador) + denominador / 2) / denominador)
}

/** Simplifica a fração para manter os Long pequenos (e evitar transbordo). */
private fun reduzir(valor: ValorExato): ValorExato {
    if (valor.numerador == 0L) return ValorExato(0L)
    val d = mdc(abs(valor.numerador), valor.denominador)
    return ValorExato(valor.numerador / d, valor.denominador / d)
}

private fun mdc(a: Long, b: Long): Long {
    var x = a
    var y = b
    while (y != 0L) {
        val t = x % y
        x = y
        y = t
    }
    return if (x == 0L) 1L else x
}

/** 1137 → "1 137". */
private fun agruparMilhares(valor: Long): String {
    val texto = valor.toString()
    val sb = StringBuilder()
    for ((i, c) in texto.withIndex()) {
        if (i > 0 && (texto.length - i) % 3 == 0) sb.append(' ')
        sb.append(c)
    }
    return sb.toString()
}
