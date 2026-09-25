package pt.haconnect.predit.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.haconnect.predit.data.local.ContratoUtilizadorDao
import pt.haconnect.predit.data.local.ContratoUtilizadorEntity
import pt.haconnect.predit.domain.calc.RegiaoIRS
import pt.haconnect.predit.domain.model.ContratoUtilizador
import pt.haconnect.predit.domain.model.EstadoCivil
import pt.haconnect.predit.domain.model.RegimeHorario

class ContratoRepository(private val dao: ContratoUtilizadorDao) {

    fun observar(): Flow<ContratoUtilizador?> {
        return dao.observar().map { it?.paraModelo() }
    }

    suspend fun obter(): ContratoUtilizador? {
        return dao.obter()?.paraModelo()
    }

    suspend fun guardar(contrato: ContratoUtilizador) {
        dao.guardar(contrato.paraEntidade())
    }

    private fun ContratoUtilizadorEntity.paraModelo(): ContratoUtilizador {
        return ContratoUtilizador(
            id = id,
            categoriaNivel = categoriaNivel,
            dataAdmissao = dataAdmissao,
            regimeHorario = try { RegimeHorario.valueOf(regimeHorario) } catch (_: Exception) { RegimeHorario.NORMAL },
            horarioSemanalH = horarioSemanalH,
            numeroDependentes = numeroDependentes,
            estadoCivil = try { EstadoCivil.valueOf(estadoCivil) } catch (_: Exception) { EstadoCivil.SOLTEIRO },
            titulares = titulares,
            primeiroArranqueConcluido = primeiroArranqueConcluido,
            regiao = try { RegiaoIRS.valueOf(regiao) } catch (_: Exception) { RegiaoIRS.CONTINENTE },
            municipioId = municipioId,
            categoriaCodigo = categoriaCodigo,
            tipoEscala = tipoEscala,
            anoNascimento = anoNascimento,
            anoPrimeiroRendimento = anoPrimeiroRendimento,
            aplicarIrsJovem = aplicarIrsJovem
        )
    }

    private fun ContratoUtilizador.paraEntidade(): ContratoUtilizadorEntity {
        return ContratoUtilizadorEntity(
            id = 1,
            categoriaNivel = categoriaNivel,
            dataAdmissao = dataAdmissao,
            regimeHorario = regimeHorario.name,
            horarioSemanalH = horarioSemanalH,
            numeroDependentes = numeroDependentes,
            estadoCivil = estadoCivil.name,
            titulares = titulares,
            primeiroArranqueConcluido = primeiroArranqueConcluido,
            regiao = regiao.name,
            municipioId = municipioId,
            categoriaCodigo = categoriaCodigo,
            tipoEscala = tipoEscala,
            anoNascimento = anoNascimento,
            anoPrimeiroRendimento = anoPrimeiroRendimento,
            aplicarIrsJovem = aplicarIrsJovem
        )
    }
}
