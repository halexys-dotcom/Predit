package pt.haconnect.predit.domain.model

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
