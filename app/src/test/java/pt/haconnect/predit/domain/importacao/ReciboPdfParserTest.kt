package pt.haconnect.predit.domain.importacao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import java.time.LocalDate
import java.time.YearMonth

/**
 * Testa o parser dos recibos contra os recibos reais.
 *
 * Os fixtures são a saída do `PdfBoxExtractor` (uma linha por bloco: página, x, y, texto)
 * de cada PDF de origem, guardada em `src/test/resources/fixtures/`. Não é texto escrito
 * à mão: foi o extractor a lê-lo dos PDFs, para o teste exercitar a mesma geometria que a
 * app vai encontrar em produção.
 */
class ReciboPdfParserTest {

    private fun blocosDe(fixture: String): List<BlocoTexto> =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$fixture")) {
            "fixture em falta: $fixture"
        }.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { linha ->
                val campos = linha.split('\t')
                BlocoTexto(
                    pagina = campos[0].toInt(),
                    x = campos[1].toFloat(),
                    y = campos[2].toFloat(),
                    texto = campos[3]
                )
            }

    private fun ler(fixture: String): List<ReciboImportado> = parseReciboPdf(blocosDe(fixture))

    private fun List<ReciboImportado>.comDataFecho(epochDay: Long): ReciboImportado =
        checkNotNull(firstOrNull { it.dataFecho == epochDay }) {
            "não há recibo com data de fecho ${LocalDate.ofEpochDay(epochDay)}"
        }

    private fun ReciboImportado.linha(codigo: String): LinhaReciboImportada =
        checkNotNull(linhas.firstOrNull { it.codigoRecibo == codigo }) {
            "o recibo não tem a linha $codigo (tem: ${linhas.map { it.codigoRecibo }})"
        }

    private fun ReciboImportado.valorDe(codigo: String): Long = linha(codigo).valor

    private fun dia(ano: Int, mes: Int, dia: Int): Long = LocalDate.of(ano, mes, dia).toEpochDay()

    // ---------------------------------------------------------------- Agosto 2026

    @Test
    fun `Agosto 2026 - cabecalho e totais`() {
        val recibos = ler("recibo-agosto-2026.txt")

        assertEquals(1, recibos.size)
        val recibo = recibos.first()

        assertEquals(YearMonth.of(2026, 8), recibo.mesReferencia)
        assertEquals(dia(2026, 8, 31), recibo.dataFecho)
        assertEquals(11_379_800L, recibo.vencimentoBase)   // 1 137,98 €
        assertEquals(65_700L, recibo.vencimentoHora)       // 6,57 €/h
        assertEquals(22, recibo.numDiasUteis)
        assertEquals(1_580_000L, recibo.irsRetidoAno)      // 158,00 € acumulado no ano
        assertEquals(13_627_700L, recibo.totalAbonos)      // 1 362,77 €
        assertEquals(1_932_900L, recibo.totalDescontos)    // 193,29 €
        assertEquals(11_694_800L, recibo.liquido)          // 1 169,48 €
        assertTrue("Agosto não devia ter avisos: ${recibo.avisos}", recibo.avisos.isEmpty())
    }

    @Test
    fun `Agosto 2026 - linhas de abono e de desconto`() {
        val recibo = ler("recibo-agosto-2026.txt").first()

        with(recibo) {
            assertEquals("VENC", linha("R01").codigoInterno)
            assertEquals(11_379_800L, valorDe("R01"))          // 1 137,98 €
            assertEquals("SUP_TRAN", linha("R14").codigoInterno)
            assertEquals(520_900L, valorDe("R14"))             // 52,09 €
            assertEquals(10_000L, linha("R14").quantidade)     // 1,00
            assertEquals("SUP_ALIM", linha("SAC").codigoInterno)
            assertEquals(1_727_000L, valorDe("SAC"))           // 172,70 €
            assertEquals(220_000L, linha("SAC").quantidade)    // 22,00

            assertEquals("D01", linha("D01").codigoInterno)
            assertEquals(1_309_100L, valorDe("D01"))           // 130,91 €
            assertEquals("D02", linha("D02").codigoInterno)
            assertEquals(510_000L, valorDe("D02"))             // 51,00 € do mês
            assertEquals("D04", linha("D04").codigoInterno)
            assertEquals(113_800L, valorDe("D04"))             // 11,38 €
        }

        // O IRS do cabeçalho não é o IRS do mês: 158,00 € é o acumulado do ano. A
        // aritmética do utilizador (204 − 158 = 46) fecha com o D02 de Setembro.
        assertEquals(510_000L, recibo.valorDe("D02"))
    }

    // -------------------------------------------------------------- Setembro 2026

    @Test
    fun `Setembro 2026 - cabecalho, totais e R01 proporcional`() {
        val recibos = ler("recibo-setembro-2026.txt")

        assertEquals(1, recibos.size)
        val recibo = recibos.first()

        assertEquals(YearMonth.of(2026, 9), recibo.mesReferencia)
        assertEquals(dia(2026, 9, 30), recibo.dataFecho)
        assertEquals(11_379_800L, recibo.vencimentoBase)   // base do contrato: 1 137,98 €
        assertEquals(65_700L, recibo.vencimentoHora)
        assertEquals(22, recibo.numDiasUteis)
        assertEquals(2_040_000L, recibo.irsRetidoAno)      // 204,00 € acumulado no ano
        assertEquals(12_383_400L, recibo.totalAbonos)      // 1 238,34 €
        assertEquals(1_858_000L, recibo.totalDescontos)    // 185,80 €
        assertEquals(10_525_400L, recibo.liquido)          // 1 052,54 €

        // O vencimento pago (1 134,70 €) é menor do que a base (1 137,98 €): a diferença
        // de 3,28 € bate com a ausência não remunerada parcial (F74: 0,50 h × 6,57 €/h).
        assertEquals(11_347_000L, recibo.valorDe("R01"))
        assertEquals(329_900L, recibo.valorDe("R14"))      // 32,99 €
        assertEquals(706_500L, recibo.valorDe("SAC"))      // 70,65 €
        assertEquals(1_284_500L, recibo.valorDe("D01"))    // 128,45 €
        assertEquals(460_000L, recibo.valorDe("D02"))      // 46,00 €
        assertEquals(113_500L, recibo.valorDe("D04"))      // 11,35 €
    }

    @Test
    fun `Setembro 2026 - ferias e ausencia parcial sao informativas, com aviso`() {
        val recibo = ler("recibo-setembro-2026.txt").first()

        val ferias = recibo.linhas.filter { it.codigoRecibo == "F50" }
        assertEquals(11, ferias.size)
        assertTrue(ferias.all { it.codigoInterno == null })
        // O valor é o Preço Unitário (o número mais à direita da linha): as linhas informativas
        // não têm Importância, portanto este 1,74 € não entra no Total nem na importação.
        assertTrue(ferias.all { it.valor == 17_400L })        // 1,74 € por dia
        assertTrue(ferias.all { it.data != null })            // cada dia traz a sua data
        assertTrue("uma linha de férias tem de trazer aviso", ferias.all { it.aviso != null })

        val ausencia = recibo.linha("F74")
        assertNull(ausencia.codigoInterno)                    // informativa: não escreve valor
        assertEquals(32_900L, ausencia.valor)                 // 3,29 €
        assertEquals(5_000L, ausencia.quantidade)             // 0,50
        assertEquals(dia(2026, 8, 6), ausencia.data)

        // Os avisos das linhas de férias/ausência sobem ao recibo.
        assertTrue(recibo.avisos.any { it.contains("F50") })
        assertTrue(recibo.avisos.any { it.contains("F74") })
    }

    // ---------------------------------------------------------------- Julho 2026

    @Test
    fun `Julho 2026 - duas paginas dao dois recibos do mesmo mes`() {
        val recibos = ler("recibo-julho-2026.txt")

        assertEquals(2, recibos.size)
        assertTrue(recibos.all { it.mesReferencia == YearMonth.of(2026, 7) })

        val principal = recibos.comDataFecho(dia(2026, 7, 31))
        assertEquals(1_070_000L, principal.irsRetidoAno)      // 107,00 € acumulado
        assertEquals(11_000_500L, principal.valorDe("R01"))   // 1 100,05 €
        assertEquals(191_000L, principal.valorDe("R14"))      // 19,10 €
        assertEquals(1_648_500L, principal.linha("RCR").valor) // 164,85 €
        assertEquals("SUP_ALIM", principal.linha("RCR").codigoInterno)
        assertEquals(1_300L, principal.valorDe("R118"))       // 0,13 €
        assertEquals("OUTROS", principal.linha("R118").codigoInterno)
        assertEquals(1_231_200L, principal.valorDe("D01"))
        assertEquals(480_000L, principal.valorDe("D02"))
        assertEquals(110_000L, principal.valorDe("D04"))
        assertEquals(18, principal.linhas.count { it.codigoRecibo == "F50" })

        val suplemento = recibos.comDataFecho(dia(2026, 7, 21))
        // F40 é informativo (subsídio durante a baixa): traz Quantidade e Preço Unitário, mas
        // nenhuma Importância — os 7,85 € não entram no Total nem na importação.
        assertNull(suplemento.linha("F40").codigoInterno)
        assertEquals(78_500L, suplemento.valorDe("F40"))      // 7,85 € (preço unitário)
        assertEquals("FERIAS", suplemento.linha("R97").codigoInterno)
        assertEquals(8_276_200L, suplemento.valorDe("R97"))   // 827,62 € subsídio de férias
        assertEquals(910_400L, suplemento.valorDe("D01"))
        assertEquals(21_117_500L, suplemento.totalAbonos)     // 2 111,75 €
        assertEquals(2_731_600L, suplemento.totalDescontos)   // 273,16 €
        assertEquals(18_385_900L, suplemento.liquido)         // 1 838,59 €
    }

    // ---------------------------------------------------------------- Março 2026

    @Test
    fun `Marco 2026 - tres paginas, uma por via do mesmo mes`() {
        val recibos = ler("recibo-marco-2026.txt")

        // A lista de ausências de Março transborda para duas páginas seguintes; cada
        // página repete o cabeçalho e tem a sua grelha. O parser devolve as três, todas
        // do mesmo mês — juntá-las (ou escolher uma) é decisão da UI.
        assertEquals(3, recibos.size)
        assertTrue(recibos.all { it.mesReferencia == YearMonth.of(2026, 3) })
        val primeira = recibos.first()
        assertEquals(11_379_800L, primeira.valorDe("R01"))    // 1 137,98 €
        assertEquals(0L, primeira.valorDe("R14"))             // 0,00 € (mês de baixa)
        assertEquals("COMP_BAIXA", primeira.linha("R51").codigoInterno)   // complemento de baixa (isento)
        assertEquals(2_587_000L, primeira.valorDe("R51"))     // 258,70 €
        assertEquals("SUP_ALIM", primeira.linha("RCR").codigoInterno)

        // F02 (baixas) é informativo, como as férias e a ausência parcial: aviso, sem rubrica.
        val baixas = primeira.linhas.filter { it.codigoRecibo == "F02" }
        assertTrue(baixas.isNotEmpty())
        assertTrue(baixas.all { it.codigoInterno == null && it.aviso != null })
        assertEquals(396_700L, baixas.first().valor)          // 39,67 €
    }

    @Test
    fun `Marco 2026 - R01 e informativo quando o mes e de baixa`() {
        val recibos = ler("recibo-marco-2026.txt")
        // O ficheiro de Março tem 3 folhas. A que tem o R01 e a R51 e a 1.a.
        val recibo = recibos.first { it.linhas.any { l -> l.codigoRecibo == "R01" } }

        // 28i: mês de baixa total (F02) pago pelo complemento (R51) — o R01 é só a referência de
        // cálculo do complemento, não entra nos totais. O valor fica, para se ver a referência.
        val r01 = recibo.linhas.first { it.codigoRecibo == "R01" }
        assertNull(r01.codigoInterno)
        assertNotEquals(null, r01.aviso)
        assertEquals(11_379_800L, r01.valor)                  // 1 137,98 € (referência)

        val r51 = recibo.linhas.first { it.codigoRecibo == "R51" }
        assertEquals("COMP_BAIXA", r51.codigoInterno)         // o pagamento do mês é este

        // O aviso de "vencimento inflacionado" sai de cena: já não há vencimento a escrever.
        assertTrue(
            "Devia avisar da baixa total: ${recibo.avisos}",
            recibo.avisos.any { it.contains("baixa total") }
        )
    }

    // ----------------------------------------------------- Agosto 2025 (Cegid)

    @Test
    fun `Agosto 2025 Cegid - outro fornecedor, sem D02`() {
        val recibos = ler("recibo-agosto-2025.txt")

        assertEquals(1, recibos.size)
        val recibo = recibos.first()

        assertEquals(YearMonth.of(2025, 8), recibo.mesReferencia)
        assertEquals(dia(2025, 8, 31), recibo.dataFecho)
        assertEquals(10_760_000L, recibo.vencimentoBase)   // 1 076,00 €
        assertEquals(62_100L, recibo.vencimentoHora)       // 6,21 €/h
        assertEquals(22, recibo.numDiasUteis)
        assertEquals(2_640_000L, recibo.irsRetidoAno)      // 264,00 € acumulado
        assertEquals(15_085_700L, recibo.totalAbonos)      // 1 508,57 €
        assertEquals(1_600_600L, recibo.totalDescontos)    // 160,06 €
        assertEquals(13_485_100L, recibo.liquido)          // 1 348,51 €

        assertEquals(10_760_000L, recibo.valorDe("R01"))
        assertEquals(492_500L, recibo.valorDe("R14"))      // 49,25 €
        assertEquals("COMP_BAIXA", recibo.linha("R51").codigoInterno)
        assertEquals(2_126_600L, recibo.valorDe("R51"))    // 212,66 €
        // A Cegid usa RCR/RCRA para o subsídio de alimentação; a PRIMAVERA usa SAC.
        assertEquals("SUP_ALIM", recibo.linha("RCR").codigoInterno)
        assertEquals(1_632_400L, recibo.valorDe("RCR"))    // 163,24 €
        assertEquals("SUP_ALIM", recibo.linha("RCRA").codigoInterno)
        assertEquals(74_200L, recibo.valorDe("RCRA"))      // 7,42 €
        assertEquals(1_471_700L, recibo.valorDe("D01"))    // 147,17 €
        assertEquals(128_900L, recibo.valorDe("D04"))      // 12,89 €

        // Nesse ano não houve retenção de IRS: a linha D02 não existe.
        assertTrue(recibo.linhas.none { it.codigoRecibo == "D02" })
    }

    // ------------------------------------------------------------------ Mapeamento

    @Test
    fun `Mapeamento - conhecidos, informativos, Outros e desconhecidos`() {
        val venc = mapearCodigoRecibo("R01")
        assertEquals("VENC", venc.codigoInterno)
        assertEquals(NaturezaRubrica.ABONO, venc.natureza)
        assertNull(venc.aviso)

        val irs = mapearCodigoRecibo("d02")   // tolera minúsculas
        assertEquals("D02", irs.codigoInterno)
        assertEquals(NaturezaRubrica.DESCONTO, irs.natureza)

        val ferias = mapearCodigoRecibo("F50")   // informativa: gozo de férias
        assertNull(ferias.codigoInterno)
        assertEquals(NaturezaRubrica.ABONO, ferias.natureza)
        assertTrue(ferias.aviso!!.contains("F50"))

        // O F40 (subsídio de alimentação por dia, formato antigo) é, nos recibos reais, a linha
        // informativa do subsídio durante a baixa e não tem rubrica no mapa. Aqui tranca-se esse
        // contrato para ninguém o voltar a mapear para SUP_ALIM.
        val subsidioDaBaixa = mapearCodigoRecibo("F40")
        assertNull(subsidioDaBaixa.codigoInterno)
        assertTrue(subsidioDaBaixa.aviso!!.contains("F40"))

        // R51 saiu de "Outros" na 28i: tem rubrica própria (COMP_BAIXA, isenta de SS e IRS) porque
        // "Outros" incide e os descontos não podem cair sobre o complemento de baixa. O R118 (média
        // de horas noturnas) é tributado e fica em Outros.
        val complemento = mapearCodigoRecibo("R51")
        assertEquals("COMP_BAIXA", complemento.codigoInterno)
        assertTrue(complemento.aviso!!.contains("R51"))
        assertEquals("OUTROS", mapearCodigoRecibo("R118").codigoInterno)

        val desconhecido = mapearCodigoRecibo("Z99")
        assertNull(desconhecido.codigoInterno)
        assertTrue(desconhecido.aviso!!.contains("Z99"))
    }

    /**
     * O eixo y depende do extractor: o pdfbox devolve `yDirAdj`, crescente para baixo; um
     * extractor que leia as coordenadas do PDF em bruto devolve y crescente para cima. O
     * parser deduz o sentido e tem de dar o mesmo resultado nos dois casos.
     */
    @Test
    fun `parser da o mesmo com o eixo y invertido`() {
        val normal = parseReciboPdf(blocosDe("recibo-agosto-2026.txt")).first()
        val invertido = parseReciboPdf(
            blocosDe("recibo-agosto-2026.txt").map { it.copy(y = -it.y) }
        ).first()

        assertEquals(normal.dataFecho, invertido.dataFecho)
        assertEquals(normal.irsRetidoAno, invertido.irsRetidoAno)
        assertEquals(normal.totalAbonos, invertido.totalAbonos)
        assertEquals(normal.totalDescontos, invertido.totalDescontos)
        assertEquals(normal.liquido, invertido.liquido)
        assertEquals(normal.linhas.map { it.codigoRecibo }, invertido.linhas.map { it.codigoRecibo })
        assertEquals(normal.linhas.map { it.valor }, invertido.linhas.map { it.valor })
    }

    // -------------------------------------------------- Setembro 2026 (Fundação D. Pedro IV)

    @Test
    fun `Setembro 2026 - Fundacao D Pedro IV`() {
        val recibos = ler("recibo-setembro-2026-fundacao.txt")

        // O extractor entrega 4 páginas: a marca de água "Duplicado" e o corpo de cada uma
        // das 2 folhas do PDF ("/Count 2"). Só as folhas com grelha dão recibo. E a
        // PRIMAVERA só escreve os valores da linha "Total" na última folha — na primeira a
        // linha existe mas os números não, portanto cabeçalho+linhas leem-se na 1.ª e os
        // totais na 2.ª.
        assertEquals(2, recibos.size)
        val primeira = recibos.first()
        val segunda = recibos.last()

        // Cabecalho (repetido nas duas folhas)
        assertEquals("FUNDAÇÃO D. PEDRO IV", primeira.empresaEmissora)
        assertEquals(dia(2026, 9, 30), primeira.dataFecho)
        assertEquals(10_000_000L, primeira.vencimentoBase) // 1000,00 €
        assertEquals(57_700L, primeira.vencimentoHora)     // 5,77 €/h
        assertEquals(22, primeira.numDiasUteis)            // "N. Dias Mês: 22.00"
        assertEquals(1_920_000L, primeira.irsRetidoAno)    // 192,00 €

        // Linhas da 1.ª folha (Descrição/Faltas/Remunerações em vez de Abonos/Quantidade)
        assertEquals(9_666_700L, primeira.valorDe("R01"))  // 966,67 €
        assertEquals(7_500_000L, primeira.valorDe("R20"))  // 750,00 €
        assertEquals(1_911_400L, primeira.valorDe("D01"))  // 191,14 €

        // Desconhecidos da 1.ª folha... (o D05 já não está: 2026-10-21 tem rubrica própria,
        // OUTROS_DESC, e é o que desbloqueia os recibos da Fundação)
        assertTrue(
            "Faltam desconhecidos: ${primeira.codigosDesconhecidos}",
            primeira.codigosDesconhecidos.containsAll(listOf("R04", "R13", "R20"))
        )
        assertTrue("O D05 não pode continuar desconhecido", "D05" !in primeira.codigosDesconhecidos)
        // ...e os da 2.ª, incluindo o F106I: sufixo alfabético que o ICTS nao tem e que o
        // RE_CODIGO antigo (`^[A-Z]{1,4}\d{0,3}$`) recusava.
        assertEquals(listOf("F106I", "F35"), segunda.codigosDesconhecidos)

        // Os informativos F40 e F50 nao devem estar em desconhecidos
        assertTrue(!primeira.codigosDesconhecidos.contains("F40"))
        assertTrue(!primeira.codigosDesconhecidos.contains("F50"))
        assertTrue(!segunda.codigosDesconhecidos.contains("F40"))

        // Totais e liquido: so a 2.ª folha os traz
        assertEquals(18_376_700L, segunda.totalAbonos)    // 1837,67 €
        assertEquals(4_830_200L, segunda.totalDescontos)  // 483,02 €
        assertEquals(13_546_500L, segunda.liquido)        // 1354,65 €
    }

    /**
     * C7: a natureza de uma linha desconhecida sai da coluna em que o valor está. O D05 da
     * Fundação ("Desconto Subsídio Alimentação") traz o valor na coluna "Descontos", é desconto e
     * desde 2026-10-21 tem rubrica própria (OUTROS_DESC) — o mapeamento também a dá como
     * DESCONTO, para o caso de a grelha não trazer a etiqueta "Descontos".
     * O R13 negativo (-5,00) está em "Remunerações" e continua abono.
     */
    @Test
    fun `C7 - D05 da Fundacao sai como DESCONTO e vai para Outros Desconto`() {
        val primeira = ler("recibo-setembro-2026-fundacao.txt").first()

        val d05 = primeira.linhas.filter { it.codigoRecibo == "D05" }
        assertEquals(2, d05.size)
        assertTrue(d05.all { it.natureza == NaturezaRubrica.DESCONTO })
        assertTrue(d05.all { it.codigoInterno == "OUTROS_DESC" })

        // O mapeamento sozinho já diz desconto, sem depender da coluna do PDF.
        val mapeado = mapearCodigoRecibo("D05")
        assertEquals("OUTROS_DESC", mapeado.codigoInterno)
        assertEquals(NaturezaRubrica.DESCONTO, mapeado.natureza)

        assertTrue(
            primeira.linhas.filter { it.codigoRecibo == "R13" }.all { it.natureza == NaturezaRubrica.ABONO }
        )
        assertEquals(NaturezaRubrica.ABONO, primeira.linha("R01").natureza)
        assertEquals(NaturezaRubrica.DESCONTO, primeira.linha("D01").natureza)
    }

    /** C7: na ICTS as linhas de abono e os descontos conhecidos mantêm a natureza de sempre. */
    @Test
    fun `C7 - ICTS mantem abonos e descontos`() {
        val recibo = ler("recibo-setembro-2026.txt").first()

        assertEquals(NaturezaRubrica.ABONO, recibo.linha("R01").natureza)
        assertEquals(NaturezaRubrica.ABONO, recibo.linha("SAC").natureza)
        assertEquals(NaturezaRubrica.DESCONTO, recibo.linha("D02").natureza)
        // Informativa: o preço unitário não está na coluna dos descontos, e mesmo que estivesse
        // as informativas ficam fora da regra.
        assertEquals(NaturezaRubrica.ABONO, recibo.linha("F74").natureza)
    }

    // ------------------------------------------------------------------ Fusao (28f)

    @Test
    fun `fusao - Fundacao funde folhas duplicadas`() {
        val recibos = ler("recibo-setembro-2026-fundacao.txt")
        val fundidos = fundirRecibos(recibos)

        assertEquals(1, fundidos.size)
        val recibo = fundidos.first()

        // Deve ter as linhas da 1.a e os totais da 2.a
        assertTrue(recibo.linhas.isNotEmpty())
        assertEquals(18_376_700L, recibo.totalAbonos)
        assertEquals(13_546_500L, recibo.liquido)
    }

    @Test
    fun `fusao - ICTS Julho nao funde recibos com fechos diferentes`() {
        val recibos = ler("recibo-julho-2026.txt")
        val fundidos = fundirRecibos(recibos)

        assertEquals(2, fundidos.size)
        // Ambos devem manter-se separados
        assertNotEquals(fundidos[0].dataFecho, fundidos[1].dataFecho)
    }

    @Test
    fun `fusao - ICTS Marco nao funde 3 vias`() {
        val recibos = ler("recibo-marco-2026.txt")
        val fundidos = fundirRecibos(recibos)

        assertEquals(3, fundidos.size)
    }

    /**
     * A PSG imprime o suplemento do subsidio de Natal com o MESMO fecho da folha principal
     * (31-05-2016 e 31-08-2016). As duas trazem linhas com valor, portanto nao sao folhas
     * duplicadas: fundi-las perdia o recibo do mes (801,82) e ficava so o suplemento (54,30).
     */
    @Test
    fun `fusao - PSG nao funde suplemento com o mesmo fecho`() {
        val principal = reciboDeTeste(pagina = 1, totalAbonos = 8_018_200L, liquido = 6_802_600L)
        val suplemento = reciboDeTeste(pagina = 2, totalAbonos = 543_000L, liquido = 475_700L)

        val fundidos = fundirRecibos(listOf(principal, suplemento))

        assertEquals(2, fundidos.size)
        assertEquals(listOf(8_018_200L, 543_000L), fundidos.map { it.totalAbonos })
    }

    /** Um recibo mínimo com uma linha R01 — o suficiente para a fusão ver "tem linhas". */
    private fun reciboDeTeste(pagina: Int, totalAbonos: Long, liquido: Long): ReciboImportado =
        ReciboImportado(
            pagina = pagina,
            empresaEmissora = "PSG - SEGURANÇA PRIVADA, SA",
            mesReferencia = YearMonth.of(2016, 5),
            dataFecho = LocalDate.of(2016, 5, 31).toEpochDay(),
            vencimentoBase = 0L,
            vencimentoHora = 0L,
            numDiasUteis = null,
            irsRetidoAno = 0L,
            totalAbonos = totalAbonos,
            totalDescontos = 0L,
            liquido = liquido,
            linhas = listOf(
                LinhaReciboImportada(
                    codigoRecibo = "R01",
                    descricao = "Vencimento",
                    codigoInterno = "VENC",
                    natureza = NaturezaRubrica.ABONO,
                    valor = totalAbonos,
                    quantidade = null,
                    data = null,
                    ordem = 1,
                    aviso = null
                )
            ),
            avisos = emptyList(),
            codigosDesconhecidos = emptyList()
        )
}


