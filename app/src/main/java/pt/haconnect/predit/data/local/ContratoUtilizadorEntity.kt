package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contrato_utilizador")
data class ContratoUtilizadorEntity(
    @PrimaryKey val id: Int = 1,
    val categoriaNivel: String,
    val dataAdmissao: Long?,              // epochDay — nullable na BD
    val regimeHorario: String,            // NORMAL | ADAPTABILIDADE
    val horarioSemanalH: Int,
    val numeroDependentes: Int,
    val estadoCivil: String,
    val titulares: Int,
    val primeiroArranqueConcluido: Boolean = false
)
