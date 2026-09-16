package pt.haconnect.predit.data.local

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
    val horarioSemanalReferencia: Int
)
