package pt.haconnect.predit.domain.importacao

import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.model.deTexto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Uma linha de rubrica lida de um recibo em PDF.
 * Dinheiro em unidades de 1/10000 € (ver domain/model/Dinheiro.kt).
 */
data class LinhaReciboImportada(
    val codigoRecibo: String,
    val descricao: String,
    val codigoInterno: String?,
    val natureza: NaturezaRubrica,
    val valor: Long,
    /** Quantidade, quando o recibo a traz (1/10000). `null` nas linhas sem quantidade. */
    val quantidade: Long?,
    /** Data da linha (epochDay): só as ausências, as férias e as baixas a trazem. */
    val data: Long?,
    val ordem: Int,
    val aviso: String?
)

/**
 * Um recibo lido de UMA página de PDF.
 *
 * Um PDF com duas páginas devolve **dois** recibos: é o caso do recibo de Julho, que traz
 * a folha principal (Data Fecho 31/07/2026) e um suplemento (Data Fecho 21/07/2026) para o
 * mesmo mês. Quem escolhe qual importar é a UI (27d); o parser não adivinha.
 */
data class ReciboImportado(
    val pagina: Int,
    val mesReferencia: YearMonth?,
    val dataFecho: Long?,
    /** Vencimento base do cabeçalho (1/10000 €) — o do contrato, não o da linha R01. */
    val vencimentoBase: Long,
    val vencimentoHora: Long,
    val numDiasUteis: Int?,
    /** IRS retido acumulado no ano, do cabeçalho (1/10000 €). */
    val irsRetidoAno: Long,
    val totalAbonos: Long,
    val totalDescontos: Long,
    val liquido: Long,
    val linhas: List<LinhaReciboImportada>,
    val avisos: List<String>
)

/**
 * Distância, em pontos, dentro da qual o valor de uma etiqueta do cabeçalho é procurado.
 * As linhas do recibo afastam-se 12 a 13 pt umas das outras e a grelha de colunas fica
 * 26 pt abaixo, portanto 20 pt apanha a linha certa sem chegar à seguinte.
 */
private const val JANELA_VALOR_PT = 18f

/** Duas posições a menos de 1 pt de distância em y estão na mesma linha visual. */
private const val TOLERANCIA_LINHA_PT = 1.0f

private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "R01", "SAC", "RCR", "RCRA", "F50", "D02"... */
private val RE_CODIGO = Regex("""^[A-Z]{1,4}\d{0,3}$""")

/** "31/08/2026" */
private val RE_DATA = Regex("""^\d{2}/\d{2}/\d{4}$""")

/** "22.00" · "22" — o número de dias úteis vem com ponto e duas casas decimais. */
private val RE_DIAS_UTEIS = Regex("""^(\d+)""")

/**
 * Lê os recibos de um PDF já extraído em blocos com posição.
 *
 * Devolve um recibo por página que tenha grelha de rubricas; um PDF de uma só via (o caso
 * normal) devolve uma lista de um elemento. Página sem grelha (por exemplo um verso) é
 * ignorada.
 */
fun parseReciboPdf(blocos: List<BlocoTexto>): List<ReciboImportado> =
    blocos.groupBy { it.pagina }
        .toSortedMap()
        .mapNotNull { (pagina, daPagina) -> parsePagina(pagina, daPagina) }

/**
 * Lê uma página. Devolve `null` se a página não tiver grelha de rubricas (não é um
 * recibo) — nesse caso não produz avisos, porque não há nada para avisar.
 */
