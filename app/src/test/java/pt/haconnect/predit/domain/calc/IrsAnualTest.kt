package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 21 — o motor do IRS anual. Prova quatro coisas:
 *
 * 1. A dedução específica da categoria A (8,54 × IAS = 4 587,09 €) e o rendimento coletável.
 * 2. A isenção do IRS Jovem no ano: a percentagem sobre o coletável, com o teto de 55 × IAS
 *    (29 542,15 €) a mandar quando a percentagem dá mais do que isso.
 * 3. Os escalões progressivos: cada fatia leva a taxa do seu escalão, cada uma arredondada ao
 *    cêntimo, e a diferença final é coleta − retenções (positiva a pagar, negativa a receber).
 * 4. A tributação conjunta (Fase 21c): o quociente conjugal do art. 69.º, com a dedução
 *    específica e o IRS Jovem aplicados a cada titular antes de agregar os rendimentos.
 *
 * O ano de referência é 2026 — IAS 537,13 € = 5 371 300 unidades de 1/10000 €, como nas tabelas
 * de retenção semeadas. O rendimento bruto é o do ano (mensal × 12 ou × 14), em unidades de
 * 1/10000 €: 200 000 000 = 20 000,00 €.
 */
class IrsAnualTest {

    private fun estimativa(
        bruto: Long,
        retencoes: Long = 0L,
        percentagem: Int? = null
    ): ResultadoIrsAnual = calcularIrsAnual(
        rendimentoBrutoAnual = bruto,
        retencoesEfetuadas = retencoes,
        ano = 2026,
        escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
        percentagemIrsJovem = percentagem
    )

    /** O mesmo pelo lado da conjunta: dois titulares e a isenção do titular 1 já calculada. */
    private fun conjunta(
        titular1: Long,
        titular2: Long,
        retencoes: Long = 0L,
        isencaoJovemTitular1: Long = 0L
    ): ResultadoIrsAnual = calcularIrsAnualConjunta(
        rendimentoTitular1 = titular1,
        rendimentoTitular2 = titular2,
        retencoesEfetuadas = retencoes,
        ano = 2026,
        escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
        isencaoIrsJovemTitular1 = isencaoJovemTitular1
    )

    /** O mesmo que [estimativa], mas com o mínimo de existência ligado (o Simulador liga-o). */
    private fun estimativaComMinimo(
        bruto: Long,
        retencoes: Long = 0L,
        percentagem: Int? = null,
        deducoesColeta: Long = 0L
    ): ResultadoIrsAnual = calcularIrsAnual(
        rendimentoBrutoAnual = bruto,
        retencoesEfetuadas = retencoes,
        ano = 2026,
        escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
        percentagemIrsJovem = percentagem,
        deducoesColeta = deducoesColeta,
        aplicarMinimoExistencia = true
    )

    /** A conjunta com o mínimo de existência ligado. */
    private fun conjuntaComMinimo(
        titular1: Long,
        titular2: Long,
        retencoes: Long = 0L
    ): ResultadoIrsAnual = calcularIrsAnualConjunta(
        rendimentoTitular1 = titular1,
        rendimentoTitular2 = titular2,
        retencoesEfetuadas = retencoes,
        ano = 2026,
        escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
        isencaoIrsJovemTitular1 = 0L,
        aplicarMinimoExistencia = true
    )

    @Test
    fun `T1 - coleta soma os escaloes que o rendimento atravessa`() {
        // 20 000,00 € de rendimento bruto do ano.
        // Dedução específica: 854 × 5 371 300 ÷ 100 = 45 870 902 = 4 587,0902 €.
        // Coletável: 200 000 000 − 45 870 902 = 154 129 098 = 15 412,9098 €.
        val resultado = estimativa(bruto = 200_000_000L)

        assertEquals(45_870_902L, resultado.deducaoEspecifica)
        assertEquals(154_129_098L, resultado.rendimentoColetavel)

        // Fatias: 83 420 000 × 12,20 % = 10 177 240 (exato)
        //         42 450 000 × 15,20 % =  6 452 400 (exato)
        //         28 259 098 × 20,70 % = 5 849 633,286 → 5 849 633
        //                                total = 22 479 273 = 2 247,9273 €
        assertEquals(22_479_273L, resultado.coletaDevida)

        // Sem retenções registadas, é tudo a pagar na entrega da declaração.
        assertEquals(22_479_273L, resultado.diferenca)
    }

