package pt.haconnect.predit.data.repository

import pt.haconnect.predit.data.local.MapeamentoRubricaDao
import pt.haconnect.predit.data.local.MapeamentoRubricaEntity

/**
 * Ponte entre o mapeamento de rubricas (Room) e quem o consome — o parser dos recibos
 * (Fase 28b).
 *
 * Fase 28a: só infraestrutura. Ainda não está ligado a nenhum ecrã nem ao parser; tal como
 * os restantes repositórios, é instanciado à mão com o DAO da base de dados.
 */
class MapeamentoRubricaRepository(private val dao: MapeamentoRubricaDao) {

    suspend fun guardar(empresa: String, codigoOriginal: String, codigoInterno: String) {
        dao.guardar(
            MapeamentoRubricaEntity(
                empresa = empresa,
                codigoOriginal = codigoOriginal,
                codigoInterno = codigoInterno
            )
        )
    }

    /** O código interno para um código original da empresa, ou null se não estiver mapeado. */
    suspend fun obterCodigoInterno(empresa: String, codigoOriginal: String): String? =
        dao.obterCodigoInterno(empresa, codigoOriginal)
}
