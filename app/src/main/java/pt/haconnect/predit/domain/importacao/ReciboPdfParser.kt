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
    /**
     * A empresa que emitiu o recibo, lida do rodapé ("Licença de: ..."); `null` quando a página
     * não a traz. É a chave do mapeamento configurável do utilizador (Fase 28b).
     */
    val empresaEmissora: String?,
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
    val avisos: List<String>,
    /** Códigos que não estão no mapa do utilizador nem no hardcoded (Fase 28b). */
    val codigosDesconhecidos: List<String>
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

/** "R01", "SAC", "RCR", "RCRA", "F50", "D02", "F106I" (sufixo alfabético na Fundação)... */
private val RE_CODIGO = Regex("""^[A-Z]{1,4}\d{0,3}[A-Z]?$""")

/**
 * "31/08/2026" · "30-04-2016" — a PRIMAVERA escreve a data de fecho com barras na ICTS e com
 * hífenes na PSG. O separador entra na regex (e o [parseData] normaliza-o) para as datas
 * continuarem a ser reconhecidas como data: sem isto, "30-04-2016" caía na coluna da
 * descrição e a data da linha perdia-se.
 */
private val RE_DATA = Regex("""^\d{2}[/-]\d{2}[/-]\d{4}$""")

/** "22.00" · "22" — o número de dias úteis vem com ponto e duas casas decimais. */
private val RE_DIAS_UTEIS = Regex("""^(\d+)""")

/**
 * Lê os recibos de um PDF já extraído em blocos com posição.
 *
 * Devolve um recibo por página que tenha grelha de rubricas; um PDF de uma só via (o caso
 * normal) devolve uma lista de um elemento. Página sem grelha (por exemplo um verso) é
 * ignorada.
 *
 * [mapeamentosPorEmpresa] é o mapa configurável do utilizador — empresa do rodapé ->
 * (código do recibo -> código interno). Só se aplica à empresa detetada na página: o mesmo
 * código pode valer coisas diferentes em empresas diferentes.
 */
fun parseReciboPdf(
    blocos: List<BlocoTexto>,
    mapeamentosPorEmpresa: Map<String, Map<String, String>> = emptyMap()
): List<ReciboImportado> =
    blocos.groupBy { it.pagina }
        .toSortedMap()
        .mapNotNull { (pagina, daPagina) ->
            parsePagina(pagina, daPagina, mapeamentosPorEmpresa)
        }

/**
 * Lê uma página. Devolve `null` se a página não tiver grelha de rubricas (não é um
 * recibo) — nesse caso não produz avisos, porque não há nada para avisar.
 */
