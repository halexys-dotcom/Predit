package pt.haconnect.predit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Uma linha por ano/região/categoria/tabela/escalão das tabelas de retenção na fonte.
 *
 * Unidades: 1/10000 € (igual a tudo o resto — ver domain/model/Dinheiro.kt).
 * taxaBasisPoints: taxa marginal máxima × 100000 (0,212 → 21200).
 * formulaComposta: true nos escalões em que a parcela a abater é a fórmula
 * taxa × k × (X − R) em vez de um valor fixo.
 * coeficienteK: o k dessa fórmula × 10000 (2,6 → 26000); 0 nos escalões de parcela fixa.
 * limiteX: o X dessa fórmula em 1/10000 € (1 273,85 € → 12_738_500); 0 fora da fórmula.
 *
 * Perfis das tabelas (metadado, não vai a coluna — vem dos XLSX oficiais):
 *   I  não casado sem dependentes OU casado 2 titulares
 *   II não casado com um ou mais dependentes
 *   III casado, único titular
 *   IV  (I) com pessoa com deficiência          V  (II) com deficiência
 *   VI  casado 2 titulares com dependentes, com deficiência
 *   VII (III) com deficiência
 *   VIII a XI — pensões, mesmos perfis de I, III, IV e VII
 */
@Entity(tableName = "tabela_irs")
data class TabelaIRSEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ano: Int,                    // 2026
    val regiao: String,              // CONTINENTE | ACORES | MADEIRA
    val categoria: String,           // TRABALHO | PENSOES
    val tabelaNumero: Int,           // 1..11 (I..XI)
    val ordemEscalao: Int,           // 0..N, ordem crescente por limiteAte
    val limiteAte: Long,             // unidades 1/10000 (920,00 € → 9_200_000)
    val taxaBasisPoints: Int,        // 0,212 → 21200
    val parcelaAbater: Long,         // unidades 1/10000
    val parcelaAdicionalDep: Long,   // unidades 1/10000
    val formulaComposta: Boolean,    // true nos primeiros escalões com taxa × k × (X − R)
    val coeficienteK: Long = 0L,     // k × 10000 (só nos escalões de fórmula composta)
    val limiteX: Long = 0L           // X em 1/10000 € (só nos escalões de fórmula composta)
)

/** Categorias das tabelas de retenção. */
object CategoriaIRS {
    const val TRABALHO = "TRABALHO"
    const val PENSOES = "PENSOES"
}
