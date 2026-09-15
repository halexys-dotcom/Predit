package pt.haconnect.predit.domain.calc

private val ABREVIATURAS_POSTO = mapOf(
    "CC ES" to "CC",
    "CC Emb ES" to "CC",
    "P6 ES" to "P6",
    "StaffCrPr ES" to "Staff",
    "LIS TAP TTA ES" to "TTA",
    "P80 ES" to "P80",
    "PMR ES" to "PMR",
    "P1 ES" to "P1",
    "GOC ES" to "GOC",
    "Figo Maduro ES" to "FiGO M"
)

fun abreviarPosto(posto: String?): String? {
    if (posto.isNullOrBlank()) return null
    val trimmed = posto.trim()
    ABREVIATURAS_POSTO[trimmed]?.let { return it }
    // fallback: primeiro token, cortado a 5 caracteres
    return trimmed.substringBefore(" ").take(5)
}