    @Test
    fun `T2 - isencao do IRS Jovem e a percentagem sobre o coletavel`() {
        // Mesmo rendimento com 25 % de isenção (8.º ao 10.º ano de obtenção).
        val resultado = estimativa(bruto = 200_000_000L, percentagem = 25)

        // 154 129 098 × 25 % = 38 532 274,5 → 38 532 275 = 3 853,2275 €.
        assertEquals(38_532_275L, resultado.isencaoIrsJovem)

        // A percentagem manda porque dá menos que o teto dos 55 × IAS (29 542,15 €).
        assertEquals(154_129_098L - 38_532_275L, resultado.coletavelAposJovem)
    }

    @Test
    fun `T3 - teto anual de 55 IAS corta a isencao`() {
        // 140 000,00 € de rendimento bruto: coletável 1 354 129 098 (135 412,9098 €).
        // 25 % daria 338 532 275 (33 853,2275 €), acima do teto legal de 55 × 5 371 300
        // = 295 421 500 = 29 542,15 €, por isso a isenção é o teto.
        val resultado = estimativa(bruto = 1_400_000_000L, percentagem = 25)

        assertEquals(1_400_000_000L - 45_870_902L, resultado.rendimentoColetavel)
        assertEquals(295_421_500L, resultado.isencaoIrsJovem)
        assertEquals(1_354_129_098L - 295_421_500L, resultado.coletavelAposJovem)
    }

    @Test
    fun `T4 - retencoes acima da coleta dao diferenca negativa`() {
        // 20 000,00 € de rendimento, 3 000,00 € retidos no ano (14 recibos de ~214,29 €).
        val resultado = estimativa(bruto = 200_000_000L, retencoes = 30_000_000L)

        // Coleta 22 479 273 (ver T1) − 30 000 000 = −7 520 727 = −752,0727 €: a receber.
        assertEquals(22_479_273L, resultado.coletaDevida)
        assertEquals(-7_520_727L, resultado.diferenca)
    }

    @Test
    fun `T5 - so o primeiro escalao e sem dados nao se inventa coleta`() {
        // 10 000,00 € de rendimento bruto do ano: coletável 54 129 098 (5 412,9098 €), todo ele
        // dentro do 1.º escalão (8 342,00 €): 54 129 098 × 12,20 % = 6 603 749,956 → 6 603 750.
        val resultado = estimativa(bruto = 100_000_000L)

        assertEquals(54_129_098L, resultado.rendimentoColetavel)
        assertEquals(6_603_750L, resultado.coletaDevida)
        assertEquals(6_603_750L, resultado.diferenca)

        // Coletável abaixo da dedução específica: não há coleta a inventar.
        val semColetavel = estimativa(bruto = 40_000_000L)
        assertEquals(0L, semColetavel.rendimentoColetavel)
        assertEquals(0L, semColetavel.coletaDevida)

        // Sem escalões não há coleta — só a diferença contra o que já foi retido.
        val semEscaloes = calcularIrsAnual(
            rendimentoBrutoAnual = 100_000_000L,
            retencoesEfetuadas = 6_603_750L,
            ano = 2026,
            escaloes = emptyList(),
            percentagemIrsJovem = null
        )
        assertEquals(0L, semEscaloes.coletaDevida)
        assertEquals(-6_603_750L, semEscaloes.diferenca)

        // Ano sem IAS tabelado: sem dedução específica (é o IAS que a fixa, não o rendimento).
        assertEquals(0L, deducaoEspecificaCategoriaA(2027))
        val outroAno = calcularIrsAnual(
            rendimentoBrutoAnual = 100_000_000L,
            retencoesEfetuadas = 0L,
            ano = 2027,
            escaloes = ESCALOES_ANUAIS_CONTINENTE_2026,
            percentagemIrsJovem = null
        )
        assertEquals(100_000_000L, outroAno.rendimentoColetavel)
    }

