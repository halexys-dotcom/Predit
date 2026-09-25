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
    val categoriaCodigo: String = "APAA",
    // Fase 19: modo de escala escolhido no Contrato — ROTACAO (o ciclo projeta os meses) ou
    // PDF_MENSAL (os chips do calendário vêm dos dia_real importados do PDF).
    // Coluna TEXT NOT NULL DEFAULT 'ROTACAO' (acrescentada por ALTER TABLE na migração 17→18),
    // por isso o defaultValue é obrigatório — ver o comentário em regiao.
    @ColumnInfo(defaultValue = "ROTACAO")
    val tipoEscala: String = "ROTACAO",
    // Fase 20: IRS Jovem. O regime é opcional — `aplicarIrsJovem` liga-o e os dois anos são
    // os únicos dados que o motor precisa (domain/calc/IrsJovem.kt). Nulos = por preencher.
    // Colunas nullable e sem DEFAULT, como o municipioId: quem nunca os preencheu fica com NULL.
    val anoNascimento: Int? = null,
    val anoPrimeiroRendimento: Int? = null,
    // Coluna INTEGER NOT NULL DEFAULT 0 (acrescentada por ALTER TABLE na migração 18→19),
    // por isso o defaultValue é obrigatório — ver o comentário em regiao.
    @ColumnInfo(defaultValue = "0")
    val aplicarIrsJovem: Boolean = false
)
