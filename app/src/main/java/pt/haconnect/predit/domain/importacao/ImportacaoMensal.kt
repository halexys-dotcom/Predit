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
