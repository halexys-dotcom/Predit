package pt.haconnect.predit.domain.model

data class DiaReal(
    val id: Long = 0,
    val data: Long,                 // epochDay
    val tipoTurnoId: Long?,         // nullable
    val inicioMin: Int,
    val fimMin: Int,
    val pausaMin: Int,
    val nota: String? = null,
    val origem: String = "MANUAL",   // "PDF" | "MANUAL"
    val posto: String? = null,       // NOVO

    /**
     * Fase 29 (v2.2.0): o turno que o PDF da empresa previa para este dia, guardado à parte das
     * horas reais.
     *
     * Só há uma linha em `dia_real` por data, e o editor regrava-a como MANUAL com as horas que o
     * utilizador trabalhou. Sem estas três colunas, editar um dia do PDF apagava a escala: o dia
     * deixava de contar como dia útil (e de valer o subsídio de alimentação), ficava sem régua
     * para as horas extra e para os atrasos dessa jornada, e uma folga do PDF trabalhada deixava
     * de ser reconhecida como descanso (DESC_DESC a 200%).
     *
     * Null nos dias que não vieram de um PDF e nos importados antes desta fase; aí a régua volta a
     * ser as horas da própria linha, que é o comportamento anterior.
     */
    val previstoInicioMin: Int? = null,
    val previstoFimMin: Int? = null,
    val previstoPausaMin: Int? = null
)
