package pt.haconnect.predit.domain.model

import pt.haconnect.predit.domain.calc.NaturezaRubrica

/**
 * Parâmetros salariais do CCT vigentes numa data.
 * Unidades: 1/10000 € (ver domain/model/Dinheiro.kt).
 */
data class ParametrosCCT(
    val id: Long = 0,
    val validoDe: Long,                   // epochDay
    val vencimentoBaseMil: Int,
    val subAlimentacaoDiaMil: Int,
    val subTransporteMesMil: Int,
    val horarioSemanalReferencia: Int
)

/**
 * Rubrica do recibo, em termos de domínio.
 * tipoCalculo: FIXO | HORAS | MANUAL | DERIVADO (descontos sobre as bases de incidência).
 * incide*: bases de incidência para SS, IRS e sindicato.
 */
data class Rubrica(
    val id: Long = 0,
    val codigo: String,
    val nome: String,
    val incideSS: Boolean,
    val incideIRS: Boolean,
    val incideSindicato: Boolean,
    val tipoCalculo: String,
    val ativaConferencia: Boolean,
    val ordem: Int
)

/**
 * Cabeçalho do recibo introduzido, em termos de domínio.
 * Unidades: 1/10000 € (ver domain/model/Dinheiro.kt).
 *
 * A chave é o mês ("2026-08"): um recibo por mês.
 */
data class ReciboMes(
    val anoMes: String,
    val dataFecho: Long,                  // epochDay (do cabeçalho do recibo)
    val vencimentoBase: Long,             // 1/10000 €
    val vencimentoHora: Long,             // 1/10000 €
    val numDiasUteis: Int,
    val irsRetidoAno: Long,               // acumulado do ano (cabeçalho)
    val totalAbonos: Long,
    val totalDescontos: Long,
    val liquido: Long,
    val nota: String?,
    val dataCriacao: Long
)

/**
 * Uma rubrica do recibo introduzido: o valor que a app estimou e o valor que o recibo
 * traz, lado a lado. Uma linha por rubrica por mês.
 *
 * A natureza é a [NaturezaRubrica] já usada pelo estimador (domain/calc) — é o mesmo
 * conceito, portanto a mesma enum, para não haver duas listas de ABONO/DESCONTO.
 */
data class ReciboLinha(
    val reciboMesId: String,
    val rubricaId: Long,
    val valorEstimado: Long,              // 1/10000 €
    val valorReal: Long,                  // 1/10000 €
    val natureza: NaturezaRubrica,
    val ordem: Int
)
