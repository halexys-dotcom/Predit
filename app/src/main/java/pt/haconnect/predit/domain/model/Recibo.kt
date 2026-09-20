package pt.haconnect.predit.domain.model

import pt.haconnect.predit.domain.calc.NaturezaRubrica

/**
 * Categoria usada quando o contrato não tem categoria ou quando ela não tem tabela salarial
 * para o ano do mês a estimar. É a categoria de referência da app (e a que existia antes da 13a).
 */
const val CATEGORIA_CCT_PADRAO = "APAA"

/**
 * Parâmetros salariais do CCT vigentes numa data, para uma categoria.
 * Unidades: 1/10000 € (ver domain/model/Dinheiro.kt).
 */
data class ParametrosCCT(
    val id: Long = 0,
    val validoDe: Long,                   // epochDay
    val vencimentoBaseMil: Int,
    val subAlimentacaoDiaMil: Int,
    val subTransporteMesMil: Int,
    val horarioSemanalReferencia: Int,
    /** Categoria CCT a que estes valores pertencem (13a). */
    val codigoCategoria: String = CATEGORIA_CCT_PADRAO,
    val nivelCCT: String = "XIII",
    val nomeCategoria: String = "Vigilante Aeroportuário/APA-A",
    /**
     * Subsídio de função mensal (1/10000 €). Zero em todas as categorias excepto o Team Leader.
     * O estimador prorrateia-o por 22 dias e corta-o pelos dias úteis de ausência.
     */
    val subsidioFuncaoMil: Long = 0L
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
