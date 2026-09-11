package pt.haconnect.predit.domain.calc

/**
 * Posição de uma data dentro do ciclo de uma rotação.
 * Funciona para datas anteriores à âncora graças ao floorMod.
 */
fun posicaoNoCiclo(epochDay: Long, ancoraEpochDay: Long, comprimentoCiclo: Int): Int {
    require(comprimentoCiclo > 0) { "O ciclo tem de ter pelo menos um dia" }
    return Math.floorMod(epochDay - ancoraEpochDay, comprimentoCiclo.toLong()).toInt()
}
