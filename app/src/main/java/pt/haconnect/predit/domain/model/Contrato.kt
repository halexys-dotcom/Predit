package pt.haconnect.predit.domain.model

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
    val primeiroArranqueConcluido: Boolean = false
)