private fun parsePagina(
    pagina: Int,
    blocos: List<BlocoTexto>,
    mapeamentosPorEmpresa: Map<String, Map<String, String>>
): ReciboImportado? {
    val cabecalhoTabela = blocos.firstOrNull { it.texto.trim() == "Cód." } ?: return null
    val linhaTotal = blocos.firstOrNull { it.texto.trim() == "Total" } ?: return null
    // O sentido do eixo y depende do extractor: o pdfbox devolve y crescente para baixo,
    // mas um extractor que leia as coordenadas do PDF em bruto tem y crescente para cima.
    // Deduz-se da linha "Total", que vem sempre depois das rubricas.
    val descendente = linhaTotal.y > cabecalhoTabela.y
    val etiquetasDaGrelha = blocos.filter { abs(it.y - cabecalhoTabela.y) <= TOLERANCIA_LINHA_PT }
    val colunas = medirColunas(etiquetasDaGrelha) ?: return null

    val avisos = mutableListOf<String>()
    // Fase 28b: a empresa sai do rodapé da página e escolhe o mapa do utilizador; sem empresa
    // detetada só o hardcoded se aplica. Os códigos que nenhum mapa conhece ficam registados
    // para a UI os poder oferecer para mapeamento.
    val empresaEmissora = extrairEmpresa(blocos)
    val mapaDaEmpresa = mapaDoUtilizador(mapeamentosPorEmpresa, empresaEmissora)
    val codigosDesconhecidos = mutableListOf<String>()

    // A linha das etiquetas do cabeçalho: onde está o "Data Fecho" (e o "Vencimento", o
    // "Venc. / Hora", o "N.º Dias Úteis" e o "Período").
    val yCabecalho = blocos.firstOrNull { it.texto.trim() == "Data Fecho" }?.y

    // Cada campo diz ao `valorDe` que tipo de texto aceita como valor: na Fundação as
    // etiquetas do cabeçalho empilham-se na mesma coluna e, sem isto, o "Data Fecho" lia a
    // etiqueta da linha de cima (Δx = 0) em vez da data desenhada ao lado.
    val periodo = valorDe(blocos, "Período", yCabecalho) { numeroDoMes(it) != null }
        ?.texto?.trim()
    val dataFecho = valorDe(blocos, "Data Fecho", yCabecalho) { parseData(it) != null }
        ?.texto?.let { parseData(it) }
    if (dataFecho == null) avisos.add("Cabeçalho sem \"Data Fecho\" legível")

    val vencimentoBase = valorDe(blocos, "Vencimento", yCabecalho) { dinheiroDe(it) != null }
        ?.texto?.let { dinheiroDe(it) } ?: 0L
    val vencimentoHora = valorDe(blocos, "Venc. / Hora", yCabecalho) { dinheiroDe(it) != null }
        ?.texto?.let { dinheiroDe(it) } ?: 0L
    // A Fundação escreve "N. Dias Mês:" onde a PRIMAVERA/ICTS escreve "N.º Dias Úteis".
    val numDiasUteis =
        valorDe(blocos, "N.º Dias Úteis", yCabecalho) { diasDe(it) != null }
            ?.texto?.let { diasDe(it) }
            ?: valorDe(blocos, "N. Dias Mês:", yCabecalho) { diasDe(it) != null }
                ?.texto?.let { diasDe(it) }

    // O "IRS Retido" do cabeçalho está noutra linha (a do bloco acumulado do ano), mas
    // só existe uma vez na página, portanto não precisa de âncora.
    val irsRetidoAno = valorDe(blocos, "IRS Retido", null) { dinheiroDe(it) != null }
        ?.texto?.let { dinheiroDe(it) }
    if (irsRetidoAno == null) {
        avisos.add("Cabeçalho sem \"IRS Retido\" legível: valor não importado")
    }

    // O mês vem do "Período" (o nome do mês) com o ano da data de fecho — e não da data
    // de fecho directamente: nas páginas de continuação de um recibo com várias folhas, a
    // data de fecho é a da última linha (a 2.ª página do recibo de Março traz
    // 11/02/2026), mas o "Período" continua a dizer o mês do recibo.
    // O `?:` é rede de segurança: `mesReferenciaDe` já cai na data de fecho quando o
    // "Período" não é um mês conhecido, e só devolve `null` sem nenhum dos dois.
    val mesReferencia = mesReferenciaDe(periodo, dataFecho)
        ?: dataFecho?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }
    if (mesReferencia == null) avisos.add("Cabeçalho sem mês reconhecível")

    val linhas = lerLinhas(
        blocos, cabecalhoTabela.y, linhaTotal.y, colunas, descendente,
        mapaDaEmpresa, avisos, codigosDesconhecidos
    )

    // As linhas de baixa (F02 na ICTS/PSG, F35 na Fundação) são informativas e não descontam
    // nada do vencimento, portanto o valor do mês pode vir inflacionado. É aviso, não erro: o
    // recibo é importado na mesma.
    val temBaixa = linhas.any { it.codigoRecibo == "F02" || it.codigoRecibo == "F35" }
    if (temBaixa) {
        avisos.add("Atenção: este recibo tem dias de baixa. O vencimento pode estar inflacionado.")
    }

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
        empresaEmissora = empresaEmissora,
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
        avisos = avisos,
        codigosDesconhecidos = codigosDesconhecidos
    )
}

/** A etiqueta do rodapé que identifica a empresa emissora do recibo. */
private const val ETIQUETA_LICENCA = "Licença de:"

/**
 * A empresa que emitiu o recibo, lida do rodapé.
 *
 * Nos recibos PRIMAVERA o nome vem partido em vários blocos na mesma linha visual
 * (`© PRIMAVERA BSS`, `/ Licença de:`, `ICTS PORTUGAL`, `,`, `SA`): o nome é o que está à
 * direita da etiqueta, lido por x e com os espaços antes da pontuação limpos — daqui sai
 * "ICTS PORTUGAL, SA". O nome também pode vir no próprio bloco da etiqueta
 * ("Licença de: SECURITAS"), e esse caso está coberto.
 *
 * Devolve `null` se a página não trouxer a etiqueta (não é um recibo PRIMAVERA, ou o
 * extractor mudou).
 */