    @Test
    fun `T6 - conjunta com rendimentos iguais dobra a coleta do quociente`() {
        // 20 000,00 € + 20 000,00 €, cada um com a sua dedução específica antes de agregar:
        // 154 129 098 + 154 129 098 = 308 258 196 = 30 825,8196 €.
        // Quociente 154 129 098 (15 412,9098 €) → a coleta do T1 (22 479 273) × 2.
        val resultado = conjunta(titular1 = 200_000_000L, titular2 = 200_000_000L)

        assertEquals(400_000_000L, resultado.rendimentoBruto)
        assertEquals(91_741_804L, resultado.deducaoEspecifica)
        assertEquals(308_258_196L, resultado.rendimentoColetavel)
        // 44 958 546 = 4 495,8546 € → apresenta-se 4 495,85 €: o cêntimo arredonda-se no fim
        // (2 × 2 247,93 daria 4 495,86 € só porque já vinha arredondado de trás).
        assertEquals(44_958_546L, resultado.coletaDevida)
        assertEquals(44_958_546L, resultado.diferenca)
    }

    @Test
    fun `T7 - conjunta com rendimentos desiguais da a mesma coleta`() {
        // 30 000,00 € + 10 000,00 €: 254 129 098 + 54 129 098 = 308 258 196 — o mesmo
        // agregado do T6, logo o mesmo quociente e a mesma coleta.
        val desiguais = conjunta(titular1 = 300_000_000L, titular2 = 100_000_000L)
        val iguais = conjunta(titular1 = 200_000_000L, titular2 = 200_000_000L)

        assertEquals(308_258_196L, desiguais.rendimentoColetavel)
        assertEquals(iguais.coletaDevida, desiguais.coletaDevida)

        // O ganho do método: o mesmo agregado (400 000,00 €) tratado como um só titular
        // pagaria 80 009 098 = 8 000,9098 €, muito acima dos 44 958 546 = 4 495,8546 €.
        val numSoTitular = estimativa(bruto = 400_000_000L)
        assertEquals(80_009_098L, numSoTitular.coletaDevida)
        assertTrue(desiguais.coletaDevida < numSoTitular.coletaDevida)
    }

    @Test
    fun `T8 - conjunta com IRS Jovem isenta so o titular que tem o regime`() {
        // A isenção do titular 1 sai do coletável dele: 154 129 098 × 25 % = 38 532 274,5
        // → 38 532 275 (o mesmo número do T2, com a conta isolada numa função própria).
        val isencao = calcularIsencaoIrsJovem(
            rendimentoBrutoAnual = 200_000_000L,
            percentagem = 25,
            ano = 2026
        )
        assertEquals(38_532_275L, isencao)

        val resultado = conjunta(
            titular1 = 200_000_000L,
            titular2 = 200_000_000L,
            isencaoJovemTitular1 = isencao
        )

        // Titular 1: 154 129 098 − 38 532 275 = 115 596 823 (11 559,6823 €).
        // Titular 2: 154 129 098 (15 412,9098 €), sem isenção nenhuma.
        assertEquals(38_532_275L, resultado.isencaoIrsJovem)
        assertEquals(269_725_921L, resultado.rendimentoColetavel)

        // Quociente 134 862 960 (13 486,2960 €) → 10 177 240 + 6 452 400 + 1 861 543
        // = 18 491 183 → coleta final 36 982 366 = 3 698,2366 €.
        assertEquals(36_982_366L, resultado.coletaDevida)
    }

    @Test
    fun `T9 - a deducao especifica e aplicada a cada titular antes de agregar`() {
        val resultado = conjunta(titular1 = 200_000_000L, titular2 = 200_000_000L)

        // 9 174,18 € = 2 × 4 587,09 €: cada titular tem direito à sua dedução da categoria A.
        assertEquals(91_741_804L, resultado.deducaoEspecifica)

        // E prova-se pelo coletável: se a dedução fosse uma só sobre o agregado, seria
        // 400 000 000 − 45 870 902 = 354 129 098. É o dobro disso menos as duas deduções:
        // 400 000 000 − 2 × 45 870 902 = 308 258 196.
        assertEquals(400_000_000L - 2L * 45_870_902L, resultado.rendimentoColetavel)

        // Um titular sem rendimento não fica com dedução negativa nem tira nada ao outro:
        // o agregado é o coletável do titular 1 sozinho (154 129 098, ver T1).
        val semRendimentoNoTitular2 = conjunta(titular1 = 200_000_000L, titular2 = 0L)
        assertEquals(154_129_098L, semRendimentoNoTitular2.rendimentoColetavel)
    }

