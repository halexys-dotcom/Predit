package pt.haconnect.predit.domain.importacao

import pt.haconnect.predit.domain.calc.NaturezaRubrica

/**
 * Como uma linha de recibo (código + descrição + valor) se liga ao catálogo de rubricas
 * da app.
 *
 * [codigoInterno] é o código de `Rubrica.codigo` ("VENC", "SUP_ALIM", ...). Fica a null
 * quando o código do recibo não tem rubrica equivalente: a linha é lida, mas não tem
 * para onde ir, e [aviso] explica o que se perdeu.
 */
data class MapeamentoRubrica(
    val codigoRecibo: String,
    val codigoInterno: String?,
    val natureza: NaturezaRubrica,
    val aviso: String?
)

/**
 * Códigos de recibo que a app sabe mapear para uma rubrica do seu catálogo.
 *
 * Os nomes entre parênteses são os que aparecem na descrição do recibo e não são usados
 * no código: servem só para quem ler isto não ter de abrir um PDF.
 * Fonte: recibos PRIMAVERA de 2026 e recibo Cegid de 2025 do utilizador.
 */
private val RUBRICAS: Map<String, String> = mapOf(
    "R01" to "VENC",      // Vencimento
    "R14" to "SUP_TRAN",  // Subsidio transport
    "SAC" to "SUP_ALIM",  // Subsídio Alimentação Cartão (PRIMAVERA)
    "RCR" to "SUP_ALIM",  // Subsídio Alimentação Cartão (Cegid 2025 — mesmo subsídio, outro código)
    "RCRA" to "SUP_ALIM", // ... acerto do subsídio de alimentação (Cegid 2025)
    "F40" to "SUP_ALIM",  // Subsídio de alimentação por dia (formato antigo)
    "R97" to "FERIAS"     // Subsídio de Férias
)

/**
 * Códigos conhecidos que não têm rubrica própria: entram em "OUTROS", com aviso — nunca
 * silenciosamente, porque o valor deles não entra nos totais do recibo e o utilizador
 * tem de saber que foi lançado ali.
 */
private val PARA_OUTROS: Map<String, String> = mapOf(
    "F02" to "Faltas",
    "F50" to "Gozo de férias",
    "F74" to "Ausência não remunerada parcial",
    "F01" to "Baixa médica",
    "R51" to "Complemento de baixa",
    "R118" to "Média de horas noturnas"
)

/** Códigos cujo valor sai do recibo: a natureza deles é DESCONTO, não ABONO. */
private val CODIGOS_DESCONTO = setOf("D01", "D02", "D04")

/**
 * Traduz um código de recibo (`R01`, `D02`, ...) para o que a app precisa de saber:
 * a rubrica interna, a natureza (abono/desconto) e, quando há dúvidas, um aviso.
 *
 * Códigos desconhecidos não rebentam a importação — devolvem `codigoInterno = null` e um
 * aviso, e quem chama decide se mostra ou ignora a linha.
 */
fun mapearCodigoRecibo(codigo: String): MapeamentoRubrica {
    val normalizado = codigo.trim().uppercase()
    return when {
        normalizado in CODIGOS_DESCONTO ->
            MapeamentoRubrica(normalizado, normalizado, NaturezaRubrica.DESCONTO, null)

        RUBRICAS.containsKey(normalizado) ->
            MapeamentoRubrica(
                normalizado, RUBRICAS.getValue(normalizado), NaturezaRubrica.ABONO, null
            )

        PARA_OUTROS.containsKey(normalizado) ->
            MapeamentoRubrica(
                normalizado, "OUTROS", NaturezaRubrica.ABONO,
                "Código $normalizado (${PARA_OUTROS.getValue(normalizado)}): lançado em Outros"
            )

        else ->
            MapeamentoRubrica(
                normalizado, null, NaturezaRubrica.ABONO,
                "Código $normalizado desconhecido: linha ignorada"
            )
    }
}