private fun parsePagina(pagina: Int, blocos: List<BlocoTexto>): ReciboImportado? {
    val cabecalhoTabela = blocos.firstOrNull { it.texto.trim() == "Cód." } ?: return null
    val linhaTotal = blocos.firstOrNull { it.texto.trim() == "Total" } ?: return null
    // O sentido do eixo y depende do extractor: o pdfbox devolve y crescente para baixo,
    // mas um extractor que leia as coordenadas do PDF em bruto tem y crescente para cima.
    // Deduz-se da linha "Total", que vem sempre depois das rubricas.
    val descendente = linhaTotal.y > cabecalhoTabela.y
    val etiquetasDaGrelha = blocos.filter { abs(it.y - cabecalhoTabela.y) <= TOLERANCIA_LINHA_PT }
    val colunas = medirColunas(etiquetasDaGrelha) ?: return null

    val avisos = mutableListOf<String>()

    // A linha das etiquetas do cabeçalho: onde está o "Data Fecho" (e o "Vencimento", o
    // "Venc. / Hora", o "N.º Dias Úteis" e o "Período").
    val yCabecalho = blocos.firstOrNull { it.texto.trim() == "Data Fecho" }?.y

    val periodo = valorDe(blocos, "Período", yCabecalho)?.texto?.trim()
    val dataFecho = valorDe(blocos, "Data Fecho", yCabecalho)?.texto?.let { parseData(it) }
    if (dataFecho == null) avisos.add("Cabeçalho sem \"Data Fecho\" legível")

    val vencimentoBase = valorDe(blocos, "Vencimento", yCabecalho)?.texto?.let { dinheiroDe(it) } ?: 0L
    val vencimentoHora = valorDe(blocos, "Venc. / Hora", yCabecalho)?.texto?.let { dinheiroDe(it) } ?: 0L
    val numDiasUteis = valorDe(blocos, "N.º Dias Úteis", yCabecalho)?.texto?.let { diasDe(it) }

    // O "IRS Retido" do cabeçalho está noutra linha (a do bloco acumulado do ano), mas
    // só existe uma vez na página, portanto não precisa de âncora.
    val irsRetidoAno = valorDe(blocos, "IRS Retido", null)?.texto?.let { dinheiroDe(it) }
    if (irsRetidoAno == null) {
        avisos.add("Cabeçalho sem \"IRS Retido\" legível: valor não importado")
    }

    // O mês vem do "Período" (o nome do mês) com o ano da data de fecho — e não da data
    // de fecho directamente: nas páginas de continuação de um recibo com várias folhas, a
    // data de fecho é a da última linha (a 2.ª página do recibo de Março traz
    // 11/02/2026), mas o "Período" continua a dizer o mês do recibo.
    val mesReferencia = mesReferenciaDe(periodo, dataFecho)
    if (mesReferencia == null) avisos.add("Cabeçalho sem mês reconhecível")

    val linhas = lerLinhas(blocos, cabecalhoTabela.y, linhaTotal.y, colunas, descendente, avisos)

    val numerosDoTotal = numerosNaLinhaDe(blocos, linhaTotal)
    val totalAbonos: Long
    val totalDescontos: Long
    if (numerosDoTotal.size >= 2) {
        // Na linha "Total" o valor dos abonos está na coluna Importância (mais à
        // esquerda) e o dos descontos na coluna Descontos (mais à direita).
        totalAbonos = dinheiroDe(numerosDoTotal.first().texto) ?: 0L
        totalDescontos = dinheiroDe(numerosDoTotal.last().texto) ?: 0L
    } else {
        totalAbonos = 0L
        totalDescontos = 0L
        avisos.add("Linha \"Total\" sem os dois totais (abonos e descontos)")
    }

    val liquido = blocoTotalPago(blocos)?.let { dinheiroDe(it.texto) }
    if (liquido == null) avisos.add("Sem \"Total Pago ( EUR )\" legível")

    return ReciboImportado(
        pagina = pagina,
        mesReferencia = mesReferencia,
        dataFecho = dataFecho,
        vencimentoBase = vencimentoBase,
        vencimentoHora = vencimentoHora,
        numDiasUteis = numDiasUteis,
        irsRetidoAno = irsRetidoAno ?: 0L,
        totalAbonos = totalAbonos,
        totalDescontos = totalDescontos,
        liquido = liquido ?: 0L,
        linhas = linhas,
        avisos = avisos
    )
}

/**
 * As fronteiras das colunas da grelha de rubricas, medidas a partir das próprias
 * etiquetas do cabeçalho (`Data`, `Abonos`, `Quantidade`, `Preço Unitário`) em vez de
 * constantes absolutas: assim o parser não depende de o extractor devolver o x na mesma
 * escala — só da ordem relativa das colunas, que vem do PDF.
 */
private data class Colunas(
    /** Acima deste x já não é o código, é a descrição. */
    val codigoMax: Float,
    val descricaoMin: Float,
    val descricaoMax: Float,
    /** A partir deste x um número é uma quantidade/preço/valor, não código nem data. */
    val numeroMin: Float,
    /** Abaixo deste x um número é "Quantidade"; acima é "Preço Unitário". */
    val quantidadeMax: Float
)

