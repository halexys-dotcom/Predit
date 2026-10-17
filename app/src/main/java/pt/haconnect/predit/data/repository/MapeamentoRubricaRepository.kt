package pt.haconnect.predit.data.repository

import androidx.room.withTransaction
import pt.haconnect.predit.data.local.MapeamentoRubricaEntity
import pt.haconnect.predit.data.local.PreditDatabase
import pt.haconnect.predit.domain.importacao.chaveDaEmpresa

/**
 * Ponte entre o mapeamento de rubricas (Room) e quem o consome — o parser dos recibos
 * (Fase 28b) e o modal de mapeamento (Fase 28c).
 *
 * Fase 28a: a tabela e o DAO. Fase 28b: o parser passou a consumir o mapa (`obterTodos`),
 * escolhido pela empresa lida do rodapé do recibo.
 *
 * Leva a PreditDatabase, e não só o DAO, porque [guardarNormalizados] grava vários códigos
 * na mesma transacção — tal como o [ReciboRepository].
 */
class MapeamentoRubricaRepository(private val db: PreditDatabase) {

    private val dao get() = db.mapeamentoRubricaDao()

    suspend fun guardar(empresa: String, codigoOriginal: String, codigoInterno: String) {
        dao.guardar(
            MapeamentoRubricaEntity(
                empresa = empresa,
                codigoOriginal = codigoOriginal,
                codigoInterno = codigoInterno
            )
        )
    }

    /**
     * Grava um mapeamento com a chave da empresa ja normalizada.
     * Usar esta em vez de guardar(MapeamentoRubricaEntity(...)) para
     * garantir simetria com a leitura feita pelo parser.
     */
    suspend fun guardarNormalizado(
        empresa: String,
        codigoOriginal: String,
        codigoInterno: String
    ) {
        guardarNormalizados(empresa, mapOf(codigoOriginal to codigoInterno))
    }

    /**
     * Grava as escolhas do modal (28c) na mesma transacção: ou entram todas, ou nenhuma.
     *
     * Sem a transacção, uma falha a meio deixava o mapa a meio caminho e a importação
     * seguinte do mesmo recibo traduzia só parte dos códigos, a pedir os restantes outra vez.
     */
    suspend fun guardarNormalizados(empresa: String, escolhas: Map<String, String>) {
        if (escolhas.isEmpty()) return
        db.withTransaction {
            for ((codigoOriginal, codigoInterno) in escolhas) {
                dao.guardar(entidadeNormalizada(empresa, codigoOriginal, codigoInterno))
            }
        }
    }

    private fun entidadeNormalizada(
        empresa: String,
        codigoOriginal: String,
        codigoInterno: String
    ) = MapeamentoRubricaEntity(
        empresa = chaveDaEmpresa(empresa),
        codigoOriginal = codigoOriginal.trim().uppercase(),
        codigoInterno = codigoInterno.trim().uppercase()
    )

    /** O código interno para um código original da empresa, ou null se não estiver mapeado. */
    suspend fun obterCodigoInterno(empresa: String, codigoOriginal: String): String? =
        dao.obterCodigoInterno(empresa, codigoOriginal)

    /**
     * Todos os mapeamentos do utilizador, agrupados por empresa:
     * empresa -> (código original -> código interno).
     *
     * É a forma que o parser consome (`parseReciboPdf(blocos, mapeamentosPorEmpresa)`): a
     * empresa vem do rodapé do recibo e o mapa certo escolhe-se por ela.
     */
    suspend fun obterTodos(): Map<String, Map<String, String>> =
        dao.obterTodos().groupBy { it.empresa }.mapValues { (_, lista) ->
            lista.associate { it.codigoOriginal to it.codigoInterno }
        }
}
