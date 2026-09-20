package pt.haconnect.predit.domain.model

import pt.haconnect.predit.domain.calc.RegiaoIRS

enum class RegimeHorario {
    NORMAL,
    ADAPTABILIDADE
}

enum class EstadoCivil {
    SOLTEIRO,
    CASADO,
    VIUVO,
    DIVORCIADO
}

data class ContratoUtilizador(
    val id: Int = 1,
    val categoriaNivel: String = "XIII, Vigilante Aeroportuário/APA-A",
    val dataAdmissao: Long? = null,
    val regimeHorario: RegimeHorario = RegimeHorario.NORMAL,
    val horarioSemanalH: Int = 40,
    val numeroDependentes: Int = 0,
    val estadoCivil: EstadoCivil = EstadoCivil.SOLTEIRO,
    val titulares: Int = 1,
    val primeiroArranqueConcluido: Boolean = false,
    // Região fiscal — decide as tabelas de retenção de IRS (domain/calc/CalculoIRS.kt).
    // Não nulo: a coluna na BD é TEXT NOT NULL DEFAULT 'CONTINENTE'.
    val regiao: RegiaoIRS = RegiaoIRS.CONTINENTE,
    /**
     * Município escolhido (municipio.id), para o feriado municipal (Fase 10).
     * Nulo = não escolhido: o recibo fica só com os feriados nacionais.
     */
    val municipioId: Int? = null,
    /**
     * Categoria CCT do contrato (13a), chave de parametros_cct.codigoCategoria. O nome legível
     * para o ecrã continua em [categoriaNivel]; é esta chave que o recibo usa para escolher a
     * tabela salarial. Por omissão APAA, que é a categoria de referência.
     */
    val categoriaCodigo: String = CATEGORIA_CCT_PADRAO
)