/** Mede as colunas a partir das etiquetas da linha de cabeçalho da grelha. */
private fun medirColunas(etiquetas: List<BlocoTexto>): Colunas? {
    fun x(etiqueta: String) = etiquetas.firstOrNull { it.texto.trim() == etiqueta }?.x
    val data = x("Data") ?: return null
    val abonos = x("Abonos") ?: return null
    val quantidade = x("Quantidade") ?: return null
    val preco = x("Preço Unitário") ?: return null
    return Colunas(
        codigoMax = (data + abonos) / 2f,
        descricaoMin = (data + abonos) / 2f,
        descricaoMax = (abonos + quantidade) / 2f,
        numeroMin = (abonos + quantidade) / 2f,
        quantidadeMax = (quantidade + preco) / 2f
    )
}


/**
 * Lê as linhas de rubrica: tudo o que está na grelha, entre a linha de cabeçalho ("Cód.")
 * e a linha "Total".
 *
 * Cada código (`R01`, `SAC`, `D02`, ...) na coluna da esquerda abre uma linha nova; os
 * blocos que se seguem — descrição, data, quantidade, valores — pertencem-lhe, até
 * aparecer o próximo código. A ordem no recibo é preservada.
 */
private fun lerLinhas(
    blocos: List<BlocoTexto>,
    yCabecalho: Float,
    yTotal: Float,
    colunas: Colunas,
    descendente: Boolean,
    avisos: MutableList<String>
): List<LinhaReciboImportada> {
    val primeira = if (descendente) yCabecalho else yTotal
    val ultima = if (descendente) yTotal else yCabecalho
    // A ordem das linhas segue o sentido do eixo, mas dentro de uma linha lê-se sempre da
    // esquerda para a direita — é aí que está o código.
    val ordem = if (descendente) {
        compareBy<BlocoTexto>({ it.y }, { it.x })
    } else {
        compareByDescending<BlocoTexto> { it.y }.thenBy { it.x }
    }
    val daGrelha = blocos
        .filter { it.y > primeira + 0.5f && it.y < ultima - 0.5f && it.texto.isNotBlank() }
        .sortedWith(ordem)

    val linhas = mutableListOf<LinhaReciboImportada>()
    var codigo: String? = null
    var grupo = mutableListOf<BlocoTexto>()

    fun fechar() {
        val cod = codigo ?: return
        val mapeamento = mapearCodigoRecibo(cod)

        val numeros = grupo
            .filter { it.x >= colunas.numeroMin }
            .mapNotNull { bloco -> dinheiroDe(bloco.texto)?.let { bloco.x to it } }
        // O valor da linha é o número mais à direita: as importâncias dos abonos ficam na
        // coluna "Importância" e os descontos na coluna "Descontos", ambas à direita das
        // colunas de quantidade e preço unitário.
        val valor = numeros.maxByOrNull { it.first }?.second ?: 0L
        val quantidade = numeros.firstOrNull { it.first < colunas.quantidadeMax }?.second

        val descricao = grupo.firstOrNull {
            it.x >= colunas.descricaoMin && it.x < colunas.descricaoMax &&
                dinheiroDe(it.texto) == null && !RE_DATA.matches(it.texto.trim())
        }?.texto?.trim().orEmpty()

        val data = grupo.firstOrNull { RE_DATA.matches(it.texto.trim()) }?.texto?.let { parseData(it) }

        mapeamento.aviso?.let { avisos.add(it) }

        linhas.add(
            LinhaReciboImportada(
                codigoRecibo = cod,
                descricao = descricao,
                codigoInterno = mapeamento.codigoInterno,
                natureza = mapeamento.natureza,
                valor = valor,
                quantidade = quantidade,
                data = data,
                ordem = linhas.size + 1,
                aviso = mapeamento.aviso
            )
        )
    }

    for (bloco in daGrelha) {
        val texto = bloco.texto.trim()
        if (bloco.x < colunas.codigoMax && RE_CODIGO.matches(texto)) {
            fechar()
            codigo = texto
            grupo = mutableListOf(bloco)
        } else if (codigo != null) {
            grupo.add(bloco)
        }
    }
    fechar()

    return linhas
}

