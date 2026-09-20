package pt.haconnect.predit.data.local

/**
 * Tabela salarial do CCT: as 11 categorias de segurança × 2 vigências (2025 e 2026).
 *
 * 13b: a lista anterior tinha 24 categorias (a tabela do CCT inteira). Ficaram só as de
 * segurança — as 13 que saíram não voltam a ser semeadas numa instalação limpa e são apagadas
 * da BD existente pela migração 15→16.
 *
 * Unidade: 1/10000 € (domain/model/Dinheiro.kt).
 *
 * ATENÇÃO À ESCALA — já houve um erro aqui (Fase 8.1). A tabela de origem dá os vencimentos
 * em 1/100 € (1 617,89 € = 161789); na app são precisos mais dois zeros (16 178 900). A
 * alimentação, essa, já vinha na unidade da app (74 200 = 7,42 €/dia) e não se multiplica.
 * Confirmado contra as duas linhas APAA que já existiam na BD antes desta fase:
 * 10 760 000 / 74 200 (2025) e 11 379 800 / 78 500 (2026) — os mesmos valores que aqui estão.
 *
 * O subsídio de transporte é único no CCT (igual para a APA-A, logo para todas as categorias)
 * e o subsídio de função só existe no Team Leader (43,00 €/mês).
 */
data class LinhaCategoriaCCT(
    val codigo: String,
    val nivel: String,
    val nome: String,
    val vencimento2025Mil: Int,
    val vencimento2026Mil: Int,
    val alimentacao2025Mil: Int,
    val alimentacao2026Mil: Int,
    val subsidioFuncaoMil: Long = 0L
)

/** Subsídio de transporte: 49,25 € (2025) e 52,09 € (2026), igual em todas as categorias. */
const val SUB_TRANSPORTE_2025_MIL = 492_500
const val SUB_TRANSPORTE_2026_MIL = 520_900

/** Horário semanal de referência do CCT. */
const val HORARIO_SEMANAL_CCT = 40

@Suppress("LongLine", "MaxLineLength")
val CATEGORIAS_CCT: List<LinhaCategoriaCCT> = listOf(
    LinhaCategoriaCCT("GESTOR_AER", "III", "Gestor Aeroportuário", 14_848_000, 15_703_200, 74_200, 78_500),
    LinhaCategoriaCCT("SUPERVISOR", "V", "Supervisor Aeroportuário", 13_529_000, 14_308_300, 74_200, 78_500),
    LinhaCategoriaCCT("VTV", "VII", "Vigilante Transporte de Valores", 13_380_600, 14_151_300, 85_300, 90_200),
    LinhaCategoriaCCT("CHEFE_BRIG", "IX", "Chefe de Brigada / Supervisor", 12_422_700, 13_138_200, 74_200, 78_500),
    LinhaCategoriaCCT("CHEFE_GRUPO", "X", "Chefe de Grupo Aeroportuário", 12_137_600, 12_836_700, 74_200, 78_500),
    LinhaCategoriaCCT("ENCARREGADO", "XI", "Encarregado", 11_953_800, 12_642_300, 74_200, 78_500),
    LinhaCategoriaCCT("APAA", "XIII", "Vigilante Aeroportuário/APA-A", 10_760_000, 11_379_800, 74_200, 78_500),
    LinhaCategoriaCCT("VIGIL_CHEFE", "XIV", "Vigilante Chefe / Controlador", 10_632_700, 11_245_100, 74_200, 78_500),
    LinhaCategoriaCCT("OPER_VALORES", "XV", "Operador de Valores", 10_342_300, 10_938_000, 76_400, 80_800),
    LinhaCategoriaCCT("VIGILANTE", "XIX", "Vigilante", 9_606_200, 10_159_500, 74_200, 78_500),
    // Única categoria com subsídio de função: 52,46 €/mês (o "Chefe de Equipa Aeroportuário" do
    // Anexo IV do CCT; a app usa 22 dias para o prorratear).
    // "XXX" não é nível romano: é a posição operacional da app, que o picker mostra no fim.
    LinhaCategoriaCCT("TEAM_LEADER", "XXX", "Team Leader Aeroportuário", 10_760_000, 11_379_800, 74_200, 78_500, 524_600L)
)

/** A linha como parâmetro salarial vigente em 2025 (`validoDe` = 2025-01-01). */
fun LinhaCategoriaCCT.para2025(validoDe: Long): ParametrosCCTEntity = ParametrosCCTEntity(
    validoDe = validoDe,
    vencimentoBaseMil = vencimento2025Mil,
    subAlimentacaoDiaMil = alimentacao2025Mil,
    subTransporteMesMil = SUB_TRANSPORTE_2025_MIL,
    horarioSemanalReferencia = HORARIO_SEMANAL_CCT,
    codigoCategoria = codigo,
    nivelCCT = nivel,
    nomeCategoria = nome,
    subsidioFuncaoMil = subsidioFuncaoMil
)

/** A linha como parâmetro salarial vigente em 2026 (`validoDe` = 2026-01-01). */
fun LinhaCategoriaCCT.para2026(validoDe: Long): ParametrosCCTEntity = ParametrosCCTEntity(
    validoDe = validoDe,
    vencimentoBaseMil = vencimento2026Mil,
    subAlimentacaoDiaMil = alimentacao2026Mil,
    subTransporteMesMil = SUB_TRANSPORTE_2026_MIL,
    horarioSemanalReferencia = HORARIO_SEMANAL_CCT,
    codigoCategoria = codigo,
    nivelCCT = nivel,
    nomeCategoria = nome,
    subsidioFuncaoMil = subsidioFuncaoMil
)
