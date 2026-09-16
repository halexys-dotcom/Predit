package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tipos de cálculo das rubricas do recibo (guardados como texto na BD). */
object TipoCalculo {
    const val FIXO = "FIXO"       // valor fixo mensal (vencimento, subsídios)
    const val HORAS = "HORAS"     // derivado das horas reais (noturnas, suplementares)
    const val MANUAL = "MANUAL"       // o utilizador introduz; a app não estima
    const val DERIVADO = "DERIVADO"   // desconto calculado sobre as bases de incidência
}

/**
 * Catálogo de rubricas do recibo.
 * incide* = base de incidência para Segurança Social, IRS e sindicato.
 * ativaConferencia é configurável pelo utilizador (liga/desliga na Fase 8.3).
 */
@Entity(tableName = "rubrica")
data class RubricaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codigo: String,               // "VENC", "HNOT", ...
    val nome: String,                 // "Vencimento", "Horas noturnas", ...
    val incideSS: Boolean,
    val incideIRS: Boolean,
    val incideSindicato: Boolean,
    val tipoCalculo: String,          // TipoCalculo.FIXO | .HORAS | .MANUAL | .DERIVADO
    val ativaConferencia: Boolean,
    val ordem: Int
)