private fun extrairEmpresa(blocos: List<BlocoTexto>): String? {
    val etiqueta = blocos.firstOrNull { it.texto.contains(ETIQUETA_LICENCA) } ?: return null

    val noProprioBloco = etiqueta.texto.substringAfter(ETIQUETA_LICENCA).trim()
    val aDireita = blocos
        .filter {
            it !== etiqueta && abs(it.y - etiqueta.y) <= TOLERANCIA_LINHA_PT && it.x > etiqueta.x
        }
        .sortedBy { it.x }
        .map { it.texto.trim() }
        .filter { it.isNotEmpty() }

    val texto = (listOf(noProprioBloco) + aDireita)
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""\s+([,.;])"""), "\$1")
        .trim()

    return texto.ifEmpty { null }
}

/**
 * O mapa do utilizador para a empresa detetada na página.
 *
 * A chave procura-se tal e qual e, se não houver, pela forma normalizada ([chaveDaEmpresa]).
 * É deliberado: o nome do rodapé sai de um recibo para o outro com e sem a forma societária
 * ("ICTS PORTUGAL" no Cegid, "ICTS PORTUGAL, SA" no PRIMAVERA) e a chave gravada na BD pode
 * ter sido escrita à mão de qualquer das maneiras. Sem isto, o mesmo emissor ficava com dois
 * mapas — ou com nenhum.
 */
private fun mapaDoUtilizador(
    mapeamentosPorEmpresa: Map<String, Map<String, String>>,
    empresa: String?
): Map<String, String> {
    if (empresa == null) return emptyMap()
    mapeamentosPorEmpresa[empresa]?.let { return it }
    val chave = chaveDaEmpresa(empresa)
    return mapeamentosPorEmpresa.entries
        .firstOrNull { chaveDaEmpresa(it.key) == chave }
        ?.value
        ?: emptyMap()
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

/**
 * Mede as colunas a partir das etiquetas da linha de cabeçalho da grelha.
 *
 * Os rótulos mudam de fornecedor para fornecedor, mas a estrutura não: a PRIMAVERA/ICTS
 * escreve `Abonos`/`Quantidade`/`Preço Unitário` e a Fundação escreve
 * `Descrição`/`Faltas`/`Remunerações`. A geometria é a mesma — as fronteiras ficam sempre
 * a meio caminho entre etiquetas vizinhas, nunca em constantes absolutas.
 */
private fun medirColunas(etiquetas: List<BlocoTexto>): Colunas? {
    fun x(etiqueta: String) = etiquetas.firstOrNull { it.texto.trim() == etiqueta }?.x

    val data = x("Data") ?: return null

    // ICTS chama-lhe "Abonos"; a Fundação chama-lhe "Descrição".
    val descricao = x("Abonos") ?: x("Descrição") ?: return null

    // ICTS: Quantidade/Preço Unitário. Fundação: Faltas/Remunerações.
    val quantidade = x("Quantidade") ?: x("Faltas")
    val preco = x("Preço Unitário") ?: x("Remunerações")

    val numeroMin =
        if (quantidade != null) (descricao + quantidade) / 2f else (descricao + (preco ?: data)) / 2f
    val quantidadeMax =
        if (quantidade != null && preco != null) (quantidade + preco) / 2f else Float.MAX_VALUE

    return Colunas(
        codigoMax = (data + descricao) / 2f,
        descricaoMin = (data + descricao) / 2f,
        descricaoMax = if (quantidade != null) (descricao + quantidade) / 2f else Float.MAX_VALUE,
        numeroMin = numeroMin,
        quantidadeMax = quantidadeMax
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
    mapaDaEmpresa: Map<String, String>,
    avisos: MutableList<String>,
    codigosDesconhecidos: MutableList<String>
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
        val mapeamento = mapearCodigoRecibo(cod, mapaDaEmpresa)

        val numeros = grupo
            .filter { it.x >= colunas.numeroMin }
            .mapNotNull { bloco -> dinheiroDe(bloco.texto)?.let { bloco.x to it } }
        // O valor da linha é o número mais à direita: as importâncias dos abonos ficam na
        // coluna "Importância" e os descontos na coluna "Descontos", ambas à direita das
        // colunas de quantidade e preço unitário.
        val valor = numeros.maxByOrNull { it.first }?.second ?: 0L
        val quantidade = numeros.firstOrNull { it.first < colunas.quantidadeMax }?.second

        // 28g: a descrição de uma linha chega partida em vários blocos ("Baixa Assist" + ". " +
        // "Familia" + "<12 Anos"). Ficávamos com o primeiro e o resto perdia-se. Juntam-se os
        // blocos da coluna da descrição, na ordem da esquerda para a direita (já vem do
        // `sortedWith(ordem)`), e colapsam-se os espaços.
        //
        // 28h: o grupo pode apanhar blocos que já pertencem à linha seguinte — na Fundação a
        // descrição é desenhada ~1 pt acima do código, por isso o corte no código seguinte
        // atribui-a à linha errada. Só se aceitam blocos a menos de 15 pt do código (o `grupo`
        // começa no bloco do código): a descrição real fica a ~1 pt, as linhas seguintes a ~10.
        val descricao = grupo
            .filter {
                it.x >= colunas.descricaoMin && it.x < colunas.descricaoMax &&
                    dinheiroDe(it.texto) == null && !RE_DATA.matches(it.texto.trim()) &&
                    abs(it.y - grupo.first().y) <= 15f // tolerância vertical
            }
            .joinToString(" ") { it.texto.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()

        val data = grupo.firstOrNull { RE_DATA.matches(it.texto.trim()) }?.texto?.let { parseData(it) }

        mapeamento.aviso?.let { avisos.add(it) }
        // Fase 28b: desconhecido é diferente de informativo — só o primeiro é que a UI deve
        // propor mapear. O código já vem normalizado de mapearCodigoRecibo.
        if (mapeamento.origem == OrigemMapeamento.DESCONHECIDO &&
            mapeamento.codigoRecibo !in codigosDesconhecidos
        ) {
            codigosDesconhecidos.add(mapeamento.codigoRecibo)
        }

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
 *
 * [aceita] diz que tipo de texto pode ser o valor (data, dinheiro, mês, dias). É o que
 * resolve a Fundação: lá as etiquetas do cabeçalho empilham-se na **mesma coluna** a ~11 pt
 * de distância, e sem o filtro o "Data Fecho" lia o "Período" da linha de cima — que ganha
 * por estar a Δx = 0. Com o filtro, só candidatos do tipo certo competem entre si e a
 * geometria continua a decidir. Em PRIMAVERA/Cegid o filtro é inócuo (o valor já era o
 * candidato mais próximo em x) e serve de rede: nunca apanha uma etiqueta vizinha.
 */
private fun valorDe(
    blocos: List<BlocoTexto>,
    etiqueta: String,
    yCabecalho: Float?,
    aceita: (String) -> Boolean = { true }
): BlocoTexto? {
    val alvo = etiqueta.trim()
    val candidatos = blocos.filter { it.texto.trim() == alvo }
    val posicao = if (yCabecalho == null) {
        blocos.firstOrNull { it.texto.trim() == alvo }
    } else {
        candidatos.minByOrNull { abs(it.y - yCabecalho) }
    } ?: return null
    return blocos
        .filter { it !== posicao && abs(it.y - posicao.y) <= JANELA_VALOR_PT && aceita(it.texto) }
        // Δx decide; em empate, a linha mais próxima. Na Fundação os valores estão na
        // mesma linha das etiquetas, e é isso que separa " 1 000,00" de " 5,77".
        .minWithOrNull(
            compareBy<BlocoTexto>({ abs(it.x - posicao.x) }, { abs(it.y - posicao.y) })
        )
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

/**
 * "31/08/2026" -> epochDay. `null` se não for data válida.
 *
 * O hífen é normalizado para barra antes do parse: a PSG escreve "30-04-2016" e o formato do
 * recibo é sempre dia/mês/ano. Só se troca o separador — quem receber uma data ISO
 * ("2016-04-30") continua a ver `null`, como antes.
 */
private fun parseData(texto: String): Long? =
    try {
        val normalizado = texto.trim().replace('-', '/')
        LocalDate.parse(normalizado, FORMATO_DATA).toEpochDay()
    } catch (_: Exception) {
        null
    }

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
