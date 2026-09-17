package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Município e o seu feriado municipal. Tabela de catálogo, semeada em PreditApplication:
 * o id é fixo (não é autoGenerate) para o contrato poder apontar-lhe por `municipioId`.
 *
 * [verificado] = false significa dados por confirmar: a UI mostra o aviso e o cálculo do
 * recibo usa-os na mesma (o aviso é a salvaguarda, não um bloqueio).
 */
@Entity(tableName = "municipio")
data class MunicipioEntity(
    @PrimaryKey val id: Int,
    val nome: String,
    val distrito: String,
    val regiao: String,         // CONTINENTE | ACORES | MADEIRA
    val feriadoDia: Int,        // 0 se não definido
    val feriadoMes: Int,        // 0 se não definido
    val feriadoNome: String,
    val verificado: Boolean     // false = dados por confirmar
)
