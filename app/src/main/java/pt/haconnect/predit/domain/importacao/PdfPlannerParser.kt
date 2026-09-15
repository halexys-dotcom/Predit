package pt.haconnect.predit.domain.importacao

import java.time.LocalDate
import java.time.YearMonth

data class DiaImportado(
    val epochDay: Long,
    val inicioMin: Int,
    val fimMin: Int,
    val pausaMin: Int,
    val duracaoMin: Int,
    val posto: String? = null
)

data class PlanoImportado(
    val contratoTrabalhoMin: Int?,
    val mesReferencia: YearMonth?,
    val dias: List<DiaImportado>,
    val totalMinutos: Int,
    val avisos: List<String>
)

private data class TarefaExtraida(
    val prefixo: String,
    val iniTaskMin: Int,
    val fimTaskMin: Int
)

fun parsePdfPlanner(
    texto: String,
    nomeFicheiro: String
): PlanoImportado {
    val avisos = mutableListOf<String>()

    // 1. Extrair Ano do nome do ficheiro
    val yearRegex = Regex("""\b(20\d{2})\b""")
    val yearMatch = yearRegex.find(nomeFicheiro)
    val ano = yearMatch?.groupValues?.get(1)?.toIntOrNull()

    if (ano == null) {
        avisos.add("Ano não detetado no nome do ficheiro")
    }

    // 2. Extrair Contrato de Trabalho
    val contratoRegex = Regex("""Contrato\s+de\s+trabalho\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
    val contratoMatch = contratoRegex.find(texto)
    val contratoHoras = contratoMatch?.groupValues?.get(1)?.toIntOrNull()
    val contratoTrabalhoMin = contratoHoras?.let { it * 60 }

    // 3. Mapeamento de meses
    val mesesMap = mapOf(
        "janeiro" to 1, "janvier" to 1, "jan" to 1,
        "fevereiro" to 2, "février" to 2, "fevrier" to 2, "fev" to 2, "feb" to 2,
        "março" to 3, "marco" to 3, "mars" to 3, "mar" to 3,
        "abril" to 4, "avril" to 4, "abr" to 4, "apr" to 4,
        "maio" to 5, "mai" to 5, "may" to 5,
        "junho" to 6, "juin" to 6, "jun" to 6,
        "julho" to 7, "juillet" to 7, "jul" to 7,
        "agosto" to 8, "août" to 8, "aout" to 8, "aug" to 8,
        "setembro" to 9, "septembre" to 9, "set" to 9, "sep" to 9,
        "outubro" to 10, "octobre" to 10, "out" to 10, "oct" to 10,
        "novembro" to 11, "novembre" to 11, "nov" to 11,
        "dezembro" to 12, "décembre" to 12, "decembre" to 12, "dec" to 12
    )

    val diaMesRegex = Regex("""(\d{1,2})[-/]([a-zA-Zà-úÀ-Ú]+)""")

    var mesReferencia: YearMonth? = null
    if (ano != null) {
        val matches = diaMesRegex.findAll(texto)
        for (match in matches) {
            val mesNomeStr = match.groupValues[2].lowercase()
            val mesNum = mesesMap[mesNomeStr]
            if (mesNum != null) {
                mesReferencia = YearMonth.of(ano, mesNum)
                break
            }
        }
    }

    val diasImportados = mutableListOf<DiaImportado>()
    val pairRegex = Regex("""(\d{1,2}):(\d{2})\s+(\d{1,2}):(\d{2})""")
    val taskRegex = Regex("""([A-ZÀ-Ú][A-Za-zÀ-ú0-9\s]+?)\s+(\d{1,2}):(\d{2})\s+(\d{1,2}):(\d{2})""")

    val linhas = texto.lines()
    val linhasUnidas = mutableListOf<String>()
    val datePatternRegex = Regex("""\d{1,2}[-/][A-ZÀ-Úa-zà-ú]+""")
    for (linha in linhas) {
        if (linha.contains(datePatternRegex)) {
            linhasUnidas.add(linha)
        } else if (linhasUnidas.isNotEmpty()) {
            linhasUnidas[linhasUnidas.lastIndex] += " " + linha
        }
    }

    for (linha in linhasUnidas) {
        val diaMatch = diaMesRegex.find(linha) ?: continue
        val diaNum = diaMatch.groupValues[1].toIntOrNull() ?: continue
        val mesNomeStr = diaMatch.groupValues[2].lowercase()
        val mesNum = mesesMap[mesNomeStr] ?: continue

        if (ano == null) continue

        val localDate = try {
            LocalDate.of(ano, mesNum, diaNum)
        } catch (_: Exception) {
            continue
        }

        val subAposData = linha.substring(diaMatch.range.last + 1)

        val todosMatches = pairRegex.findAll(subAposData).toList()
        if (todosMatches.isEmpty()) continue

        val todosPares = todosMatches.map { m ->
            val h1 = m.groupValues[1].toInt()
            val min1 = m.groupValues[2].toInt()
            val h2 = m.groupValues[3].toInt()
            val min2 = m.groupValues[4].toInt()
            (h1 * 60 + min1) to (h2 * 60 + min2)
        }

        val hhorarioPares = mutableListOf<Pair<Int, Int>>()
        var currentChainEnd = -1

        for (par in todosPares) {
            if (hhorarioPares.isEmpty()) {
                hhorarioPares.add(par)
                currentChainEnd = par.second
            } else if (par.first >= currentChainEnd) {
                hhorarioPares.add(par)
                currentChainEnd = par.second
            }
        }

        if (hhorarioPares.isEmpty()) continue

        val iniMinFinal = hhorarioPares.first().first
        val fimMinFinal = hhorarioPares.last().second
        val duracaoMinFinal = hhorarioPares.sumOf { it.second - it.first }
        val pausaMinFinal = (fimMinFinal - iniMinFinal) - duracaoMinFinal

        if (pausaMinFinal < 0) {
            avisos.add("Dia ${diaMatch.value}: turnos sobrepostos, ignorado")
            continue
        }

        // Extrair tarefas para determinação do Posto
        val taskMatches = taskRegex.findAll(subAposData).toList()
        val postosCandidatos = taskMatches.mapNotNull { m ->
            val rawPref = m.groupValues[1].trim()
            val prefixo = rawPref.replace(Regex("(?i)\\bFINJHRS\\w*"), "").trim()
            if (prefixo.count { it.isLetter() } < 2) return@mapNotNull null

            val h1 = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val min1 = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val h2 = m.groupValues[4].toIntOrNull() ?: return@mapNotNull null
            val min2 = m.groupValues[5].toIntOrNull() ?: return@mapNotNull null
            TarefaExtraida(prefixo, h1 * 60 + min1, h2 * 60 + min2)
        }

        val postoFinal: String? = when {
            postosCandidatos.isEmpty() -> null
            postosCandidatos.map { it.prefixo }.distinct().size == 1 -> postosCandidatos.first().prefixo
            else -> {
                val exato = postosCandidatos.firstOrNull { it.fimTaskMin == fimMinFinal }
                exato?.prefixo ?: postosCandidatos.last().prefixo
            }
        }

        diasImportados.add(
            DiaImportado(
                epochDay = localDate.toEpochDay(),
                inicioMin = iniMinFinal,
                fimMin = fimMinFinal,
                pausaMin = pausaMinFinal,
                duracaoMin = duracaoMinFinal,
                posto = postoFinal
            )
        )
    }

    val totalMinutos = diasImportados.sumOf { it.duracaoMin }

    return PlanoImportado(
        contratoTrabalhoMin = contratoTrabalhoMin,
        mesReferencia = mesReferencia,
        dias = diasImportados,
        totalMinutos = totalMinutos,
        avisos = avisos
    )
}