/**
 * O valor de uma etiqueta do cabeçalho: o bloco **na linha de cima ou de baixo** da
 * etiqueta (não importa qual: depende do sentido do eixo y do extractor) **no x mais
 * próximo** dela.
 *
 * No cabeçalho do recibo a etiqueta e o valor estão em colunas, em linhas vizinhas — mas
 * são desenhados separadamente, e em todas as vias da PRIMAVERA/Cegid os valores saem da
 * direita para a esquerda. Procurar "o número a seguir" daria o valor errado; procurar o
 * mais próximo em x dá o certo, e é o que distingue o IRS retido (x≈477) do Total de
 * remunerações (x≈539), que estão na mesma linha.
 *
 * [yCabecalho] é o y da linha de etiquetas do cabeçalho e serve para desambiguar etiquetas
 * repetidas na página — o "Vencimento" do cabeçalho e o "Vencimento" que descreve a linha
 * R01. `null` quando só há uma.
 */
private fun valorDe(blocos: List<BlocoTexto>, etiqueta: String, yCabecalho: Float?): BlocoTexto? {
    val alvo = etiqueta.trim()
    val candidatos = blocos.filter { it.texto.trim() == alvo }
    val posicao = if (yCabecalho == null) {
        blocos.firstOrNull { it.texto.trim() == alvo }
    } else {
        candidatos.minByOrNull { abs(it.y - yCabecalho) }
    } ?: return null
    return blocos
        .filter { it !== posicao && abs(it.y - posicao.y) <= JANELA_VALOR_PT }
        .minByOrNull { abs(it.x - posicao.x) }
}

/** Os números desenhados na mesma linha visual de [referencia], à direita dela, por x. */
private fun numerosNaLinhaDe(blocos: List<BlocoTexto>, referencia: BlocoTexto): List<BlocoTexto> =
    blocos
        .filter { abs(it.y - referencia.y) <= TOLERANCIA_LINHA_PT && it.x > referencia.x + 0.5f }
        .filter { dinheiroDe(it.texto) != null }
        .sortedBy { it.x }

/** O valor de "Total Pago ( EUR )": o número mais à direita da mesma linha. */
private fun blocoTotalPago(blocos: List<BlocoTexto>): BlocoTexto? {
    val etiqueta = blocos.filter { it.texto.contains("Total Pago") }.minByOrNull { it.y } ?: return null
    return blocos
        .filter {
            abs(it.y - etiqueta.y) <= TOLERANCIA_LINHA_PT && it.x > etiqueta.x &&
                dinheiroDe(it.texto) != null
        }
        .maxByOrNull { it.x }
}

/**
 * Texto do recibo -> unidades de 1/10000 €. `null` se não for dinheiro válido.
 * Reutiliza o `deTexto` do domínio (exato, sem `Double`): aceita "1 137,98", "1137,98" e
 * "14.36", e recusa tudo o que tenha letras ou mais de 4 casas decimais.
 */
private fun dinheiroDe(texto: String): Long? =
    try { deTexto(texto).numerador } catch (_: Exception) { null }

/** " 22.00" -> 22. O número de dias úteis vem com ponto e duas casas. */
private fun diasDe(texto: String): Int? =
    RE_DIAS_UTEIS.find(texto.trim())?.groupValues?.get(1)?.toIntOrNull()

/** "31/08/2026" -> epochDay. `null` se não for data válida. */
private fun parseData(texto: String): Long? =
    try { LocalDate.parse(texto.trim(), FORMATO_DATA).toEpochDay() } catch (_: Exception) { null }

/** Os meses em português, pela ordem do calendário, para traduzir o "Período". */
private val MESES = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
)

/** "Março" -> 3. `null` se o texto não for um mês em português. */
private fun numeroDoMes(nome: String): Int? {
    val alvo = nome.trim().lowercase(Locale("pt", "PT"))
    // Alguns recibos escrevem "Marco", sem cedilha.
    val indice = MESES.indexOf(if (alvo == "marco") "março" else alvo)
    return if (indice < 0) null else indice + 1
}

/**
 * O mês do recibo: o nome do "Período" com o ano da data de fecho. Sem "Período" (ou com
 * um nome que não seja um mês), cai na própria data de fecho.
 */
private fun mesReferenciaDe(periodo: String?, dataFecho: Long?): YearMonth? {
    val data = dataFecho?.let { LocalDate.ofEpochDay(it) }
    val mes = periodo?.let { numeroDoMes(it) }
    return when {
        mes != null && data != null -> YearMonth.of(data.year, mes)
        data != null -> YearMonth.from(data)
        else -> null
    }
}
