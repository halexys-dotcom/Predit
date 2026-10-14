package pt.haconnect.predit.domain.importacao

/**
 * Um pedaço de texto extraído de um PDF, com a posição em que foi desenhado na página.
 *
 * O parser dos planeamentos só precisa de texto corrido, mas o dos recibos não: a grelha
 * do cabeçalho (`IRS Retido`, `Total Remun.`, `Alim.`, `CDH`, ...) é desenhada valor a
 * valor, e a ordem de desenho não coincide com a ordem das etiquetas — em todos os
 * recibos da PRIMAVERA/Cegid os valores saem da direita para a esquerda. Sem as
 * coordenadas não há forma fiável de saber qual dos valores é o IRS retido. Com elas, a
 * regra é simples: cada valor alinha com a etiqueta que tem o x mais próximo.
 *
 * Unidades: pontos PDF (A4 = 595 x 842). [x] cresce para a direita, [y] para baixo —
 * ou seja, menor y = mais perto do topo. É a convenção que o `PDFTextStripper` do pdfbox
 * devolve em `getXDirAdj()` / `getYDirAdj()`.
 */
data class BlocoTexto(
    val pagina: Int,
    val x: Float,
    val y: Float,
    val texto: String
)
