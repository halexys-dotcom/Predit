package pt.haconnect.predit.domain.importacao

import pt.haconnect.predit.domain.model.DiaReal

enum class AcaoImportacaoDia {
    INSERIR,
    ATUALIZAR,
    MANTER_MANUAL
}

fun decidirImportacaoDia(existente: DiaReal?): AcaoImportacaoDia {
    return when {
        existente == null -> AcaoImportacaoDia.INSERIR
        existente.origem == "PDF" -> AcaoImportacaoDia.ATUALIZAR
        else -> AcaoImportacaoDia.MANTER_MANUAL
    }
}

/**
 * Fase 29: linha a gravar para um dia que só existe no PDF (ou que já era do PDF e vai ser
 * reimportado). As horas do PDF ficam nas colunas reais — é o que o calendário e o editor
 * mostram — e em duplicado nas previstas: é o previsto que sobrevive quando o utilizador editar
 * o dia, e sem ele a edição apagava a escala daquele dia.
 */
fun diaRealDoPdf(diaImportado: DiaImportado, id: Long = 0L): DiaReal = DiaReal(
    id = id,
    data = diaImportado.epochDay,
    tipoTurnoId = null,
    inicioMin = diaImportado.inicioMin,
    fimMin = diaImportado.fimMin,
    pausaMin = diaImportado.pausaMin,
    nota = null,
    origem = "PDF",
    posto = diaImportado.posto,
    previstoInicioMin = diaImportado.inicioMin,
    previstoFimMin = diaImportado.fimMin,
    previstoPausaMin = diaImportado.pausaMin
)

/**
 * Fase 29: o utilizador já corrigiu este dia, por isso as horas reais, o tipo, a nota e o posto
 * ficam como estão — a reimportação só repõe as horas previstas do PDF. É esta a única forma de
 * preencher o previsto dos dias importados antes da migração 23→24 (que ficaram sem ele).
 */
fun diaManualComPrevistoDoPdf(existente: DiaReal, diaImportado: DiaImportado): DiaReal =
    existente.copy(
        previstoInicioMin = diaImportado.inicioMin,
        previstoFimMin = diaImportado.fimMin,
        previstoPausaMin = diaImportado.pausaMin
    )
