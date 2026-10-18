package pt.haconnect.predit.domain.importacao

import pt.haconnect.predit.domain.calc.NaturezaRubrica

/** Espaços seguidos, para colapsar em [chaveDaEmpresa]. Compilada uma vez, não por chamada. */
private val REGEX_ESPACOS = Regex("\\s+")

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
/**
 * De onde veio a decisão de [MapeamentoRubrica.codigoInterno] — é o que permite ao parser
 * distinguir "código que a app não conhece" (para pedir o mapeamento ao utilizador, Fase 28c)
 * de "linha informativa" (que é para ficar de fora, por desenho) e do que já está no mapa do
 * utilizador (para a UI poder mostrar de onde veio).
 */
enum class OrigemMapeamento {
    /** Uma das entradas fixas em [RUBRICAS]. */
    HARDCODED,
    /** Linha informativa (ver [CODIGOS_INFORMATIVOS]): está no PDF mas não é pagamento. */
    INFORMATIVO,
    /** Pagamento real sem rubrica própria: vai para "OUTROS". */
    PARA_OUTROS,
    /** O próprio código é a rubrica de desconto (D01/D02/D04). */
    DESCONTO,
    /** Mapeamento configurado pelo utilizador e guardado na BD (empresa + código). */
    UTILIZADOR,
    /** Nenhum mapa o conhece: aguarda mapeamento do utilizador. */
    DESCONHECIDO
}

data class MapeamentoRubrica(
    val codigoRecibo: String,
    val codigoInterno: String?,
    val natureza: NaturezaRubrica,
    val aviso: String?,
    val origem: OrigemMapeamento
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
    "R12" to "SUP_ALIM",  // Subsídio Alimentação - Valor Fixo (PSG)
    "R14" to "SUP_TRAN",  // Subsidio transport
    "SAC" to "SUP_ALIM",  // Subsídio Alimentação Cartão (PRIMAVERA)
    "RCR" to "SUP_ALIM",  // Subsídio Alimentação Cartão (Cegid 2025 — mesmo subsídio, outro código)
    "RCRA" to "SUP_ALIM", // ... acerto do subsídio de alimentação (Cegid 2025)
    "R30" to "FERIAS",    // Subsídio de Férias (PSG)
    "R31" to "NATAL",     // Subsídio de Natal (PSG)
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
fun mapearCodigoRecibo(
    codigo: String,
    mapeamentosUtilizador: Map<String, String> = emptyMap()
): MapeamentoRubrica {
    val normalizado = codigo.trim().uppercase()

    // 1. Informativos (prioridade máxima: o utilizador não os pode transformar em pagamento —
    //    são linhas sem Importância no recibo e escrevê-las punha dinheiro que não existe).
    if (normalizado in CODIGOS_INFORMATIVOS) {
        return MapeamentoRubrica(
            normalizado, null, NaturezaRubrica.ABONO,
            "Código $normalizado: linha informativa, não é pagamento (fica fora do recibo)",
            OrigemMapeamento.INFORMATIVO
        )
    }

    // 2. Mapa do utilizador (BD) — é o que permite a outra empresa sem recompilar a app.
    val doUtilizador = mapeamentosUtilizador[normalizado]
    if (doUtilizador != null) {
        val natureza =
            if (doUtilizador in CODIGOS_DESCONTO) NaturezaRubrica.DESCONTO else NaturezaRubrica.ABONO
        return MapeamentoRubrica(
            normalizado, doUtilizador, natureza, null, OrigemMapeamento.UTILIZADOR
        )
    }

    // 3. Hardcoded (recibos PRIMAVERA/Cegid do utilizador).
    if (normalizado in CODIGOS_DESCONTO) {
        return MapeamentoRubrica(
            normalizado, normalizado, NaturezaRubrica.DESCONTO, null, OrigemMapeamento.DESCONTO
        )
    }
    if (RUBRICAS.containsKey(normalizado)) {
        return MapeamentoRubrica(
            normalizado, RUBRICAS.getValue(normalizado), NaturezaRubrica.ABONO, null,
            OrigemMapeamento.HARDCODED
        )
    }
    if (normalizado in CODIGOS_PARA_OUTROS) {
        return MapeamentoRubrica(
            normalizado, "OUTROS", NaturezaRubrica.ABONO,
            "Código $normalizado: pagamento real, lançado em Outros", OrigemMapeamento.PARA_OUTROS
        )
    }

    // 4. Desconhecido: a linha é lida mas não escreve valor; o código sobe na lista de
    //    `codigosDesconhecidos` do recibo para a UI poder pedir o mapeamento (Fase 28c).
    return MapeamentoRubrica(
        normalizado, null, NaturezaRubrica.ABONO,
        "Código $normalizado desconhecido: aguarda mapeamento", OrigemMapeamento.DESCONHECIDO
    )
}

/**
 * Chave normalizada para identificar a empresa emissora na tabela
 * mapeamento_rubrica. Regra unica aplicada na leitura (parser) e na
 * escrita (modal de mapeamento), senao o save fica assimetrico e o
 * lookup nunca casa.
 *
 * Regras: uppercase, cortar depois da primeira virgula, colapsar
 * espacos multiplos, trim final.
 *
 * Exemplos:
 *   "ICTS PORTUGAL, SA"     -> "ICTS PORTUGAL"
 *   "  ICTS PORTUGAL  "     -> "ICTS PORTUGAL"
 *   "Securitas, Lda."       -> "SECURITAS"
 */
internal fun chaveDaEmpresa(empresa: String): String =
    empresa.uppercase()
        .substringBefore(",")
        .trim()
        .replace(REGEX_ESPACOS, " ")
