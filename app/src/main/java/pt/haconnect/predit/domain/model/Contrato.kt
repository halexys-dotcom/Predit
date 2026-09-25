package pt.haconnect.predit.domain.model

import pt.haconnect.predit.domain.calc.RegiaoIRS
import pt.haconnect.predit.domain.calc.TIPO_ESCALA_ROTACAO

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
    val categoriaCodigo: String = CATEGORIA_CCT_PADRAO,
    /**
     * Modo de escala (Fase 19). [TIPO_ESCALA_ROTACAO] — o ciclo da rotação projeta os meses;
     * [TIPO_ESCALA_PDF_MENSAL] — os chips do calendário vêm dos dias importados do PDF.
     * Coluna TEXT NOT NULL DEFAULT 'ROTACAO': quem já usava a app mantém a rotação.
     */
    val tipoEscala: String = TIPO_ESCALA_ROTACAO,
    /**
     * IRS Jovem (Fase 20). [aplicarIrsJovem] liga o regime no recibo; os dois anos são os
     * únicos dados que o motor precisa para descobrir a percentagem de isenção
     * (domain/calc/IrsJovem.kt: 100 %, 75 %, 50 % ou 25 %, conforme o ano de obtenção).
     * Nulos por omissão: o regime só se aplica a quem o ligar e tiver os dois anos.
     */
    val anoNascimento: Int? = null,
    val anoPrimeiroRendimento: Int? = null,
    val aplicarIrsJovem: Boolean = false
)
