package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.haconnect.predit.domain.model.CategoriaTurno
import pt.haconnect.predit.domain.model.DiaReal
import pt.haconnect.predit.domain.model.TipoTurno
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fase 19 — modo de escala por PDF mensal. Prova duas coisas:
 *
 * 1. Com o contrato em PDF_MENSAL a grelha do calendário passa a vir dos dia_real importados do
 *    PDF (e a rotação deixa de contar), incluindo o estado vazio quando não há PDF no mês.
 * 2. O chip do dia vem de [tipoDerivadoDoPdf], porque o importador do PDF grava sempre
 *    `tipoTurnoId = null` (guarda só horas e posto — inputs, não valores calculados).
 *
 * A composição do chip é a que o EscalaViewModel faz: para cada dia devolvido por
 * [diasProjetadosPara], derivar o tipo a partir do dia_real correspondente.
 */
class EscalaModoPdfTest {

    private val T08H = 100L   // tipo de trabalho 13:00-21:00
    private val FOLGA = 200L  // tipo de folga

    private val tipos = listOf(
        TipoTurno(
            id = T08H,
            nome = "Tarde 8h",
            abreviatura = "T08H",
            cor = 0xFFC0392BL,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            pausaMin = 0,
            categoria = CategoriaTurno.TRABALHO
        ),
        TipoTurno(
            id = FOLGA,
            nome = "Folga",
            abreviatura = "F",
            cor = 0xFF1ABC9CL,
            inicioMin = 0,
            fimMin = 0,
            pausaMin = 0,
            categoria = CategoriaTurno.FOLGA
        )
    )

    private val setembro = YearMonth.of(2026, 9)

    /** Rotação em vigor o ano todo: só serve para provar que no modo PDF é ignorada. */
    private val aplicacao = AplicacaoVigente(
        validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
        validoAte = null,
        dataAncora = LocalDate.of(2026, 1, 1).toEpochDay(),
        slots = listOf(T08H, FOLGA)
    )

    private fun diaPdf(dia: Int, inicioMin: Int, fimMin: Int, posto: String? = null) = DiaReal(
        data = LocalDate.of(2026, 9, dia).toEpochDay(),
        tipoTurnoId = null,   // o importador do PDF grava sempre null
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = 0,
        origem = ORIGEM_PDF,
        posto = posto
    )

    private fun dia(dia: Int, origem: String, inicioMin: Int, fimMin: Int) = DiaReal(
        data = LocalDate.of(2026, 9, dia).toEpochDay(),
        tipoTurnoId = null,
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = 0,
        origem = origem
    )

    private val de = LocalDate.of(2026, 9, 1).toEpochDay()
    private val ate = LocalDate.of(2026, 9, 30).toEpochDay()

    /** O que o ViewModel faz por dia: o chip sai do dia_real do PDF, com o tipo derivado. */
    private fun chips(dias: List<DiaProjetado>, diasReais: List<DiaReal>): List<TipoTurno?> =
        dias.map { dp ->
            tipoDerivadoDoPdf(diasReais.first { it.data == dp.epochDay }, tipos)
        }

    @Test
    fun `T1 - modo PDF mensal mostra os chips dos dias do PDF`() {
        val diasReais = listOf(
            diaPdf(1, 13 * 60, 21 * 60, "CC ES"),
            diaPdf(2, 13 * 60, 21 * 60, "CC ES"),
            diaPdf(3, 7 * 60, 15 * 60, "StaffCrPr ES"),
            // Fora da janela do mês: não pode entrar na grelha.
            DiaReal(
                data = LocalDate.of(2026, 8, 31).toEpochDay(),
                tipoTurnoId = null,
                inicioMin = 13 * 60,
                fimMin = 21 * 60,
                pausaMin = 0,
                origem = ORIGEM_PDF
            ),
            // Registo manual dentro do mês: no modo PDF a grelha é a do PDF, logo fica de fora.
            dia(4, "MANUAL", 9 * 60, 17 * 60)
        )

        val dias = diasProjetadosPara(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = emptyList()
        )

        assertEquals(
            "só os três dias do PDF dentro do mês",
            listOf(1, 2, 3),
            dias.map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }
        )
        assertTrue("o dia_real do PDF não traz tipo: vem null", dias.all { it.tipoTurnoId == null })
        assertEquals(
            "chip nesses três dias, com o tipo derivado das horas",
            listOf<TipoTurno?>(tipos[0], tipos[0], tipos[0]),
            chips(dias, diasReais)
        )

