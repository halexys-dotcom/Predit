package pt.haconnect.predit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Parâmetros salariais do CCT, versionados por data de vigência (epochDay).
 *
 * Unidade: 1/10000 € (ver domain/model/Dinheiro.kt) — o sufixo "Mil" vem do comando
 * da Fase 8.1, mas a escala é 1/10000 (11 379 800 = 1 137,98 €).
 *
 * 2025 (validoDe 2025-01-01): base 1 076,00 € · alimentação 7,42 €/dia · transporte 49,25 €/mês
 * 2026 (validoDe 2026-01-01): base 1 137,98 € · alimentação 7,85 €/dia · transporte 52,09 €/mês
 */
@Entity(tableName = "parametros_cct")
data class ParametrosCCTEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val validoDe: Long,                   // epochDay
    val vencimentoBaseMil: Int,
    val subAlimentacaoDiaMil: Int,
    val subTransporteMesMil: Int,
    val horarioSemanalReferencia: Int,
    // 13a B.1 - categoria CCT a que esta linha se refere. A tabela passa a ter uma linha por
    // (codigoCategoria, validoDe): 24 categorias x 2 vigências = 48 linhas.
    // Os defaultValue são obrigatórios: as colunas entram por ALTER TABLE com DEFAULT e o Room
    // compara-os na validação do esquema (MigracaoTest). Os valores por omissão são os da
    // categoria APAA/Sigla XIII, que era a única que existia antes desta fase.
    @ColumnInfo(defaultValue = "APAA")
    val codigoCategoria: String = "APAA",
    @ColumnInfo(defaultValue = "XIII")
    val nivelCCT: String = "XIII",
    @ColumnInfo(defaultValue = "Vigilante Aeroportuário/APA-A")
    val nomeCategoria: String = "Vigilante Aeroportuário/APA-A",
    /**
     * Subsídio de função mensal (1/10000 €). Zero em todas as categorias excepto o Team Leader
     * (43,00 €). Calculado em domain/calc/EstimadorRecibo.kt sobre 22 dias.
     */
    @ColumnInfo(defaultValue = "0")
    val subsidioFuncaoMil: Long = 0L
)
