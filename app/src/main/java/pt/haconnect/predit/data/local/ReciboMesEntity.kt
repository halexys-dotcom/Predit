package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cabeçalho do recibo de um mês, como o utilizador o introduziu.
 *
 * Unidades: 1/10000 € (ver domain/model/Dinheiro.kt) — o sufixo não aparece aqui
 * porque estes campos são cópia directa do recibo, não passos de cálculo.
 *
 * A chave é o mês ("2026-08"): um recibo por mês. Reintroduzir o mesmo mês substitui
 * o cabeçalho e as linhas (a Fase 8.2b.2b é que decide se avisa antes).
 */
@Entity(tableName = "recibo_mes")
data class ReciboMesEntity(
    @PrimaryKey val anoMes: String,      // "2026-08"
    val dataFecho: Long,                 // epochDay (do cabeçalho do recibo)
    val vencimentoBase: Long,            // 1/10000 €
    val vencimentoHora: Long,            // 1/10000 €
    val numDiasUteis: Int,
    val irsRetidoAno: Long,              // acumulado do ano (cabeçalho)
    val totalAbonos: Long,
    val totalDescontos: Long,
    val liquido: Long,
    val nota: String?,
    val dataCriacao: Long                // System.currentTimeMillis()
)