    @Test
    fun `T10 - coleta individual sobre o agregado so serve de comparacao`() {
        // Os 400 000,00 € do T7 tratados como um só titular: uma dedução só e a tabela toda por
        // cima. É o número que o ecrã mostra por baixo do «IRS devido» para dizer quanto o
        // quociente conjugal poupa (80 009 098 − 44 958 546 = 35 050 552 = 3 505,0552 €).
        val agregado = 400_000_000L

        assertEquals(80_009_098L, coletaIndividualSobreAgregado(agregado, 2026, ESCALOES_ANUAIS_CONTINENTE_2026))

        // Sem IRS Jovem e com o coletável todo dentro dos escalões, tem de dar exatamente o
        // que o motor individual dá — a função nova não pode divergir dele.
        assertEquals(
            estimativa(bruto = agregado).coletaDevida,
            coletaIndividualSobreAgregado(agregado, 2026, ESCALOES_ANUAIS_CONTINENTE_2026)
        )

        // Sem escalões não há coleta, como no resto do motor.
        assertEquals(0L, coletaIndividualSobreAgregado(agregado, 2026, emptyList()))
    }

    @Test
    fun `T11 - minimo de existencia abaixo de V e limitado pelo rendimento`() {
        // 12 000,00 €, abaixo de V = 12 880 €: alínea a) do n.º 2.
        // 12 880 − 4 587,09 (dedução) − 2 049,18 (250 € ÷ 12,2 %) = 6 243,73 €.
        val deducao = deducaoEspecificaCategoriaA(2026)
        assertEquals(62_437_295L, abatimentoMinimoExistencia(120_000_000L, deducao, 2026))

        val resultado = estimativaComMinimo(120_000_000L)
        assertEquals(62_437_295L, resultado.abatimentoMinimoExistencia)
        assertEquals(74_129_098L, resultado.rendimentoColetavel)          // 12 000 − 4 587,09
        assertEquals(11_691_803L, resultado.coletavelAposAbatimento)
        // 11 691 803 × 12,20 % = 1 426 399,966 → 1 426 400 = 142,64 €.
        assertEquals(1_426_400L, resultado.coletaDevida)

        // A 10 000,00 € o n.º 2 d) já manda: a alínea a) daria 6 243,73 €, mas o abatimento não
        // pode passar de R − deduções específicas = 10 000 − 4 587,09 = 5 412,91 €.
        assertEquals(54_129_098L, abatimentoMinimoExistencia(100_000_000L, deducao, 2026))
    }

    @Test
    fun `T12 - minimo de existencia entre V e L`() {
        // 13 500,00 €, entre V (12 880 €) e L (14 628 €): alínea b) do n.º 2.
        // 12 880 − 2,60 × 620 − 4 587,09 − 2 049,18 = 4 631,73 €.
        val deducao = deducaoEspecificaCategoriaA(2026)
        assertEquals(46_317_295L, abatimentoMinimoExistencia(135_000_000L, deducao, 2026))

        val resultado = estimativaComMinimo(135_000_000L)
        assertEquals(46_317_295L, resultado.abatimentoMinimoExistencia)
        assertEquals(89_129_098L, resultado.rendimentoColetavel)          // 13 500 − 4 587,09
        assertEquals(42_811_803L, resultado.coletavelAposAbatimento)
        // 42 811 803 × 12,20 % = 5 223 040,466 → 5 223 040 = 522,30 €.
        assertEquals(5_223_040L, resultado.coletaDevida)
    }

