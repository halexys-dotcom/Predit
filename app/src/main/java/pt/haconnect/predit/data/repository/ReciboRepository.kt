package pt.haconnect.predit.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.PreditDatabase
import pt.haconnect.predit.data.local.ReciboLinhaEntity
import pt.haconnect.predit.data.local.ReciboMesEntity
import pt.haconnect.predit.domain.calc.NaturezaRubrica
import pt.haconnect.predit.domain.model.ReciboLinha
import pt.haconnect.predit.domain.model.ReciboMes

/**
 * Ponte entre o recibo introduzido (Room) e o domínio.
 *
 * Leva a PreditDatabase, e não só os DAOs, porque [guardar] tem de escrever o
 * cabeçalho e as linhas na mesma transacção: sem isso um erro a meio (por exemplo
 * uma rubrica inexistente, travada pelo FK RESTRICT) deixaria o mês gravado sem linhas.
 */
class ReciboRepository(private val db: PreditDatabase) {

    private val mesDao get() = db.reciboMesDao()
    private val linhaDao get() = db.reciboLinhaDao()

    fun observarTodosMeses(): Flow<List<ReciboMes>> =
        mesDao.observarTodos().map { lista -> lista.map { it.paraModelo() } }

    fun observarMes(anoMes: String): Flow<ReciboMes?> =
        mesDao.observarPorMes(anoMes).map { it?.paraModelo() }

    /**
     * Os cabeçalhos dos recibos de um ano ("2026" apanha 2026-01 a 2026-12).
     *
     * O simulador de IRS anual pré-preenche o rendimento com a soma do `totalAbonos` dos meses
     * guardados e diz em quantos meses se apoia — para isso precisa dos cabeçalhos, não só das
     * linhas. Não há tabela nova nem migração: é a mesma recibo_mes, lida por ano.
     */
    fun observarMesesDoAno(ano: Int): Flow<List<ReciboMes>> =
        mesDao.observarTodos().map { lista ->
            lista.filter { it.anoMes.startsWith("$ano-") }.map { it.paraModelo() }
        }

    fun observarLinhas(anoMes: String): Flow<List<ReciboLinha>> =
        linhaDao.observarPorMes(anoMes).map { lista -> lista.map { it.paraModelo() } }

    /**
     * As linhas de todos os meses de um ano — o simulador de IRS anual soma os D02 do ano
     * para saber quanto já foi retido. Não há tabela nova nem migração: é a mesma que serve
     * a conferência do recibo, lida por ano.
     */
    fun observarLinhasDoAno(ano: Int): Flow<List<ReciboLinha>> =
        linhaDao.observarDoAno(ano.toString()).map { lista -> lista.map { it.paraModelo() } }

    /**
     * Grava um recibo completo (cabeçalho + linhas) numa transacção.
     *
     * Apaga primeiro as linhas do mês: a chave é composta, logo o upsert só substitui as
     * rubricas que venham na lista nova — as que tivessem saído ficariam lá como órfãs de
     * conteúdo. O DELETE e o INSERT vão na mesma transacção, para não haver um instante
     * em que o mês aparece sem linhas.
     */
    suspend fun guardar(mes: ReciboMes, linhas: List<ReciboLinha>) {
        db.withTransaction {
            mesDao.upsert(mes.paraEntidade())
            linhaDao.apagarPorMes(mes.anoMes)
            linhaDao.upsertTodas(linhas.map { it.paraEntidade() })
        }
    }

    /** Apaga o mês; o CASCADE leva as linhas. */
    suspend fun apagar(anoMes: String) {
        mesDao.apagar(anoMes)
    }

    private fun ReciboMesEntity.paraModelo(): ReciboMes =
        ReciboMes(
            anoMes = anoMes,
            dataFecho = dataFecho,
            vencimentoBase = vencimentoBase,
            vencimentoHora = vencimentoHora,
            numDiasUteis = numDiasUteis,
            irsRetidoAno = irsRetidoAno,
            totalAbonos = totalAbonos,
            totalDescontos = totalDescontos,
            liquido = liquido,
            nota = nota,
            dataCriacao = dataCriacao
        )

    private fun ReciboMes.paraEntidade(): ReciboMesEntity =
        ReciboMesEntity(
            anoMes = anoMes,
            dataFecho = dataFecho,
            vencimentoBase = vencimentoBase,
            vencimentoHora = vencimentoHora,
            numDiasUteis = numDiasUteis,
            irsRetidoAno = irsRetidoAno,
            totalAbonos = totalAbonos,
            totalDescontos = totalDescontos,
            liquido = liquido,
            nota = nota,
            dataCriacao = dataCriacao
        )

    private fun ReciboLinhaEntity.paraModelo(): ReciboLinha =
        ReciboLinha(
            reciboMesId = reciboMesId,
            rubricaId = rubricaId,
            valorEstimado = valorEstimado,
            valorReal = valorReal,
            // Mesmo padrão defensivo do RegiaoIRS/EstadoCivil: um valor estranho na
            // coluna não pode rebentar a leitura do recibo; cai em ABONO.
            natureza = try { NaturezaRubrica.valueOf(natureza) } catch (_: Exception) {
                NaturezaRubrica.ABONO
            },
            ordem = ordem
        )

    private fun ReciboLinha.paraEntidade(): ReciboLinhaEntity =
        ReciboLinhaEntity(
            reciboMesId = reciboMesId,
            rubricaId = rubricaId,
            valorEstimado = valorEstimado,
            valorReal = valorReal,
            natureza = natureza.name,
            ordem = ordem
        )
}
