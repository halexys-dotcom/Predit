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
}
