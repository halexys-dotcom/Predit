package pt.haconnect.predit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import pt.haconnect.predit.data.local.PlanejamentoMesEntity
import pt.haconnect.predit.data.local.paraEntidade
import pt.haconnect.predit.data.local.paraModelo

class PlanejamentoMesTest {

    @Test
    fun `1 - mapeamento modelo para entidade e vice-versa`() {
        val model = PlanejamentoMes(
            anoMes = "2026-09",
            totalMinutos = 10440,
            numTurnos = 22,
            numFolgas = 8,
            contratoMinutos = 10560,
            dataImportacao = 1700000000000L
        )

        val entity = model.paraEntidade()
        assertEquals("2026-09", entity.anoMes)
        assertEquals(10440, entity.totalMinutos)
        assertEquals(22, entity.numTurnos)
        assertEquals(8, entity.numFolgas)
        assertEquals(10560, entity.contratoMinutos)
        assertEquals(1700000000000L, entity.dataImportacao)

        val modelDeVolta = entity.paraModelo()
        assertEquals(model, modelDeVolta)
    }

    @Test
    fun `2 - campos opcionais nulos em PlanejamentoMes`() {
        val entity = PlanejamentoMesEntity(
            anoMes = "2026-10",
            totalMinutos = 9600,
            numTurnos = 20,
            numFolgas = 11,
            contratoMinutos = null,
            dataImportacao = 1700000000500L
        )

        val model = entity.paraModelo()
        assertEquals("2026-10", model.anoMes)
        assertEquals(9600, model.totalMinutos)
        assertEquals(20, model.numTurnos)
        assertEquals(11, model.numFolgas)
        assertNotNull(model)
        assertEquals(null, model.contratoMinutos)
    }

    @Test
    fun `3 - upsert com numTurnos e numFolgas`() {
        val model = PlanejamentoMes(
            anoMes = "2026-09",
            totalMinutos = 10440,
            numTurnos = 22,
            numFolgas = 8,
            contratoMinutos = 10560,
            dataImportacao = 1700000000000L
        )

        val entity = model.paraEntidade()
        assertEquals(22, entity.numTurnos)
        assertEquals(8, entity.numFolgas)

        val modelDeVolta = entity.paraModelo()
        assertEquals(22, modelDeVolta.numTurnos)
        assertEquals(8, modelDeVolta.numFolgas)
    }
}
