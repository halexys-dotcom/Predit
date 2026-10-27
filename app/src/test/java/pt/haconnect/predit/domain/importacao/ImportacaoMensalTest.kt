package pt.haconnect.predit.domain.importacao

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.model.DiaReal

class ImportacaoMensalTest {

    @Test
    fun `1 - Dia novo + PDF deve resultar em INSERIR`() {
        val existente: DiaReal? = null
        val acao = decidirImportacaoDia(existente)
        assertEquals(AcaoImportacaoDia.INSERIR, acao)
    }

    @Test
    fun `2 - Dia existente com origem PDF + PDF deve resultar em ATUALIZAR`() {
        val existente = DiaReal(
            id = 10L,
            data = 20697L,
            tipoTurnoId = null,
            inicioMin = 780,
            fimMin = 1260,
            pausaMin = 0,
            origem = "PDF"
        )

        val acao = decidirImportacaoDia(existente)
        assertEquals(AcaoImportacaoDia.ATUALIZAR, acao)
    }

    @Test
    fun `3 - Dia existente com origem MANUAL + PDF deve resultar em MANTER_MANUAL`() {
        val existente = DiaReal(
            id = 11L,
            data = 20697L,
            tipoTurnoId = null,
            inicioMin = 849, // 14:09 editado pelo utilizador
            fimMin = 1260,
            pausaMin = 0,
            origem = "MANUAL"
        )

        val acao = decidirImportacaoDia(existente)
        assertEquals(AcaoImportacaoDia.MANTER_MANUAL, acao)
    }

    /**
     * T4 (fase 29): o dia importado grava as horas do PDF nas colunas reais e nas previstas. É o
     * previsto que sobrevive quando o utilizador editar o dia.
     */
    @Test
    fun `4 - importar o PDF grava as horas tambem nas previstas`() {
        val doPdf = DiaImportado(
            epochDay = 20697L,
            inicioMin = 780,
            fimMin = 1260,
            pausaMin = 0,
            duracaoMin = 480,
            posto = "CC ES"
        )

        val gravado = diaRealDoPdf(doPdf, id = 7L)

        assertEquals(7L, gravado.id)
        assertEquals("PDF", gravado.origem)
        assertEquals("o importador continua a não guardar tipo", null, gravado.tipoTurnoId)
        assertEquals("CC ES", gravado.posto)
        assertEquals(780, gravado.inicioMin)
        assertEquals(1260, gravado.fimMin)
        assertEquals("horas do PDF, também como previsto", 780, gravado.previstoInicioMin)
        assertEquals(1260, gravado.previstoFimMin)
        assertEquals(0, gravado.previstoPausaMin)
    }

    /**
     * T5 (fase 29): reimportar o PDF não toca num dia que o utilizador corrigiu. Só o previsto é
     * reposto — é a única forma de preencher os dias importados antes da migração 23→24.
     */
    @Test
    fun `5 - reimportar o PDF so repoe o previsto num dia manual`() {
        val existente = DiaReal(
            id = 11L,
            data = 20697L,
            tipoTurnoId = 5L,
            inicioMin = 849,     // 14:09, como o utilizador registou
            fimMin = 1260,
            pausaMin = 30,
            nota = "troquei com o colega",
            origem = "MANUAL",
            posto = "StaffCrPr ES"
        )
        val doPdf = DiaImportado(
            epochDay = 20697L,
            inicioMin = 780,
            fimMin = 1260,
            pausaMin = 0,
            duracaoMin = 480,
            posto = "CC ES"
        )

        val gravado = diaManualComPrevistoDoPdf(existente, doPdf)

        // O registo do utilizador fica intacto...
        assertEquals(11L, gravado.id)
        assertEquals("MANUAL", gravado.origem)
        assertEquals(5L, gravado.tipoTurnoId)
        assertEquals(849, gravado.inicioMin)
        assertEquals(1260, gravado.fimMin)
        assertEquals(30, gravado.pausaMin)
        assertEquals("troquei com o colega", gravado.nota)
        assertEquals("StaffCrPr ES", gravado.posto)
        // ...e só as horas previstas do PDF são repostas.
        assertEquals(780, gravado.previstoInicioMin)
        assertEquals(1260, gravado.previstoFimMin)
        assertEquals(0, gravado.previstoPausaMin)
    }
}
