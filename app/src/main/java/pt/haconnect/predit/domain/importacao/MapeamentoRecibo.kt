package pt.haconnect.predit.domain.importacao

import pt.haconnect.predit.domain.calc.NaturezaRubrica

/**
 * Como uma linha de recibo (código + descrição + valor) se liga ao catálogo de rubricas
 * da app.
 *
 * [codigoInterno] é o código de `Rubrica.codigo` ("VENC", "SUP_ALIM", ...). Fica a null em dois
 * casos e, nos dois, a linha é lida mas não escreve valor nenhum no mês:
 * - o código do recibo não tem rubrica equivalente, e [aviso] explica o que se perdeu;
 * - o código é uma linha informativa (ver [CODIGOS_INFORMATIVOS]): baixa, férias gozadas,
 *   ausência parcial, subsídio de alimentação durante a baixa. Está no PDF, mas não é pagamento.
 *
 * A [natureza] das linhas informativas fica ABONO porque `NaturezaRubrica` só tem ABONO e
 * DESCONTO — e não tem consequência: sem `codigoInterno` a linha nunca chega a ser escrita.
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
 *
 * As linhas informativas não entram aqui: não têm rubrica onde escrever (ver
 * [CODIGOS_INFORMATIVOS]). O "F40" (subsídio de alimentação por dia, formato antigo) esteve
 * neste mapa e saiu — nos recibos reais é linha informativa, e deixá-lo aqui era um ramo
 * morto que só induzia em erro.
 */
private val RUBRICAS: Map<String, String> = mapOf(
    "R01" to "VENC",      // Vencimento
    "R14" to "SUP_TRAN",  // Subsidio transport
    "SAC" to "SUP_ALIM",  // Subsídio Alimentação Cartão (PRIMAVERA)
    "RCR" to "SUP_ALIM",  // Subsídio Alimentação Cartão (Cegid 2025 — mesmo subsídio, outro código)
    "RCRA" to "SUP_ALIM", // ... acerto do subsídio de alimentação (Cegid 2025)
    "R97" to "FERIAS"     // Subsídio de Férias
)

/**
 * Códigos ICTS que aparecem no PDF mas NÃO representam pagamento. Ignorados na importação (não
 * escrevem valorReal). Descritos no PDF original se o utilizador precisar de os ver.
 *
 * Confirmado 2026-10-16 com base em 5 recibos reais:
 * - F01/F02: dias de baixa (informativo)
 * - F40: subsídio de alimentação durante a baixa (pago por engano pela empresa num mês,
 *   devolvido — não é pago)
 * - F50: gozo de férias (informativo)
 * - F74: ausência parcial (informativo)
 *
 * Confirmação nos fixtures reais (`src/test/resources/fixtures/`): todas estas linhas trazem
 * Quantidade e Preço Unitário e **nenhum** valor na coluna Importância — não somam no Total do
 * recibo. O valor que o parser lê para elas (o preço unitário, por ser o número mais à direita)
 * é só descritivo: ninguém o escreve.
 */
private val CODIGOS_INFORMATIVOS = setOf(
    "F01", "F02", "F40", "F50", "F74"
)

/**
 * Códigos que correspondem a OUTRA rubrica interna sem ser o código natural. Mapeados para
 * "OUTROS", mas com valor: ao contrário das linhas informativas, estes são pagamentos reais e
 * contam para o Total (trazem Importância no recibo).
 */
private val CODIGOS_PARA_OUTROS = setOf(
    "R51",   // Complemento de baixa
    "R118"   // Média de horas noturnas
)

/** Códigos cujo valor sai do recibo: a natureza deles é DESCONTO, não ABONO. */
private val CODIGOS_DESCONTO = setOf("D01", "D02", "D04")

/**
 * Traduz um código de recibo (`R01`, `D02`, ...) para o que a app precisa de saber:
 * a rubrica interna, a natureza (abono/desconto) e, quando há dúvidas, um aviso.
 *
 * Códigos desconhecidos não rebentam a importação — devolvem `codigoInterno = null` e um
 * aviso, e quem chama decide se mostra ou ignora a linha. As linhas informativas usam o mesmo
 * mecanismo (`null` + aviso) de propósito: para quem chama, uma linha informativa e uma linha
 * desconhecida têm o mesmo destino — não escrever valor.
 */
fun mapearCodigoRecibo(codigo: String): MapeamentoRubrica {
    val normalizado = codigo.trim().uppercase()
    return when {
        // Informativo em primeiro lugar: a classificação manda sobre tudo o resto, e o teste
        // tranca-a. Hoje nenhum código informativo tem entrada nas listas abaixo, mas a ordem
        // deixa o contrato explícito para quem mexer aqui.
        normalizado in CODIGOS_INFORMATIVOS ->
            MapeamentoRubrica(
                normalizado, null, NaturezaRubrica.ABONO,
                "Código $normalizado: linha informativa, não é pagamento (fica fora do recibo)"
            )

        normalizado in CODIGOS_DESCONTO ->
            MapeamentoRubrica(normalizado, normalizado, NaturezaRubrica.DESCONTO, null)

        RUBRICAS.containsKey(normalizado) ->
            MapeamentoRubrica(
                normalizado, RUBRICAS.getValue(normalizado), NaturezaRubrica.ABONO, null
            )

        normalizado in CODIGOS_PARA_OUTROS ->
            MapeamentoRubrica(
                normalizado, "OUTROS", NaturezaRubrica.ABONO,
                "Código $normalizado: pagamento real, lançado em Outros"
            )

        else ->
            MapeamentoRubrica(
                normalizado, null, NaturezaRubrica.ABONO,
                "Código $normalizado desconhecido: linha ignorada"
            )
    }
}
