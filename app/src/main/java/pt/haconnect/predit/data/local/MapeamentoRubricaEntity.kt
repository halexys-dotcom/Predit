package pt.haconnect.predit.data.local

import androidx.room.Entity

/**
 * Mapeamento entre o código de uma rubrica tal como vem no recibo (PDF de cada empresa) e o
 * código interno do catálogo (o `codigo` de [RubricaEntity], ex.: VENC, SUP_ALIM, OUTROS).
 *
 * A chave é composta (empresa, codigoOriginal): a mesma designação pode valer coisas
 * diferentes em empresas diferentes, mas dentro de uma empresa um código original só pode
 * apontar para um destino. Sem id auto-gerado — a tabela é pequena e é o parser que a
 * consulta.
 *
 * Fase 28a: só infraestrutura. Quem preenche a tabela é o parser (Fase 28b).
 */
@Entity(
    tableName = "mapeamento_rubrica",
    primaryKeys = ["empresa", "codigoOriginal"]
)
data class MapeamentoRubricaEntity(
    val empresa: String,           // ex.: "SECURITAS", "ICTS PORTUGAL"
    val codigoOriginal: String,    // tal como vem no PDF
    val codigoInterno: String      // Rubrica.codigo
)
