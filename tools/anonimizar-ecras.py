#!/usr/bin/env python3
"""Higieniza os ecras publicados em docs/screens: troca siglas operacionais
(CC, GOC, P6, P80, TTA, Staff, T08H, PMR, P1 e os codigos das rubricas do
recibo) por designacoes genericas, sem mexer no resto da interface.

Os PNG de docs/screens sao derivados das capturas reais pelo
Temp/build-web-assets.ps1, por isso esta passagem corre depois desse script:

    powershell -File .\\Temp\\build-web-assets.ps1
    python .\\tools\\anonimizar-ecras.py

Nao ha OCR nem reconstrucao da interface: cada chip e cada linha de texto e
localizada pela cor e o texto antigo e apagado e redesenhado por cima. Assim a
captura continua a ser a da app verdadeira; muda so o rotulo.

A passagem foi feita para correr uma so vez sobre as capturas originais. A
guarda em redesenhar tenta reconhecer o rotulo ja escrito e nao mexer, mas so
dispara quando a tinta observada coincide quase toda com a que ia desenhar;
como isso nem sempre acontece, correr outra vez sobre um ecra ja tratado pode
voltar a pinta-lo. A fonte de verdade e sempre screenshots/: o caminho normal e
correr o Temp/build-web-assets.ps1 (que regenera os recortes) seguido deste
script.

Uso:
    python tools/anonimizar-ecras.py               # aplica
    python tools/anonimizar-ecras.py --diagnostico # mostra o que faria
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parents[1]
ECRAS = RAIZ / "docs" / "screens"

# Fonte de desenho: a mais parecida com a da app (Roboto) que existe em cada
# sistema. A app usa a tipografia padrao do Material 3, nao traz ficheiro de
# fonte proprio, por isso nao ha nada no repositorio para reutilizar.
FONTES_NEGRITO = (
    Path(r"C:\Windows\Fonts\segoeuib.ttf"),
    Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
)
FONTES_NORMAL = (
    Path(r"C:\Windows\Fonts\segoeui.ttf"),
    Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
)

DIF_MIN = 12        # diferenca minima para separar os glifos do fundo do chip
INSET = 3           # margem interior de cada chip ignorada na analise
GLIFOS_MIN = 12     # pixels minimos para se considerar que ha texto
SATURACAO_NEUTRA = 20  # abaixo disto o pixel e texto de chip (branco ou escuro)
PROPORCAO_ROTULO = 0.45  # altura das maiusculas do rotulo / altura do chip
LIMIAR_IGUAL = 0.70  # sobreposicao a partir da qual o texto ja esta escrito
PESO_LUM = np.array([299, 587, 114])  # luminancia (inteiro, base 1000)

# Titulos do ecra do recibo que trazem codigo de rubrica no inicio. As janelas
# sao as faixas de linhas onde o texto foi medido na captura; cada uma e
# validada antes de ser tocada (ver anonimizar_recibo).
JANELAS_RECIBO = (
    ("Vencimento", 765, 865),
    ("Horas noturnas", 970, 1070),
    ("Subsídio diurno", 1195, 1295),
)

_cache_fontes: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}


def fonte(tamanho: int, negrito: bool) -> ImageFont.FreeTypeFont:
    candidatas = FONTES_NEGRITO if negrito else FONTES_NORMAL
    caminho = next((c for c in candidatas if c.exists()), None)
    if caminho is None:
        raise SystemExit(
            "Sem fonte TTF utilizavel: instale a Segoe UI (Windows) ou a DejaVu "
            "(Linux) antes de correr este script."
        )
    chave = (str(caminho), tamanho)
    if chave not in _cache_fontes:
        _cache_fontes[chave] = ImageFont.truetype(str(caminho), tamanho)
    return _cache_fontes[chave]


def altura_cap(tamanho: int, negrito: bool) -> int:
    caixa = fonte(tamanho, negrito).getbbox("H")
    return caixa[3] - caixa[1]


def tamanho_para_cap(cap_alvo: int, texto: str, negrito: bool, largura_max: float) -> int:
    """Maior tamanho em que a altura das maiusculas nao passa do alvo e o texto cabe.

    O limite e estrito (nunca "cap_alvo + 1"): assim o tamanho escolhido para um
    rotulo ja desenhado e o mesmo que ele tem, o que mantem a passagem estavel
    quando se corre outra vez sobre a mesma captura.
    """
    escolhido = 8
    for candidato in range(8, 64):
        if altura_cap(candidato, negrito) > cap_alvo:
            break
        if fonte(candidato, negrito).getlength(texto) > largura_max:
            break
        escolhido = candidato
    return escolhido


def posicionar(desenho, texto, centro, cap_alvo, largura_max, negrito, alinhamento="centro"):
    """Escolhe o tamanho da fonte e onde o texto fica, sem o desenhar.

    A referencia vertical e a banda do "H": e a que os rotulos antigos (todos
    com maiuscula inicial) ocupam, por isso acentos e descidas nao deslocam a
    linha. Devolve (fonte, tamanho, x, y).
    """
    tamanho = tamanho_para_cap(cap_alvo, texto, negrito, largura_max)
    f = fonte(tamanho, negrito)
    bb = desenho.textbbox((0, 0), texto, font=f)
    cb = desenho.textbbox((0, 0), "H", font=f)
    if alinhamento == "esquerda":
        x = centro[0] - bb[0]
    else:
        x = centro[0] - (bb[0] + bb[2]) / 2
    y = centro[1] - (cb[1] + cb[3]) / 2
    return f, tamanho, x, y


def mascara_do_texto(texto, f, forma, posicao, fundo, tinta, neutra=False):
    """Tinta que o texto deixaria, medida com o mesmo criterio da mascara real.

    Desenhar o texto numa tela com a cor de fundo local e medir com o mesmo
    criterio da captura e o que permite comparar as duas formas (nos chips, a
    neutralidade da tinta; nas areas de fundo liso, a diferenca para o fundo).
    """
    tela = Image.new("RGB", (forma[1], forma[0]), tuple(int(v) for v in fundo))
    ImageDraw.Draw(tela).text(posicao, texto, font=f, fill=tuple(int(v) for v in tinta))
    arranjo = np.asarray(tela).astype(np.int16)
    if neutra:
        return (arranjo.max(axis=2) - arranjo.min(axis=2)) < SATURACAO_NEUTRA
    return np.abs(arranjo - np.asarray(fundo, dtype=np.int16)).max(axis=2) > DIF_MIN


def sobreposicao(observada, nova):
    """Quanto a tinta observada e a tinta a desenhar coincidem (0 a 1)."""
    uniao = np.logical_or(observada, nova).sum()
    if uniao == 0:
        return 0.0
    return float(np.logical_and(observada, nova).sum()) / float(uniao)


# ---------------------------------------------------------------------------
# Analise da imagem
# ---------------------------------------------------------------------------


def luminancia(regiao):
    return (regiao.astype(np.int32) @ PESO_LUM) // 1000


def cor_dominante(regiao):
    """Cor mais comum de uma regiao (o fundo do chip, do cartao ou do ecra)."""
    cores, contagens = np.unique(regiao.reshape(-1, 3), axis=0, return_counts=True)
    return cores[contagens.argmax()]


def bandas(mask, altura_min, altura_max, minimo=2):
    """Faixas horizontais (y0, y1) com pelo menos `minimo` pixels por linha."""
    ocupadas = np.flatnonzero(mask.sum(axis=1) >= minimo)
    if ocupadas.size == 0:
        return []
    cortes = np.flatnonzero(np.diff(ocupadas) > 1)
    resultado = []
    for i0, i1 in zip(np.r_[0, cortes + 1], np.r_[cortes, ocupadas.size - 1]):
        y0, y1 = int(ocupadas[i0]), int(ocupadas[i1]) + 1
        if altura_min <= y1 - y0 <= altura_max:
            resultado.append((y0, y1))
    return resultado


def fatias(mask, largura_min):
    """Faixas verticais (x0, x1) com pixels da mascara dentro de uma banda."""
    ocupadas = np.flatnonzero(mask.sum(axis=0) > 0)
    if ocupadas.size == 0:
        return []
    cortes = np.flatnonzero(np.diff(ocupadas) > 1)
    resultado = []
    for i0, i1 in zip(np.r_[0, cortes + 1], np.r_[cortes, ocupadas.size - 1]):
        x0, x1 = int(ocupadas[i0]), int(ocupadas[i1]) + 1
        if x1 - x0 >= largura_min:
            resultado.append((x0, x1))
    return resultado


def caixa_da_mascara(mascara, minimo=GLIFOS_MIN):
    """Caixa justa da tinta de uma mascara booleana."""
    ys, xs = np.nonzero(mascara)
    if ys.size < minimo:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def mascara_do_fundo(regiao, fundo):
    """Tinta numa area de fundo liso: o que se afasta da cor dominante."""
    desvio = np.abs(regiao.astype(np.int16) - np.asarray(fundo, dtype=np.int16)).max(axis=2)
    return desvio > DIF_MIN


def mascara_neutra(regiao, limiar=SATURACAO_NEUTRA):
    """Tinta de um chip: o rotulo e neutro (branco ou escuro) e o chip e colorido.

    Procurar a neutralidade, em vez da diferenca para a cor do chip, evita que a
    propria borda do chip (mais clara) entre na conta como se fosse texto.
    """
    mx = regiao.max(axis=2).astype(np.int16)
    mn = regiao.min(axis=2).astype(np.int16)
    return (mx - mn) < limiar


def cor_da_tinta(regiao, mascara, fundo):
    """Cor do texto: o extremo de luminosidade da tinta (clara ou escura)."""
    pixels = regiao[mascara]
    lum = luminancia(pixels)
    if lum.mean() >= luminancia(np.asarray(fundo, dtype=np.uint8).reshape(1, 3))[0]:
        return pixels[lum >= np.percentile(lum, 85)].mean(axis=0)
    return pixels[lum <= np.percentile(lum, 15)].mean(axis=0)


def faixa_nucleo(desvio, fracao=0.22):
    """Banda de linhas que concentra o texto, deixando descidas e acentos fora.

    Serve para medir a altura das maiusculas: num titulo com "Sup." a caixa do
    texto inclui a descida do "p", que nao pode entrar no tamanho da fonte.
    """
    contagens = desvio.sum(axis=1)
    if contagens.max() == 0:
        return 0, desvio.shape[0]
    uteis = np.flatnonzero(contagens >= max(1.0, contagens.max() * fracao))
    return int(uteis.min()), int(uteis.max()) + 1


def grupo_de_colunas(mask, salto=8):
    """Grupo de colunas com mais pixels, tolerando falhas ate `salto`.

    As letras de uma mesma palavra distam poucos pixeis (ficam no mesmo grupo);
    ja a passagem para outro texto tem um salto grande e faz grupo proprio. E
    assim que se isola a abreviatura dentro do disco do turno sem apanhar o
    horario que vem a seguir.
    """
    ocupadas = np.flatnonzero(mask.sum(axis=0) > 0)
    if ocupadas.size == 0:
        return None
    cortes = np.flatnonzero(np.diff(ocupadas) > salto)
    grupos = []
    for i0, i1 in zip(np.r_[0, cortes + 1], np.r_[cortes, ocupadas.size - 1]):
        x0, x1 = int(ocupadas[i0]), int(ocupadas[i1]) + 1
        grupos.append((x0, x1, int(mask[:, x0:x1].sum())))
    x0, x1, _ = max(grupos, key=lambda g: g[2])
    return x0, x1


def rotulo_do_chip(cor):
    """Rotulo generico a partir da cor do chip (a app usa uma cor por categoria)."""
    r, g, b = (int(v) for v in cor)
    if g > r and g >= b:
        return "Folga"
    if b > r + 10:
        return "Férias"
    return "Turno"


# ---------------------------------------------------------------------------
# Substituicao do texto
# ---------------------------------------------------------------------------


def redesenhar(img, a, caixa, nome, *, negrito, alinhamento="centro", recorte=0,
               largura_max=None, fundo=None, nucleo=False, cap_alvo=None,
               neutra=False, centro_y=None):
    """Apaga o texto dentro de `caixa` e escreve `nome` por cima, no mesmo sitio.

    `caixa` = (x0, y0, x1, y1) em coordenadas da imagem. `recorte` ignora uma
    margem interior (cantos redondos dos chips). `fundo` forca a cor de
    apagamento (por omissao, a cor dominante da area). `neutra` procura a tinta
    pela neutralidade (chips) em vez da diferenca para o fundo. `nucleo` mede o
    tamanho da fonte pela banda das maiusculas; `cap_alvo` impoe esse tamanho a
    mao; `centro_y` fixa a linha do texto (nos chips, o centro do chip).
    Devolve o resultado, ou None se nao houver texto ai ou se ele ja estiver escrito.
    """
    x0, y0, x1, y1 = caixa
    regiao = a[y0 + recorte:y1 - recorte, x0 + recorte:x1 - recorte]
    if regiao.size == 0:
        return None
    cor_fundo = cor_dominante(regiao) if fundo is None else np.asarray(fundo, dtype=np.uint8)
    mascara = mascara_neutra(regiao) if neutra else mascara_do_fundo(regiao, cor_fundo)
    glifos = caixa_da_mascara(mascara)
    if glifos is None:
        return None
    gx0, gy0, gx1, gy1 = glifos
    if recorte and (gx0 <= 1 or gy0 <= 1 or gx1 >= regiao.shape[1] - 1 or gy1 >= regiao.shape[0] - 1):
        return None  # a tinta encosta ao recorte: nao vale a pena arriscar
    cor_tinta = cor_da_tinta(regiao, mascara, cor_fundo)
    rx, ry = x0 + recorte, y0 + recorte
    if largura_max is None:
        largura_max = (x1 - x0) - 2 * recorte
    # Altura de referencia: a caixa toda, ou so a banda das maiusculas (nucleo).
    if nucleo:
        ny0, ny1 = faixa_nucleo(mascara[gy0:gy1, gx0:gx1])
    else:
        ny0, ny1 = 0, gy1 - gy0
    if cap_alvo is None:
        cap_alvo = max(8, ny1 - ny0)
    centro_x = rx + gx0 if alinhamento == "esquerda" else rx + (gx0 + gx1) / 2
    if centro_y is None:
        centro_y = ry + gy0 + (ny0 + ny1) / 2
    desenho = ImageDraw.Draw(img)
    f, tamanho, px, py = posicionar(desenho, nome, (centro_x, centro_y), cap_alvo,
                                    largura_max, negrito, alinhamento)
    # Se ali ja estiver escrita a mesma coisa, nao se toca em nada: e isto que
    # torna a passagem repetivel (duas corridas seguidas dao o mesmo ficheiro).
    nova = mascara_do_texto(nome, f, mascara.shape, (px - rx, py - ry),
                            cor_fundo, cor_tinta, neutra)
    if sobreposicao(mascara, nova) >= LIMIAR_IGUAL:
        return None
    desenho.rectangle([rx + gx0 - 2, ry + gy0 - 2, rx + gx1 + 1, ry + gy1 + 1],
                      fill=tuple(int(v) for v in cor_fundo))
    desenho.text((px, py), nome, font=f, fill=tuple(int(v) for v in cor_tinta))
    return {"nome": nome, "caixa": (rx + gx0, ry + gy0, rx + gx1, ry + gy1),
            "fundo": tuple(int(v) for v in cor_fundo),
            "tinta": tuple(int(v) for v in cor_tinta), "tamanho": tamanho}


# ---------------------------------------------------------------------------
# Ecrãs: escala mensal, escala com rotacao, horario e recibo
# ---------------------------------------------------------------------------


def localizar_chips(a, faixa, minimo=250, tolerancia=8, ocupacao=0.70):
    """Localiza os chips pela cor, um chip de cada vez.

    Cada chip e um retangulo de cor plana. Procura-se por cor (e nao por
    saturacao), para o realce do dia de hoje, que tem outro tom e e mais alto,
    nunca se confundir com um chip. O preenchimento minimo do retangulo, a
    altura e o formato descartam as franjas de antialiasing do proprio chip e os
    numeros destacados, que sao do mesmo tom dos realces.
    """
    y_min, y_max = faixa
    area = a[y_min:y_max]
    cores, contagens = np.unique(area.reshape(-1, 3), axis=0, return_counts=True)
    caixas = []
    for cor, contagem in zip(cores, contagens):
        if contagem < minimo:
            continue
        mascara = (np.abs(area.astype(np.int16) - cor.astype(np.int16)) <= tolerancia).all(axis=2)
        for y0, y1 in bandas(mascara, 26, 50):
            for x0, x1 in fatias(mascara[y0:y1], 40):
                linhas = np.flatnonzero(mascara[y0:y1, x0:x1].any(axis=1))
                by0, by1 = y0 + int(linhas.min()), y0 + int(linhas.max()) + 1
                largura, altura = x1 - x0, by1 - by0
                if not 1.1 <= largura / altura <= 3.4:
                    continue
                if mascara[by0:by1, x0:x1].mean() < ocupacao:
                    continue
                caixas.append((x0, y_min + by0, x1, y_min + by1))
    return caixas


def anonimizar_calendario(caminho, faixa, so_turnos, diagnostico):
    """Chips do calendario: o rotulo passa a "Turno", "Folga" ou "Férias"."""
    img = Image.open(caminho).convert("RGB")
    a = np.asarray(img)
    acoes = []
    vistas = []
    for caixa in localizar_chips(a, faixa):
        x0, y0, x1, y1 = caixa
        if any(abs(x0 - v[0]) < 12 and abs(y0 - v[1]) < 12 for v in vistas):
            continue  # ja tratado (duas cores sobrepostas no mesmo chip)
        interior = a[y0 + INSET:y1 - INSET, x0 + INSET:x1 - INSET]
        if interior.size == 0:
            continue
        fundo_chip = cor_dominante(interior)
        if int(fundo_chip.max()) - int(fundo_chip.min()) < 20:
            continue  # chip do dia de hoje (invertido): nao tem cor de categoria
        nome = rotulo_do_chip(fundo_chip)
        if so_turnos and nome != "Turno":
            continue
        # O tamanho do rotulo vem da altura do chip (e nao da tinta medida): a
        # caixa do chip e sempre a mesma, por isso a passagem e repetivel.
        feito = redesenhar(img, a, caixa, nome, negrito=True, recorte=INSET, neutra=True,
                           centro_y=(y0 + y1) / 2,
                           cap_alvo=max(8, round(PROPORCAO_ROTULO * (y1 - y0))))
        if feito:
            vistas.append(caixa)
            acoes.append((caixa, feito))
    if acoes and not diagnostico:
        img.save(caminho)
    return acoes


def altura_nucleo(a, caixa, fundo):
    """Altura da banda das maiusculas de um texto ja delimitado."""
    x0, y0, x1, y1 = caixa
    mascara = mascara_do_fundo(a[y0:y1, x0:x1], fundo)
    ny0, ny1 = faixa_nucleo(mascara)
    return ny1 - ny0


def anonimizar_recibo(caminho, diagnostico):
    """Ecrã Recibo: tira o codigo da rubrica e deixa o nome por extenso."""
    img = Image.open(caminho).convert("RGB")
    a = np.asarray(img)
    fundo = cor_dominante(a)
    titulos = []
    for nome, y0, y1 in JANELAS_RECIBO:
        # A primeira linha de texto dentro da janela e o titulo da rubrica; o que
        # vem por baixo e a etiqueta "Real" da caixa de valores, que fica intocada.
        area = a[y0:y1, 0:620]
        claros = luminancia(area) > 120
        linhas = bandas(claros, 14, 40, minimo=20)
        if not linhas:
            print(f"   aviso: nada de texto na janela {y0}-{y1} (esperava «{nome}»)")
            continue
        ly0, ly1 = linhas[0]
        ys, xs = np.nonzero(claros[ly0:ly1])
        caixa = (int(xs.min()), y0 + ly0 + int(ys.min()),
                 int(xs.max()) + 1, y0 + ly0 + int(ys.max()) + 1)
        altura, largura = caixa[3] - caixa[1], caixa[2] - caixa[0]
        if not (14 <= altura <= 40 and 100 <= largura <= 620 and caixa[0] <= 60):
            print(f"   aviso: caixa inesperada para «{nome}»: {caixa} (altura {altura})")
            continue
        titulos.append((nome, caixa))
    if not titulos:
        return []
    # Os tres titulos sao do mesmo estilo na app: usa-se a menor banda de
    # maiusculas para que fiquem todos com a mesma altura (a descida do "p" de
    # "Sup." nao pode aumentar a fonte desse).
    cap = max(8, min(altura_nucleo(a, caixa, fundo) for _, caixa in titulos))
    acoes = []
    for nome, caixa in titulos:
        feito = redesenhar(img, a, caixa, nome, negrito=True, alinhamento="esquerda",
                           fundo=fundo, nucleo=True, cap_alvo=cap,
                           largura_max=620 - caixa[0])
        if feito:
            acoes.append((caixa, feito))
    if acoes and not diagnostico:
        img.save(caminho)
    return acoes


def main() -> int:
    parser = argparse.ArgumentParser(description="Anonimiza os ecras de docs/screens.")
    parser.add_argument("--diagnostico", action="store_true",
                        help="mostra as substituicoes previstas, sem gravar nada")
    args = parser.parse_args()

    tarefas = (
        ("escala.png", lambda c, d: anonimizar_calendario(c, (150, 985), False, d)),
        ("rotacao.png", lambda c, d: anonimizar_calendario(c, (205, 985), True, d)),
        ("horario.png", anonimizar_horario),
        ("recibo.png", anonimizar_recibo),
    )
    for nome, tarefa in tarefas:
        caminho = ECRAS / nome
        if not caminho.exists():
            print(f"{nome}: nao existe - corre primeiro o Temp/build-web-assets.ps1")
            return 1
        acoes = tarefa(caminho, args.diagnostico)
        aviso = " (diagnostico: nada gravado)" if args.diagnostico else ""
        print(f"{nome}: {len(acoes)} substituicoes{aviso}")
        for caixa, feito in acoes:
            print("   {:<14} caixa={} fundo={} tinta={} tamanho={}".format(
                feito["nome"], caixa, feito["fundo"], feito["tinta"], feito["tamanho"]))
    return 0


def anonimizar_horario(caminho, diagnostico):
    """Ecrã Horario: o disco do turno passa a "T" e a linha do posto a "Turno"."""
    img = Image.open(caminho).convert("RGB")
    a = np.asarray(img)
    fundo = cor_dominante(a)
    direita = np.abs(a[:, 560:640].astype(np.int16) - fundo.astype(np.int16)).max(axis=2) > 6
    acoes = []
    for y0, y1 in bandas(direita, 100, 150, minimo=40):
        cartao = cor_dominante(a[y0 + 8:y1 - 8, 340:620])
        # 1) abreviatura dentro do disco do turno (texto claro, entre o dia e a hora)
        janela = a[y0:y0 + 75, 218:280]
        claros = luminancia(janela) > 200
        bloco = grupo_de_colunas(claros)
        if bloco is not None:
            col0, col1 = bloco
            ys = np.flatnonzero(claros[:, col0:col1].any(axis=1))
            caixa = (218 + col0, y0 + int(ys.min()), 218 + col1, y0 + int(ys.max()) + 1)
            disco = cor_dominante(a[caixa[1] - 5:caixa[3] + 5, caixa[0] - 5:caixa[2] + 5])
            feito = redesenhar(img, a, caixa, "T", negrito=True, fundo=disco)
            if feito:
                acoes.append((caixa, feito))
        # 2) linha do posto, por baixo do intervalo de horas
        corte = y0 + int(0.55 * (y1 - y0))
        area = a[corte:y1 - 6, 14:340]
        ys, xs = np.nonzero(luminancia(area) > 90)
        if ys.size >= 40:
            caixa = (14 + int(xs.min()), corte + int(ys.min()),
                     14 + int(xs.max()) + 1, corte + int(ys.max()) + 1)
            feito = redesenhar(img, a, caixa, "Turno", negrito=False,
                               alinhamento="esquerda", fundo=cartao,
                               largura_max=340 - caixa[0])
            if feito:
                acoes.append((caixa, feito))
    if acoes and not diagnostico:
        img.save(caminho)
    return acoes


if __name__ == "__main__":
    raise SystemExit(main())

