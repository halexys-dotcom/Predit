package pt.haconnect.predit.domain.importacao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PdfPlannerParserTest {

    @Test
    fun `1 - Julho 2026 - parse com sucesso`() {
        val textoJulho = """
            Contrato de trabalho : 176
            01-Julho 13:00 21:00
            02-Julho 13:00 21:00
            03-Julho 13:00 21:00
            04-Julho 13:00 21:00
            05-Julho
            06-Julho
            07-Julho 13:00 21:00
            08-Julho 13:00 21:00
            09-Julho 13:00 21:00
            10-Julho 13:00 21:00
            11-Julho
            12-Julho
            13-Julho 13:00 21:00
            14-Julho 13:00 21:00
            15-Julho 13:00 21:00
            16-Julho 13:00 21:00
            17-Julho
            18-Julho
            19-Julho 13:00 21:00
            20-Julho 13:00 21:00
            21-Julho 13:00 21:00
            22-Julho 13:00 21:00
            23-Julho
            24-Julho
            25-Julho 13:00 21:00
            26-Julho 13:00 21:00
            27-Julho 13:00 21:00
            28-Julho 13:00 21:00
            29-Julho
            30-Julho 13:00 21:00
            31-Julho 13:00 21:00
        """.trimIndent()

        val plano = parsePdfPlanner(textoJulho, "Planning-Juillet-2026.pdf")

        assertEquals(YearMonth.of(2026, 7), plano.mesReferencia)
        assertEquals(176 * 60, plano.contratoTrabalhoMin)
        assertEquals(22, plano.dias.size)
        assertEquals(176 * 60, plano.totalMinutos)
        assertTrue(plano.avisos.isEmpty())
    }

    @Test
    fun `2 - Setembro 2026 - verifica duracao de 7h e folgas`() {
        val textoSetembro = """
            Contrato de trabalho : 176
            01-Setembro 13:00 21:00
            02-Setembro 13:00 21:00
            03-Setembro 13:00 14:09 14:09 21:00
            04-Setembro 13:00 21:00
            05-Setembro
            06-Setembro
            07-Setembro 13:00 21:00
            08-Setembro 13:00 21:00
            09-Setembro 13:00 20:00
            10-Setembro 13:00 20:00
            11-Setembro
            12-Setembro
            13-Setembro 13:00 21:00
            14-Setembro 13:00 21:00
            15-Setembro 13:00 21:00
            16-Setembro 13:00 21:00
            17-Setembro
            18-Setembro
            19-Setembro 13:00 21:00
            20-Setembro 13:00 21:00
            21-Setembro 13:00 21:00
            22-Setembro 13:00 21:00
            23-Setembro
            24-Setembro
            25-Setembro 13:00 21:00
            26-Setembro 13:00 21:00
            27-Setembro 13:00 22:00
            28-Setembro 13:00 22:00
            29-Setembro
            30-Setembro 13:00 21:00
        """.trimIndent()

        val plano = parsePdfPlanner(textoSetembro, "Planning-Septembre-2026 (4).pdf")

        assertEquals(YearMonth.of(2026, 9), plano.mesReferencia)
        assertEquals(168 * 60, plano.totalMinutos)
        assertTrue(plano.avisos.isEmpty())

        val dia03 = plano.dias.find { it.epochDay == LocalDate.of(2026, 9, 3).toEpochDay() }
        assertEquals(8 * 60, dia03?.duracaoMin)

        val dia09 = plano.dias.find { it.epochDay == LocalDate.of(2026, 9, 9).toEpochDay() }
        val dia10 = plano.dias.find { it.epochDay == LocalDate.of(2026, 9, 10).toEpochDay() }
        assertEquals(7 * 60, dia09?.duracaoMin)
        assertEquals(7 * 60, dia10?.duracaoMin)

        val folgasEsperadas = listOf(5, 6, 11, 12, 17, 18)
        for (diaFolga in folgasEsperadas) {
            val d = plano.dias.find { it.epochDay == LocalDate.of(2026, 9, diaFolga).toEpochDay() }
            assertNull("Dia $diaFolga deveria ser folga", d)
        }
    }

    @Test
    fun `3 - Dois turnos no mesmo dia`() {
        val textoDoisTurnos = """
            Contrato de trabalho : 8
            02-Setembro 08:00 12:00 14:00 18:00 CC
        """.trimIndent()

        val plano = parsePdfPlanner(textoDoisTurnos, "Planning-Septembre-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals(8 * 60, dia.inicioMin)
        assertEquals(18 * 60, dia.fimMin)
        assertEquals(120, dia.pausaMin)
        assertEquals(8 * 60, dia.duracaoMin)
    }

    @Test
    fun `4 - Ano ausente no nome do ficheiro - gera aviso`() {
        val texto = "01-Julho 13:00 21:00"
        val plano = parsePdfPlanner(texto, "Planning-Julho.pdf")

        assertNull(plano.mesReferencia)
        assertTrue(plano.avisos.contains("Ano não detetado no nome do ficheiro"))
    }

    @Test
    fun `5 - Um turno com par repetido na tarefa`() {
        val texto = "Qua01-Julho13:00 21:00CC ES 13:00 21:0008:00"
        val plano = parsePdfPlanner(texto, "Planning-Julho-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals(13 * 60, dia.inicioMin)
        assertEquals(21 * 60, dia.fimMin)
        assertEquals(0, dia.pausaMin)
        assertEquals(8 * 60, dia.duracaoMin)
        assertTrue(plano.avisos.isEmpty())
    }

    @Test
    fun `6 - Dois turnos genuinos`() {
        val texto = "Ter02-Setembro05:00 13:00 13:00 21:00GOC ES..."
        val plano = parsePdfPlanner(texto, "Planning-Setembro-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals(5 * 60, dia.inicioMin)
        assertEquals(21 * 60, dia.fimMin)
        assertEquals(0, dia.pausaMin)
        assertEquals(16 * 60, dia.duracaoMin)
        assertTrue(plano.avisos.isEmpty())
    }

    @Test
    fun `7 - Dia com duas tarefas - escolher a que fecha no HHorario`() {
        val texto = "Qui02-Julho13:00 21:00CC Emb ES 13:00 19:00CC ES 19:00 21:0008:00"
        val plano = parsePdfPlanner(texto, "Planning-Julho-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals("CC ES", dia.posto)
    }

    @Test
    fun `8 - Dia com tarefa unica`() {
        val texto = "Qua01-Julho13:00 21:00CC ES 13:00 21:0008:00"
        val plano = parsePdfPlanner(texto, "Planning-Julho-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals("CC ES", dia.posto)
    }

    @Test
    fun `9 - Dia com tarefa cujo fim nao coincide com HHorario - sem aviso`() {
        val texto = "Qua15-Julho13:00 21:00CC ES 13:00 15:0008:00CC ES 16:00 18:0008:00"
        val plano = parsePdfPlanner(texto, "Planning-Julho-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals("CC ES", dia.posto)
        assertTrue(plano.avisos.isEmpty())
    }

    @Test
    fun `10 - Setembro dia 09 - tarefa unica P6 ES`() {
        val texto = "Qua09-Setembro13:00 20:00P6 ES 13:00 20:0007:00"
        val plano = parsePdfPlanner(texto, "Planning-Setembro-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals("P6 ES", dia.posto)
    }

    @Test
    fun `11 - Dia com dois pares no HHorario somados`() {
        val texto = "Qui03-Setembro13:00 14:09 14:09 21:00FINJHRS..."
        val plano = parsePdfPlanner(texto, "Planning-Setembro-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals(13 * 60, dia.inicioMin)
        assertEquals(21 * 60, dia.fimMin)
        assertEquals(8 * 60, dia.duracaoMin)
        assertEquals(0, dia.pausaMin)
    }

    @Test
    fun `12 - Dia com dois pares e pausa real`() {
        val texto = "Seg15-Setembro13:00 15:00 15:30 21:00CC ES..."
        val plano = parsePdfPlanner(texto, "Planning-Setembro-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals(13 * 60, dia.inicioMin)
        assertEquals(21 * 60, dia.fimMin)
        assertEquals(7 * 60 + 30, dia.duracaoMin)
        assertEquals(30, dia.pausaMin)
    }

    @Test
    fun `13 - Dia 24-Set do PDF real com tarefas terminadas em 20h00`() {
        val texto = "Qui24-Setembro13:00 21:00CC ES 15:00 18:00CC ES 18:00 20:0008:00"
        val plano = parsePdfPlanner(texto, "Planning-Setembro-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals("CC ES", dia.posto)
        assertTrue(plano.avisos.isEmpty())
    }

    @Test
    fun `14 - Linha FINJHRS ignorada`() {
        val texto = "Qui03-Setembro13:00 21:00FINJHRS CC ES 15:00 21:00"
        val plano = parsePdfPlanner(texto, "Planning-Setembro-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertEquals("CC ES", dia.posto)
        assertTrue(plano.avisos.isEmpty())
    }

    @Test
    fun `15 - Sem tarefas validas`() {
        val texto = "Sex11-Setembro13:00 21:00"
        val plano = parsePdfPlanner(texto, "Planning-Setembro-2026.pdf")

        assertEquals(1, plano.dias.size)
        val dia = plano.dias.first()
        assertNull(dia.posto)
        assertTrue(plano.avisos.isEmpty())
    }
}
