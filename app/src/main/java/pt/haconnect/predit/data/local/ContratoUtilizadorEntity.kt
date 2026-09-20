package pt.haconnect.predit.data.local

import androidx.room.ColumnInfo
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
    val primeiroArranqueConcluido: Boolean = false,
    // Região fiscal, para escolher as tabelas de retenção. Guardada como texto.
    // O defaultValue é obrigatório: a coluna é adicionada por ALTER TABLE com DEFAULT.
    @ColumnInfo(defaultValue = "CONTINENTE")
    val regiao: String = "CONTINENTE",    // CONTINENTE | ACORES | MADEIRA
    // Município escolhido (municipio.id), para o feriado municipal (Fase 10).
    // Nulo = não escolhido. A coluna é nullable e sem DEFAULT: vem de ALTER TABLE simples.
    val municipioId: Int? = null,
    // 13a B.2 - chave da categoria CCT (parametros_cct.codigoCategoria). O nome de exibição
    // continua em categoriaNivel: aqui guarda-se a chave, que é o que o recibo procura.
    // Coluna com DEFAULT, por isso o defaultValue é obrigatório (ver comentário em regiao).
    @ColumnInfo(defaultValue = "APAA")
    val categoriaCodigo: String = "APAA"
)