    @Test
    fun `T13 - rendimento acima de L nao tem minimo de existencia`() {
        // 18 437,10 €, o caso real: acima de L (14 628 €) a alínea c) já dá negativo, e o
        // n.º 4 a) também exclui (18 437,10 > 2,2 × 14 × IAS = 16 543,60 €).
        assertEquals(
            0L,
            abatimentoMinimoExistencia(184_371_000L, deducaoEspecificaCategoriaA(2026), 2026)
        )

        val resultado = estimativaComMinimo(184_371_000L)
        assertEquals(0L, resultado.abatimentoMinimoExistencia)
        assertEquals(resultado.rendimentoColetavel, resultado.coletavelAposAbatimento)
        // Sem abatimento o cálculo é o do motor simples (ver T1 com 20 000 €): 1 924,41 €.
        assertEquals(estimativa(bruto = 184_371_000L).coletaDevida, resultado.coletaDevida)
        assertEquals(19_244_070L, resultado.coletaDevida)

        // Deduções à coleta de 1 500,00 €: entram depois da coleta e baixam o que se entrega.
        val comDeducoes = estimativaComMinimo(184_371_000L, deducoesColeta = 15_000_000L)
        assertEquals(15_000_000L, comDeducoes.deducoesColetaAplicadas)
        assertEquals(19_244_070L - 15_000_000L, comDeducoes.coletaAposDeducoes)
        assertEquals(19_244_070L - 15_000_000L, comDeducoes.diferenca)

        // Deduções acima da coleta: nunca devolvem dinheiro — a coleta fica em 0.
        val semColeta = estimativaComMinimo(184_371_000L, deducoesColeta = 30_000_000L)
        assertEquals(0L, semColeta.coletaAposDeducoes)
        assertEquals(0L, semColeta.diferenca)
    }

    @Test
    fun `T14 - minimo de existencia nunca passa do rendimento menos a deducao`() {
        // 9 000,00 €: a alínea a) daria 6 243,73 €, mas o n.º 2 d) limita ao máximo legal,
        // que aqui é tudo o que sobra do rendimento: 9 000 − 4 587,09 = 4 412,91 €.
        val r = 90_000_000L
        assertEquals(44_129_098L, abatimentoMinimoExistencia(r, deducaoEspecificaCategoriaA(2026), 2026))

        val resultado = estimativaComMinimo(r)
        assertEquals(44_129_098L, resultado.abatimentoMinimoExistencia)
        assertEquals(0L, resultado.coletavelAposAbatimento)
        assertEquals(0L, resultado.coletaDevida)

        // Abaixo da dedução específica o máximo é 0: não há abatimento a inventar.
        assertEquals(0L, abatimentoMinimoExistencia(40_000_000L, deducaoEspecificaCategoriaA(2026), 2026))
    }

    @Test
    fun `T15 - conjunta aplica o minimo de existencia a cada titular`() {
        // R1 = R2 = 12 000,00 €: 6 243,73 € em cada um, antes de agregar (o máximo legal de
        // cada titular é 7 412,91 €, por isso não corta).
        val resultado = conjuntaComMinimo(titular1 = 120_000_000L, titular2 = 120_000_000L)
        assertEquals(62_437_295L * 2L, resultado.abatimentoMinimoExistencia)
        assertEquals(11_691_803L * 2L, resultado.rendimentoColetavel)
        // Quociente 11 691 803 (1 169,1803 €) → 1 426 400 × 2 = 2 852 800 = 285,28 €.
        assertEquals(2_852_800L, resultado.coletaDevida)

        // Com 13 500,00 € cada um, o abatimento já não esgota o coletável: 4 631,73 € por
        // titular deixam 42 811,803 € a cada um (ver T12).
        val semZerar = conjuntaComMinimo(titular1 = 135_000_000L, titular2 = 135_000_000L)
        assertEquals(46_317_295L * 2L, semZerar.abatimentoMinimoExistencia)
        assertEquals(42_811_803L * 2L, semZerar.rendimentoColetavel)
        assertEquals(10_446_080L, semZerar.coletaDevida)

        // Sem mínimo de existência o mesmo casal pagaria 22 090 046 = 2 209,0046 €.
        assertEquals(22_090_046L, conjunta(titular1 = 135_000_000L, titular2 = 135_000_000L).coletaDevida)

        // O n.º 4 a) dobra com dois titulares: 2 × 16 543,60 €. Acima disso não há abatimento
        // para nenhum deles, mesmo que um deles renda pouco.
        val acima = conjuntaComMinimo(titular1 = 340_000_000L, titular2 = 10_000_000L)
        assertEquals(0L, acima.abatimentoMinimoExistencia)
    }
}
