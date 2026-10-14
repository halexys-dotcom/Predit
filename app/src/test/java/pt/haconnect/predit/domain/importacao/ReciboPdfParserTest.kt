package pt.haconnect.predit.domain.importacao

import org.junit.Assert.assertEquals
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
    fun `Setembro 2026 - ferias e ausencia parcial caem em Outros, com aviso`() {
        val recibo = ler("recibo-setembro-2026.txt").first()

        val ferias = recibo.linhas.filter { it.codigoRecibo == "F50" }
        assertEquals(11, ferias.size)
        assertTrue(ferias.all { it.codigoInterno == "OUTROS" })
        assertTrue(ferias.all { it.valor == 17_400L })        // 1,74 € por dia
        assertTrue(ferias.all { it.data != null })            // cada dia traz a sua data
        assertTrue("uma linha de férias tem de trazer aviso", ferias.all { it.aviso != null })

        val ausencia = recibo.linha("F74")
        assertEquals("OUTROS", ausencia.codigoInterno)
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
        assertEquals("SUP_ALIM", suplemento.linha("F40").codigoInterno)
        assertEquals(78_500L, suplemento.valorDe("F40"))      // 7,85 €
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
        assertEquals("OUTROS", primeira.linha("R51").codigoInterno)   // complemento de baixa
        assertEquals(2_587_000L, primeira.valorDe("R51"))     // 258,70 €
        assertEquals("SUP_ALIM", primeira.linha("RCR").codigoInterno)

        // F02 (baixas) entra em Outros com aviso, como as férias e a ausência parcial.
        val baixas = primeira.linhas.filter { it.codigoRecibo == "F02" }
        assertTrue(baixas.isNotEmpty())
        assertTrue(baixas.all { it.codigoInterno == "OUTROS" && it.aviso != null })
        assertEquals(396_700L, baixas.first().valor)          // 39,67 €
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
        assertEquals("OUTROS", recibo.linha("R51").codigoInterno)
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
    fun `Mapeamento - codigos conhecidos, Outros e desconhecidos`() {
        val venc = mapearCodigoRecibo("R01")
        assertEquals("VENC", venc.codigoInterno)
        assertEquals(NaturezaRubrica.ABONO, venc.natureza)
        assertNull(venc.aviso)

        val irs = mapearCodigoRecibo("d02")   // tolera minúsculas
        assertEquals("D02", irs.codigoInterno)
        assertEquals(NaturezaRubrica.DESCONTO, irs.natureza)

        val ferias = mapearCodigoRecibo("F50")
        assertEquals("OUTROS", ferias.codigoInterno)
        assertEquals(NaturezaRubrica.ABONO, ferias.natureza)
        assertTrue(ferias.aviso!!.contains("F50"))

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
}


