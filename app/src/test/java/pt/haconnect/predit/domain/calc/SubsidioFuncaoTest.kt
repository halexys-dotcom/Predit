package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.haconnect.predit.data.local.TABELAS_IRS_2026
import pt.haconnect.predit.data.repository.paraEscalao
import pt.haconnect.predit.domain.model.Ausencia
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.ParametrosCCT
import pt.haconnect.predit.domain.model.Rubrica
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.LocalDate
import java.time.YearMonth

/**
 * 13a: subsídio de função (SUP_FUNCAO).
 *
 * Só as categorias com [ParametrosCCT.subsidioFuncaoMil] > 0 o recebem (hoje o Team Leader,
 * 43,00 €/mês) e é prorrateado por 22 dias, cortado pelos dias úteis cobertos por ausências —
 * a mesma base de dias úteis do SUP_ALIM, mas com 22 e não com os 30 do transporte.
 */
class SubsidioFuncaoTest {

    /** Escalões reais da tabela I (trabalho, Continente) para 2026 — o D02 precisa deles. */
    private val tabelaI: List<EscalaoIRS> = TABELAS_IRS_2026
        .filter { it.regiao == "CONTINENTE" && it.categoria == "TRABALHO" && it.tabelaNumero == 1 }
        .map { it.paraEscalao() }

    /** Catálogo mínimo: o que o estimador calcula neste teste e os três descontos derivados. */
    private val catalogo = listOf(
        Rubrica(1, "VENC", "Vencimento", true, true, true, "FIXO", true, 1),
        Rubrica(9, "SUP_ALIM", "Sub. alimentação", false, false, false, "FIXO", true, 9),
        Rubrica(10, "SUP_TRAN", "Sub. transporte", true, true, false, "FIXO", true, 10),
        Rubrica(23, "SUP_FUNCAO", "Subsídio de função", true, true, false, "DERIVADO", true, 23),
        Rubrica(20, "D01", "Segurança Social (11%)", false, false, false, "DERIVADO", true, 20),
        Rubrica(21, "D02", "IRS", false, false, false, "DERIVADO", true, 21),
        Rubrica(22, "D04", "Sindicato (1%)", false, false, false, "DERIVADO", true, 22)
    )

    private val turnoNormal = TipoTurno(
        id = 1,
        nome = "Tarde 8h",
        abreviatura = "T08H",
        cor = 0xFFC0392BL,
        inicioMin = 13 * 60,
        fimMin = 21 * 60,
        pausaMin = 0,
        categoria = CategoriaTurno.TRABALHO,
        ativo = true
    )

    private val contrato = ContratoUtilizador(
        dataAdmissao = LocalDate.of(2020, 1, 1).toEpochDay(),
        categoriaCodigo = "TEAM_LEADER"
    )

    private fun parametros(subsidioFuncaoMil: Long) = ParametrosCCT(
        validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
        vencimentoBaseMil = 11_379_800,
        subAlimentacaoDiaMil = 78_500,
        subTransporteMesMil = 520_900,
        horarioSemanalReferencia = 40,
        codigoCategoria = "TEAM_LEADER",
        subsidioFuncaoMil = subsidioFuncaoMil
    )

    /**
     * Setembro de 2026 com 22 dias de turno na projeção (é o N.º Dias Úteis do mês) e sem dias
     * reais: chega para o cálculo dos subsídios. A ausência, quando existe, cobre dias úteis.
     */
    private fun contexto(
        parametrosCCT: ParametrosCCT,
        ausencias: List<Ausencia> = emptyList()
    ): ContextoEstimativa {
        val setembro = YearMonth.of(2026, 9)
        val projecao = (1..22).map { dia -> DiaProjetado(setembro.atDay(dia).toEpochDay(), tipoTurnoId = 1L) }
        return ContextoEstimativa(
            anoMes = setembro,
            contrato = contrato,
            parametrosCCT = parametrosCCT,
            rubricas = catalogo,
            diasReais = emptyList(),
            projecao = projecao,
            escaloesIRS = tabelaI,
            tiposTurno = listOf(turnoNormal),
            ausencias = ausencias
        )
    }

    /** Setembro de 2026: 7 a 11 é segunda a sexta — 5 dias úteis cobertos. */
    private fun feriasDeumaSemana() = Ausencia(
        id = 1,
        tipoTurnoId = 3,
        dataInicio = LocalDate.of(2026, 9, 7).toEpochDay(),
        dataFim = LocalDate.of(2026, 9, 11).toEpochDay()
    )

    private fun EstimativaRecibo.valor(codigo: String): Long =
        rubricas.first { it.codigo == codigo }.valorMil

    @Test
    fun `S1 - Team Leader com o mes completo recebe os 43 euros`() {
        val est = estimarRecibo(contexto(parametros(subsidioFuncaoMil = 430_000L)))

        assertEquals(430_000L, est.valor("SUP_FUNCAO"))    // 43,00 €, mês inteiro
    }

    @Test
    fun `S2 - APAA sem subsidio de funcao fica a zero`() {
        val est = estimarRecibo(contexto(parametros(subsidioFuncaoMil = 0L)))

        assertEquals(0L, est.valor("SUP_FUNCAO"))
    }

    @Test
    fun `S3 - ausencia de 5 dias uteis prorrateia 17 de 22 dias`() {
        val est = estimarRecibo(
            contexto(parametros(subsidioFuncaoMil = 430_000L), listOf(feriasDeumaSemana()))
        )

        // 430 000 × 17 ÷ 22 = 332 272,72… → 332 273 (43,00 € → 33,23 €)
        assertEquals(332_273L, est.valor("SUP_FUNCAO"))
        // O sindicato continua a incidir só sobre o VENC: o subsídio de função não mexe no D04.
        assertEquals(113_798L, est.valor("D04"))
    }
}
