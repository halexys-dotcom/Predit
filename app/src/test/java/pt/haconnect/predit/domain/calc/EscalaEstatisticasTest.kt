package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.domain.model.PlanejamentoMes
import pt.haconnect.predit.ui.escala.EstatisticasMes
import pt.haconnect.predit.ui.escala.FonteEstatistica

class EscalaEstatisticasTest {

    @Test
    fun `1 - Com PlanejamentoMes calcula estatisticas do PDF`() {
        val plan = PlanejamentoMes(
            anoMes = "2026-09",
            totalMinutos = 10440, // 174h
            numTurnos = 22,
            numFolgas = 8,
            contratoMinutos = 10560,
            dataImportacao = 1000L
        )

        val numAusencias = 0
        val estatisticas = EstatisticasMes(
            numTurnos = plan.numTurnos,
            numFolgas = plan.numFolgas,
            numAusencias = numAusencias,
            totalMinutosTrabalho = plan.totalMinutos,
            fonte = FonteEstatistica.PLANEJAMENTO
        )

        assertEquals(22, estatisticas.numTurnos)
        assertEquals(8, estatisticas.numFolgas)
        assertEquals(0, estatisticas.numAusencias)
        assertEquals(10440, estatisticas.totalMinutosTrabalho)
        assertEquals(FonteEstatistica.PLANEJAMENTO, estatisticas.fonte)
    }

    @Test
    fun `2 - Sem PlanejamentoMes cai no calculo por projecao`() {
        val estatisticas = EstatisticasMes(
            numTurnos = 21,
            numFolgas = 9,
            numAusencias = 0,
            totalMinutosTrabalho = 10080,
            fonte = FonteEstatistica.PROJECAO
        )

        assertEquals(21, estatisticas.numTurnos)
        assertEquals(9, estatisticas.numFolgas)
        assertEquals(10080, estatisticas.totalMinutosTrabalho)
        assertEquals(FonteEstatistica.PROJECAO, estatisticas.fonte)
    }
}
