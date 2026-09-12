package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.data.local.RotacaoEntity
import pt.haconnect.predit.data.local.RotacaoSlotEntity
import pt.haconnect.predit.domain.model.Rotacao
import pt.haconnect.predit.domain.model.RotacaoSlot
import pt.haconnect.predit.domain.model.RotacaoDetalhada

class ContagemDiasTest {

    @Test
    fun `contagem real de dias para varios tamanhos de ciclo`() {
        val tamanhos = listOf(1, 6, 16, 17, 18)

        for (tamanho in tamanhos) {
            val slots = (0 until tamanho).map { idx ->
                RotacaoSlot(rotacaoId = 1L, posicao = idx, tipoTurnoId = 1L)
            }
            val detalhe = RotacaoDetalhada(
                rotacao = Rotacao(id = 1L, nome = "Teste", comprimentoCiclo = tamanho),
                slots = slots
            )

            assertEquals(tamanho, detalhe.comprimentoReal)
        }
    }

    @Test
    fun `round trip guardar e recarregar mantem contagem exata`() {
        val slotsEmMemoria = (0 until 17).map { idx -> 1L } // 17 slots

        // Simula guardar
        val tamanhoGuardado = slotsEmMemoria.size
        val entidadeRotacao = RotacaoEntity(id = 1L, nome = "Ciclo 17", comprimentoCiclo = tamanhoGuardado)
        val entidadesSlots = slotsEmMemoria.mapIndexed { idx, tipoId ->
            RotacaoSlotEntity(rotacaoId = 1L, posicao = idx, tipoTurnoId = tipoId)
        }

        // Simula recarregar do banco
        val modeloSlots = entidadesSlots.map {
            RotacaoSlot(rotacaoId = it.rotacaoId, posicao = it.posicao, tipoTurnoId = it.tipoTurnoId)
        }
        val modeloDetalhado = RotacaoDetalhada(
            rotacao = Rotacao(id = entidadeRotacao.id, nome = entidadeRotacao.nome, comprimentoCiclo = entidadeRotacao.comprimentoCiclo),
            slots = modeloSlots
        )

        assertEquals(17, modeloDetalhado.comprimentoReal)
        assertEquals(modeloDetalhado.comprimentoReal, modeloDetalhado.rotacao.comprimentoCiclo)
    }
}