        assertTrue(
            "havendo dias PDF no mês, a escala existe",
            temEscalaAplicada(TIPO_ESCALA_PDF_MENSAL, diasReais, emptyList(), setembro)
        )
    }

    @Test
    fun `T2 - modo PDF mensal sem PDF importado nao tem escala aplicada`() {
        val diasReais = listOf(dia(4, "MANUAL", 9 * 60, 17 * 60))

        val dias = diasProjetadosPara(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = listOf(aplicacao)
        )

        assertTrue("a rotação em vigor é ignorada no modo PDF", dias.isEmpty())
        assertFalse(
            "sem dia PDF no mês, o estado vazio aparece",
            temEscalaAplicada(TIPO_ESCALA_PDF_MENSAL, diasReais, listOf(aplicacao), setembro)
        )

        // Contraste: com a mesma aplicação, o modo de sempre continua a projetar o mês.
        assertEquals(30, diasProjetadosPara(TIPO_ESCALA_ROTACAO, diasReais, de, ate, listOf(aplicacao)).size)
        assertTrue(temEscalaAplicada(TIPO_ESCALA_ROTACAO, diasReais, listOf(aplicacao), setembro))
    }

    @Test
    fun `T3 - tipo derivado do dia do PDF`() {
        // a) horas iguais às de um tipo de trabalho -> esse tipo
        assertEquals(T08H, tipoDerivadoDoPdf(diaPdf(1, 13 * 60, 21 * 60, "CC ES"), tipos)?.id)

        // b) horas sem tipo correspondente -> o primeiro de trabalho
        assertEquals(T08H, tipoDerivadoDoPdf(diaPdf(2, 13 * 60, 22 * 60, "CC ES"), tipos)?.id)

        // c) sem horas -> folga
        assertEquals(FOLGA, tipoDerivadoDoPdf(diaPdf(3, 0, 0), tipos)?.id)

        // Sem nenhum tipo de trabalho na lista fica null: o chip aparece vazio, como antes.
        assertNull(tipoDerivadoDoPdf(diaPdf(4, 13 * 60, 21 * 60), listOf(tipos[1])))
    }

    /**
     * T4 e T5 (2026-10-21): a régua do estimador do recibo. No modo PDF a projeção tem de sair com
     * o tipo derivado — o dia_real do PDF não o guarda — senão o estimador conta 0 dias de
     * trabalho, não proporcionaliza o VENC e deixa os subsídios a 0,00 €.
     */
    @Test
    fun `T4 - no modo PDF a projecao do recibo leva o tipo derivado`() {
        val diasReais = listOf(diaPdf(1, 13 * 60, 21 * 60), diaPdf(2, 0, 0))

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = LocalDate.of(2026, 9, 1).toEpochDay(),
            ateEpochDay = LocalDate.of(2026, 9, 2).toEpochDay(),
            aplicacoes = listOf(aplicacao),
            tiposTurno = tipos
        )

        // 13h-21h é o T08H; dia sem horas é folga.
        assertEquals(listOf<Long?>(T08H, FOLGA), projecao.map { it.tipoTurnoId })
        // As datas são as do PDF — a rotação em vigor não entra nesta grelha.
        assertEquals(diasReais.map { it.data }, projecao.map { it.epochDay })
    }

    @Test
    fun `T5 - no modo rotacao a projecao do recibo continua a ser a da rotacao`() {
        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_ROTACAO,
            // Um dia de trabalho no PDF não pode contaminar a projeção da rotação.
            diasReais = listOf(diaPdf(1, 13 * 60, 21 * 60)),
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = listOf(aplicacao),
            tiposTurno = tipos
        )

        assertEquals(projetarIntervalo(de, ate, listOf(aplicacao)), projecao)
        // Dia 1 de setembro no ciclo (âncora 1 de janeiro, 2 posições) é folga: se o tipo
        // derivado do PDF tivesse entrado, aqui aparecia o T08H.
        assertEquals(FOLGA, projecao.first { it.epochDay == de }.tipoTurnoId)
        // E a régua continua a ser o tipo projetado: nenhum dia traz horas previstas próprias.
        assertTrue(projecao.all { it.minutosPrevistos == null })
    }

    /**
     * T6 (2026-10-21, auditoria ponto 2): no modo PDF as horas previstas vêm do próprio dia do PDF
     * e não da duração do tipo derivado. Um turno de 12h cuja única correspondência no catálogo é
     * um tipo de 8h ficava com a régua errada — 8h — e o estimador inventava 4h de suplemento.
     */
    @Test
    fun `T6 - no modo PDF a projecao leva as horas previstas do proprio dia`() {
        val diasReais = listOf(
            diaPdf(1, 8 * 60, 20 * 60),   // 12h, sem tipo correspondente (o catálogo tem 13-21)
            diaPdf(2, 22 * 60, 8 * 60),   // 10h a atravessar a meia-noite
            diaPdf(3, 0, 0)               // folga do PDF
        )

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = LocalDate.of(2026, 9, 1).toEpochDay(),
            ateEpochDay = LocalDate.of(2026, 9, 3).toEpochDay(),
            aplicacoes = listOf(aplicacao),
            tiposTurno = tipos
        )

        assertEquals(listOf(12 * 60, 10 * 60, 0), projecao.map { it.minutosPrevistos })
        // O tipo derivado continua a servir para o chip e para saber o que é trabalho/folga.
        assertEquals(listOf<Long?>(T08H, T08H, FOLGA), projecao.map { it.tipoTurnoId })
    }

    /**
     * Linha de um dia do PDF que o utilizador editou: a origem passa a MANUAL e as horas são as
     * reais, mas o turno previsto pelo PDF fica guardado nas colunas `previsto*` (fase 29,
     * migração 23→24) — é isso que mantém o dia na escala depois da edição.
     */
    private fun diaEditado(
        dia: Int,
        tipoTurnoId: Long?,
        inicioMin: Int,
        fimMin: Int,
        previstoInicioMin: Int,
        previstoFimMin: Int
    ) = DiaReal(
        data = LocalDate.of(2026, 9, dia).toEpochDay(),
        tipoTurnoId = tipoTurnoId,
        inicioMin = inicioMin,
        fimMin = fimMin,
        pausaMin = 0,
        origem = "MANUAL",
        previstoInicioMin = previstoInicioMin,
        previstoFimMin = previstoFimMin,
        previstoPausaMin = 0
    )

    /** A regra do chip do EscalaViewModel (fase 29) para um dia que veio do PDF. */
    private fun chipDoDia(diaReal: DiaReal): TipoTurno? =
        diaReal.tipoTurnoId?.let { id -> tipos.firstOrNull { it.id == id } }
            ?: tipoDerivadoDoPdf(diaReal, tipos)

    /**
     * T7 (fase 29): editar um dia do PDF já não o tira da escala. A linha passa a MANUAL com as
     * horas e o tipo do utilizador, mas o previsto do PDF fica guardado e é ele que dá a régua
     * (12h do PDF, não as 8h registadas) e a categoria do dia.
     */
    @Test
    fun `T7 - dia do PDF editado continua na grelha e a regua e o previsto`() {
        val editado = diaEditado(
            dia = 2,
            tipoTurnoId = T08H,
            inicioMin = 13 * 60,
            fimMin = 21 * 60,        // 8h reais, como o utilizador registou
            previstoInicioMin = 8 * 60,
            previstoFimMin = 20 * 60 // 12h no PDF
        )
        val diasReais = listOf(diaPdf(1, 13 * 60, 21 * 60), editado)

        val dias = diasProjetadosPara(TIPO_ESCALA_PDF_MENSAL, diasReais, de, ate, emptyList())
        assertEquals(
            "o dia editado não desaparece da grelha",
            listOf(1, 2),
            dias.map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }
        )
        assertEquals("a projeção leva o tipo do registo; o chip decide", T08H, dias[1].tipoTurnoId)

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = emptyList(),
            tiposTurno = tipos
        )
        val doDiaEditado = projecao.first { it.epochDay == editado.data }
        assertEquals(
            "a régua é o turno do PDF, não as horas registadas",
            12 * 60,
            doDiaEditado.minutosPrevistos
        )
        assertEquals(
            "12h sem tipo correspondente cai no primeiro de trabalho",
            T08H,
            doDiaEditado.tipoTurnoId
        )
        assertEquals("o chip mostra o tipo do registo do utilizador", T08H, chipDoDia(editado)?.id)
    }

    /**
     * T8 (fase 29, auditoria ponto 2): folga do PDF trabalhada. A linha é MANUAL e traz um tipo de
     * trabalho (o registo do utilizador), mas o previsto do PDF é 0h — logo o dia projetado é
     * FOLGA e o estimador paga DESC_DESC a 200%. Sem o previsto, o tipo da linha ganhava e o dia
     * passava a contar como um dia de trabalho normal.
     */
    @Test
    fun `T8 - folga do PDF trabalhada projeta FOLGA e o chip mostra o registo`() {
        val folgaTrabalhada = diaEditado(
            dia = 5,
            tipoTurnoId = T08H,     // o utilizador registou uma tarde normal
            inicioMin = 13 * 60,
            fimMin = 21 * 60,
            previstoInicioMin = 0,
            previstoFimMin = 0      // no PDF era folga
        )

        val dias = diasProjetadosPara(
            TIPO_ESCALA_PDF_MENSAL,
            listOf(folgaTrabalhada),
            de,
            ate,
            emptyList()
        )
        assertEquals("o dia trabalhado na folga fica na grelha", 1, dias.size)

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = listOf(folgaTrabalhada),
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = emptyList(),
            tiposTurno = tipos
        )
        val diaProjetado = projecao.first { it.epochDay == folgaTrabalhada.data }
        assertEquals(
            "previsto de 0h é folga, mesmo com 8h registadas",
            FOLGA,
            diaProjetado.tipoTurnoId
        )
        assertEquals(0, diaProjetado.minutosPrevistos)
        assertEquals(
            "o mês do PDF vem completo: os dias que ele não lista são folgas",
            setembro.lengthOfMonth(),
            projecao.size
        )
        assertEquals(
            "o chip continua a mostrar o turno que o utilizador fez",
            T08H,
            chipDoDia(folgaTrabalhada)?.id
        )
        assertTrue(
            "e o mês não perde a escala",
            temEscalaAplicada(
                TIPO_ESCALA_PDF_MENSAL,
                listOf(folgaTrabalhada),
                emptyList(),
                setembro
            )
        )
    }

    /**
     * T9 (fase 29): um mês em que o utilizador editou todos os dias importados continua a ter
     * escala — todas as linhas são MANUAL, mas têm o previsto do PDF. Um registo manual que nunca
     * veio de um PDF continua fora da grelha do modo PDF.
     */
    @Test
    fun `T9 - mes com todos os dias editados continua com escala aplicada`() {
        val editados = (1..3).map { dia ->
            diaEditado(dia, T08H, 13 * 60, 21 * 60, 8 * 60, 20 * 60)
        }
        // Registo manual criado de raiz: não veio de PDF nenhum.
        val manual = dia(4, "MANUAL", 9 * 60, 17 * 60)

        val dias = diasProjetadosPara(
            TIPO_ESCALA_PDF_MENSAL,
            editados + manual,
            de,
            ate,
            emptyList()
        )
        assertEquals("só os dias que vieram do PDF", 3, dias.size)
        assertTrue(
            "sem nenhuma linha com origem PDF, o mês ainda tem escala",
            temEscalaAplicada(TIPO_ESCALA_PDF_MENSAL, editados, emptyList(), setembro)
        )
        assertFalse(
            "fora do mês do PDF não há escala",
            temEscalaAplicada(
                TIPO_ESCALA_PDF_MENSAL,
                editados,
                emptyList(),
                YearMonth.of(2026, 10)
            )
        )
    }

    /**
     * T10 (fase 29): os dias importados antes da migração 23→24 não têm `previsto*`. Aí continua
     * tudo como antes: a régua são as horas da própria linha, que num dia do PDF são as do PDF.
     */
    @Test
    fun `T10 - dia do PDF sem previsto usa as horas da linha`() {
        val legado = diaPdf(7, 8 * 60, 20 * 60)   // 12h, sem colunas previsto*

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = listOf(legado),
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = emptyList(),
            tiposTurno = tipos
        )
        assertEquals(12 * 60, projecao.first { it.epochDay == legado.data }.minutosPrevistos)
        assertEquals(T08H, projecao.first { it.epochDay == legado.data }.tipoTurnoId)
        assertEquals(T08H, chipDoDia(legado)?.id)
    }

    /**
     * T11 (fase 29, auditoria: as folgas do PDF não existem como linha): a projeção do estimador
     * enche o mês com as folgas implícitas, mas o calendário não muda — [diasProjetadosPara] /
     * [diasDoPdf] continuam a devolver só os dias que vieram do PDF, e é isso que mantém os dias
     * sem chip. Um mês sem nenhuma linha do PDF continua sem projeção nenhuma.
     */
    @Test
    fun `T11 - a projecao enche as folgas do mes do PDF e o calendario nao`() {
        val diasReais = listOf(
            diaPdf(1, 13 * 60, 21 * 60),
            diaPdf(2, 13 * 60, 21 * 60),
            dia(4, "MANUAL", 9 * 60, 17 * 60)   // registo de raiz num dia que o PDF não lista
        )

        val grelha = diasProjetadosPara(TIPO_ESCALA_PDF_MENSAL, diasReais, de, ate, emptyList())
        assertEquals(
            "o calendário continua só com os dias do PDF",
            listOf(1, 2),
            grelha.map { LocalDate.ofEpochDay(it.epochDay).dayOfMonth }
        )

        val projecao = projecaoParaEstimativa(
            tipoEscala = TIPO_ESCALA_PDF_MENSAL,
            diasReais = diasReais,
            deEpochDay = de,
            ateEpochDay = ate,
            aplicacoes = emptyList(),
            tiposTurno = tipos
        )
        assertEquals("o estimador vê o mês inteiro", setembro.lengthOfMonth(), projecao.size)
        assertEquals(
            "os dias que o PDF não lista são folgas com 0h previstas",
            List(setembro.lengthOfMonth() - 2) { FOLGA },
            projecao.filter { it.epochDay != diasReais[0].data && it.epochDay != diasReais[1].data }
                .map { it.tipoTurnoId }
        )
        assertTrue(
            "e nenhum deles ganha horas previstas",
            projecao.none { it.tipoTurnoId == FOLGA && it.minutosPrevistos != 0 }
        )

        // Mês sem uma única linha do PDF: projeção vazia, como sempre foi.
        val outubro = YearMonth.of(2026, 10)
        assertTrue(
            projecaoParaEstimativa(
                tipoEscala = TIPO_ESCALA_PDF_MENSAL,
                diasReais = diasReais,
                deEpochDay = outubro.atDay(1).toEpochDay(),
                ateEpochDay = outubro.atEndOfMonth().toEpochDay(),
                aplicacoes = emptyList(),
                tiposTurno = tipos
            ).isEmpty()
        )
    }
}
