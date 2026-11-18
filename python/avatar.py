"""
Jarvis 001 — Rosto dos apresentadores 🎭 (Ticket 050, Estúdio do Brabo)

Desenha os personagens ORIGINAIS do zero, só com numpy (nenhuma imagem baixada, nenhum programa novo):
  - brabo  → comentarista de GTA: boné aba reta, óculos escuros na testa, fone gamer, moletom preto.
  - mestre → "Mestre Parafuso", o marido de aluguel: capacete amarelo, bigode, camisa azul, braço com chave.

Cada personagem tem 3 bocas (fechada, meia, aberta) e 2 olhos (aberto, piscando).
A boca é escolhida pelo VOLUME da voz a cada quadro (estilo PNGtuber).

  python avatar.py amostra brabo     → grava brabo-amostra.png na pasta de saída dos cortes

Regras:
- Tudo é desenhado por código: arte nossa, não imita ninguém real.
- Sem dependência nova: numpy (já no projeto) + FFmpeg (já instalado) para gravar PNG/vídeo.
"""

import numpy as np

LADO = 400          # o desenho final é LADO x LADO (RGBA)
SUPER = 3           # desenha 3x maior e reduz → borda lisa (antisserrilhado)
FPS = 30
PERSONAGENS = ("brabo", "mestre", "capi")


def _cor(hexa, alfa=255):
    hexa = hexa.lstrip("#")
    return np.array([int(hexa[i:i + 2], 16) for i in (0, 2, 4)] + [alfa], dtype=np.float32)


class Tela:
    """Uma tela RGBA grande onde as formas são pintadas por cima, em ordem."""

    def __init__(self, lado=LADO, super_=SUPER):
        self.n = lado * super_
        self.s = super_ * lado / 400.0          # as medidas abaixo são pensadas numa tela de 400
        self.img = np.zeros((self.n, self.n, 4), dtype=np.float32)
        eixo = (np.arange(self.n, dtype=np.float32) + 0.5) / self.s
        self.y, self.x = np.meshgrid(eixo, eixo, indexing="ij")

    def pintar(self, mascara, cor):
        cor = _cor(cor) if isinstance(cor, str) else cor
        a = (cor[3] / 255.0) * mascara.astype(np.float32)
        for c in range(3):
            self.img[..., c] = self.img[..., c] * (1 - a) + cor[c] * a
        self.img[..., 3] = np.maximum(self.img[..., 3], 255.0 * a)

    def elipse(self, cx, cy, rx, ry, cor, girar=0.0):
        dx, dy = self.x - cx, self.y - cy
        if girar:
            t = np.deg2rad(girar)
            dx, dy = dx * np.cos(t) + dy * np.sin(t), -dx * np.sin(t) + dy * np.cos(t)
        self.pintar((dx / rx) ** 2 + (dy / ry) ** 2 <= 1.0, cor)

    def retangulo(self, x0, y0, x1, y1, cor, raio=0.0):
        x = np.clip(self.x, x0 + raio, x1 - raio)
        y = np.clip(self.y, y0 + raio, y1 - raio)
        dentro = (self.x >= x0) & (self.x <= x1) & (self.y >= y0) & (self.y <= y1)
        if raio:
            dentro &= (self.x - x) ** 2 + (self.y - y) ** 2 <= raio ** 2
        self.pintar(dentro, cor)

    def poligono(self, pontos, cor):
        """Polígono CONVEXO (pontos em ordem)."""
        dentro = np.ones_like(self.x, dtype=bool)
        sinal = None
        for (x0, y0), (x1, y1) in zip(pontos, pontos[1:] + pontos[:1]):
            lado = (x1 - x0) * (self.y - y0) - (y1 - y0) * (self.x - x0)
            if sinal is None:
                cx = sum(p[0] for p in pontos) / len(pontos)
                cy = sum(p[1] for p in pontos) / len(pontos)
                sinal = np.sign((x1 - x0) * (cy - y0) - (y1 - y0) * (cx - x0)) or 1.0
            dentro &= lado * sinal >= 0
        self.pintar(dentro, cor)

    def anel(self, cx, cy, r_fora, r_dentro, cor, so_cima=False):
        d = (self.x - cx) ** 2 + (self.y - cy) ** 2
        m = (d <= r_fora ** 2) & (d >= r_dentro ** 2)
        if so_cima:
            m &= self.y <= cy
        self.pintar(m, cor)

    def linha(self, x0, y0, x1, y1, esp, cor):
        """Traço grosso com ponta redonda, de (x0, y0) até (x1, y1)."""
        vx, vy = x1 - x0, y1 - y0
        k = np.clip(((self.x - x0) * vx + (self.y - y0) * vy) / max(vx * vx + vy * vy, 1e-6), 0, 1)
        d2 = (self.x - x0 - k * vx) ** 2 + (self.y - y0 - k * vy) ** 2
        self.pintar(d2 <= (esp / 2) ** 2, cor)

    def zoom(self, z):
        """Desenha o que vier depois z vezes do tamanho, centrado (z=0.8 → 80%)."""
        eixo = (np.arange(self.n, dtype=np.float32) + 0.5) / self.s
        eixo = (eixo - 200.0) / z + 200.0
        self.y, self.x = np.meshgrid(eixo, eixo, indexing="ij")

    def pronta(self):
        """Reduz para o tamanho final (média dos blocos) → uint8 RGBA."""
        k = SUPER
        lado = self.n // k
        bloco = self.img.reshape(lado, k, lado, k, 4)
        alfa = bloco[..., 3:4]
        soma_a = alfa.sum(axis=(1, 3))
        cor = (bloco[..., :3] * alfa).sum(axis=(1, 3)) / np.maximum(soma_a, 1e-6)
        final = np.concatenate([cor, soma_a / (k * k)], axis=-1)
        return np.clip(final + 0.5, 0, 255).astype(np.uint8)


# ---------------------------- os personagens ----------------------------

PELE = "#c98b5e"
PELE_SOMBRA = "#a96f47"


def _boca(t, boca, cx=200, cy=262):
    if boca == 0:                                   # fechada: sorriso de canto (deboche)
        t.elipse(cx + 6, cy, 26, 5, "#5a2a1a", girar=-8)
        t.elipse(cx + 6, cy - 3, 26, 5, PELE, girar=-8)
        t.elipse(cx + 30, cy - 6, 4, 4, "#5a2a1a")
    elif boca == 1:                                 # meia
        t.elipse(cx + 4, cy + 2, 24, 11, "#3b1010")
        t.retangulo(cx - 14, cy - 8, cx + 22, cy - 2, "#f4f1ea", raio=2)
    else:                                           # aberta
        t.elipse(cx + 4, cy + 6, 28, 20, "#3b1010")
        t.retangulo(cx - 16, cy - 12, cx + 24, cy - 4, "#f4f1ea", raio=3)
        t.elipse(cx + 4, cy + 18, 15, 8, "#d9534f")


def _olhos(t, piscando, cy=200):
    for cx in (162, 238):
        if piscando:
            t.retangulo(cx - 18, cy - 2, cx + 18, cy + 3, "#2b1a12", raio=2)
        else:
            t.elipse(cx, cy, 19, 15, "#ffffff")
            t.elipse(cx + 4, cy + 2, 9, 9, "#3a2416")
            t.elipse(cx + 7, cy - 1, 3, 3, "#ffffff")


def _cabeca(t):
    t.retangulo(178, 270, 222, 330, PELE_SOMBRA)                      # pescoço
    t.elipse(117, 212, 14, 22, PELE)                                   # orelhas
    t.elipse(283, 212, 14, 22, PELE)
    t.elipse(200, 205, 88, 100, PELE)                                  # rosto
    t.elipse(200, 238, 14, 10, PELE_SOMBRA)                            # nariz


def desenhar_brabo(boca=0, piscando=False, fundo=True):
    t = Tela()
    if fundo:
        t.retangulo(0, 0, 400, 400, "#14141a", raio=36)
        t.anel(200, 200, 197, 184, "#ff7a00")
    t.elipse(200, 420, 175, 110, "#111114")                            # moletom
    t.poligono([(170, 312), (230, 312), (200, 352)], PELE_SOMBRA)       # gola
    t.retangulo(194, 330, 206, 400, "#ff7a00")                         # cordão do moletom
    _cabeca(t)
    # boné aba reta (preto, aba para o lado = deboche)
    t.elipse(200, 135, 92, 62, "#111114")
    t.retangulo(108, 128, 292, 150, "#111114")
    t.retangulo(230, 136, 345, 156, "#3a3a46", raio=6)                 # aba
    t.elipse(200, 104, 18, 18, "#ff7a00")                              # logo laranja
    # óculos escuros na testa
    t.retangulo(146, 152, 254, 158, "#2a2a2a")
    t.elipse(168, 160, 24, 13, "#0b0b0d")
    t.elipse(232, 160, 24, 13, "#0b0b0d")
    t.elipse(160, 156, 6, 3, "#5c5c66")
    # sobrancelhas: uma levantada (cara de "sério isso?")
    t.poligono([(142, 182), (182, 176), (182, 183), (142, 189)], "#2b1a12")
    t.poligono([(218, 172), (258, 164), (258, 171), (218, 179)], "#2b1a12")
    _olhos(t, piscando)
    _boca(t, boca)
    # fone gamer
    t.anel(200, 200, 112, 100, "#2b2b33", so_cima=True)
    t.retangulo(84, 180, 118, 250, "#2b2b33", raio=14)
    t.retangulo(282, 180, 316, 250, "#2b2b33", raio=14)
    t.retangulo(90, 196, 112, 234, "#ff7a00", raio=8)
    t.poligono([(100, 240), (108, 240), (172, 282), (166, 290)], "#2b2b33")  # microfone
    t.elipse(170, 286, 9, 9, "#111114")
    return t.pronta()


def desenhar_mestre(boca=0, piscando=False, fundo=True, braco=True, lado=LADO):
    t = Tela(lado=lado)
    if fundo:
        t.retangulo(0, 0, 400, 400, "#eef3f8", raio=36)
        t.anel(200, 200, 197, 184, "#1f6fd1")
    t.elipse(200, 420, 180, 110, "#1f6fd1")                            # camisa azul
    t.poligono([(168, 312), (232, 312), (200, 356)], PELE_SOMBRA)
    t.retangulo(150, 330, 162, 400, "#f2c200")                         # suspensório
    t.retangulo(238, 330, 250, 400, "#f2c200")
    if braco:                                                          # braço com a chave inglesa
        t.poligono([(300, 345), (340, 330), (372, 250), (348, 238)], "#1f6fd1")
        t.elipse(362, 236, 20, 20, PELE)
        t.retangulo(355, 150, 369, 230, "#9aa3ad", raio=5)
        t.elipse(362, 148, 20, 14, "#9aa3ad")
        if fundo:
            t.elipse(362, 140, 9, 9, "#eef3f8")
    _cabeca(t)
    t.elipse(200, 125, 98, 62, "#f2c200")                              # capacete
    t.retangulo(96, 128, 304, 150, "#f2c200")
    t.retangulo(84, 142, 316, 156, "#d9a800", raio=6)
    t.retangulo(192, 70, 208, 142, "#d9a800", raio=4)
    t.poligono([(142, 180), (182, 178), (182, 185), (142, 187)], "#6b4a2a")   # sobrancelhas calmas
    t.poligono([(218, 178), (258, 180), (258, 187), (218, 185)], "#6b4a2a")
    _olhos(t, piscando)
    _boca(t, boca, cy=268)
    t.elipse(176, 252, 30, 11, "#6b4a2a", girar=8)                      # bigode
    t.elipse(224, 252, 30, 11, "#6b4a2a", girar=-8)
    return t.pronta()


def desenhar_capi(boca=0, piscando=False, fundo=True):
    """Capi: capivara gamer (Ticket 055), personagem de outro canal. Tranquilona, de fone rosa e microfone."""
    t = Tela()
    if fundo:
        t.retangulo(0, 0, 400, 400, "#121a2c", raio=36)
        t.anel(200, 200, 197, 184, "#18e0c8")
    for x in (118, 282):                                               # orelhas
        t.elipse(x, 110, 26, 22, "#70482a")
        t.elipse(x, 112, 13, 11, "#50321c")
    t.retangulo(80, 100, 320, 362, "#9c683c", raio=112)                # cabeça
    t.elipse(200, 290, 102, 64, "#ba8658")                             # focinho
    for x0, x1 in ((186, 176), (200, 200), (214, 224)):                # topete
        t.linha(x0, 108, x1, 80, 7, "#6e4626")
    for x in (148, 252):                                               # olhos sonolentos
        if piscando:
            t.retangulo(x - 18, 198, x + 18, 205, "#140e0a", raio=3)
        else:
            t.elipse(x, 202, 17, 19, "#140e0a")
            t.elipse(x + 6, 195, 5, 5, "#ffffff")
            t.retangulo(x - 24, 176, x + 24, 192, "#9c683c")
    for x in (165, 235):                                               # narinas
        t.elipse(x, 262, 12, 8, "#3c2414")
    if boca == 0:
        t.linha(176, 304, 200, 312, 6, "#3c1e12")
        t.linha(200, 312, 224, 304, 6, "#3c1e12")
    else:
        h = 26 if boca == 1 else 44
        t.retangulo(168, 300, 232, 300 + h, "#46141a", raio=12)
        t.elipse(200, 300 + h * 0.78, 22, max(4, h * 0.3), "#eb6478")
        t.retangulo(186, 300, 214, 310, "#faf9f5", raio=2)              # dentões
    t.anel(200, 218, 142, 128, "#1e1e26", so_cima=True)                # fone
    t.retangulo(50, 170, 88, 252, "#ff3b6b", raio=15)
    t.retangulo(312, 170, 350, 252, "#ff3b6b", raio=15)
    t.linha(332, 246, 252, 318, 8, "#1e1e26")                          # microfone
    t.elipse(248, 320, 10, 10, "#18e0c8")
    return t.pronta()


def desenhar_tomada(lado=600):
    """Tomada padrão brasileiro (3 pinos) com os 3 fios: fase, neutro e terra no MEIO. Para o quadro de ensino."""
    t = Tela(lado=lado)
    _tomada(t)
    return t.pronta()


def _tomada(t):
    t.retangulo(40, 230, 200, 246, "#1a1a1a", raio=6)          # fio fase (preto) → borne da esquerda
    t.retangulo(40, 236, 120, 240, "#c98b3a")
    t.retangulo(200, 280, 360, 296, "#3b8fd9", raio=6)         # fio neutro (azul claro) → borne da direita
    t.retangulo(280, 286, 360, 290, "#c98b3a")
    t.retangulo(185, 300, 215, 396, "#2f9e44", raio=6)         # fio terra (verde) → borne do meio
    t.retangulo(110, 40, 290, 330, "#fbfbfb", raio=26)         # espelho
    t.retangulo(110, 40, 290, 330, "#d8d8d8", raio=26)
    t.retangulo(116, 46, 284, 324, "#ffffff", raio=22)
    t.elipse(200, 185, 70, 70, "#e9e9e9")                       # rebaixo redondo
    t.elipse(172, 200, 11, 11, "#2b2b2b")                       # 2 furos (fase/neutro)
    t.elipse(228, 200, 11, 11, "#2b2b2b")
    t.elipse(200, 160, 11, 11, "#2b2b2b")                       # furo do terra (no meio, em cima)
    t.elipse(200, 74, 7, 7, "#bdbdbd")                          # parafusos
    t.elipse(200, 296, 7, 7, "#bdbdbd")


# ---------------------------- cartões do ensino (Mestre Parafuso) ----------------------------

AZUL, AZUL_ESCURO, AMARELO = "#1f6fd1", "#0b2d5c", "#f2c200"
COBRE, METAL, PERIGO, CERTO = "#c97b3a", "#9aa3ad", "#d63b3b", "#2f9e44"


def _seta_baixo(t, cx, y0, y1, cor=AMARELO):
    t.retangulo(cx - 9, y0, cx + 9, y1 - 18, cor, raio=4)
    t.poligono([(cx - 26, y1 - 22), (cx + 26, y1 - 22), (cx, y1)], cor)


def _check(t, cx, cy, r, fundo=CERTO):
    t.elipse(cx, cy, r, r, fundo)
    t.linha(cx - r * .45, cy, cx - r * .1, cy + r * .38, r * .22, "#ffffff")
    t.linha(cx - r * .1, cy + r * .38, cx + r * .5, cy - r * .35, r * .22, "#ffffff")


def _mola(t, x0, y0, x1, y1, cor=COBRE, voltas=11, r=22):
    """A resistência: uma mola de fio, vista de lado (anéis em fila)."""
    t.linha(x0 - 26, y0 + 10, x0, y0, 6, METAL)
    t.linha(x1, y1, x1 + 26, y1 - 10, 6, METAL)
    for i in range(voltas):
        k = i / (voltas - 1)
        t.anel(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, r, r - 6, cor)


def _etiqueta(t, x, y, cor=AMARELO):
    """Plaquinha de voltagem/potência (o mesmo desenho = o mesmo valor)."""
    t.retangulo(x, y, x + 84, y + 46, cor, raio=8)
    t.retangulo(x + 12, y + 12, x + 72, y + 19, AZUL_ESCURO, raio=3)
    t.retangulo(x + 12, y + 27, x + 52, y + 34, AZUL_ESCURO, raio=3)


def _cabeca_chuveiro(t, cx, cy, aberto=False, escala=1.0):
    e = escala
    t.retangulo(cx - 10 * e, cy - 130 * e, cx + 10 * e, cy - 30 * e, METAL, raio=4)       # cano
    t.elipse(cx, cy, 128 * e, 56 * e, "#c9d1da")
    t.elipse(cx, cy - 4 * e, 120 * e, 50 * e, "#f4f6f8")
    if aberto:                                                          # por dentro: a mola
        t.elipse(cx, cy + 4 * e, 96 * e, 34 * e, "#3a4450")
        for i in range(9):
            t.anel(cx - 64 * e + i * 16 * e, cy + 4 * e, 13 * e, 8 * e, COBRE)
    else:
        for i in range(-3, 4):
            t.elipse(cx + i * 26 * e, cy + 26 * e, 5 * e, 3 * e, "#7d8794")


def _suave(p):
    p = min(max(float(p), 0.0), 1.0)
    return p * p * (3 - 2 * p)


def _gotas(t, cx, cy, cor, linhas=4, p=0.0):
    """Gotas caindo. p de 0 a 1 = um ciclo (desce 2 fileiras e repete igualzinho)."""
    passo = 46
    fundo = cy + (linhas - 0.5) * passo
    for j in range(-2, linhas):
        y = cy + (j + 2 * p) * passo
        if y > fundo:
            continue
        for i in range(-3, 4):
            t.elipse(cx + i * 30 + (j % 2) * 15, y, 6, 13, cor)


def _disjuntor(t, cx, y0, ligado, destaque=False, e=1.0, alavanca=None):
    """alavanca: 0 = em cima (ligado) … 1 = embaixo (desligado). Sem valor: segue `ligado`."""
    if alavanca is None:
        alavanca = 0.0 if ligado else 1.0
    if destaque:
        t.retangulo(cx - 40 * e, y0 - 14 * e, cx + 40 * e, y0 + 194 * e, AMARELO, raio=14 * e)
    t.retangulo(cx - 30 * e, y0, cx + 30 * e, y0 + 180 * e, METAL, raio=8 * e)
    t.retangulo(cx - 26 * e, y0 + 4 * e, cx + 26 * e, y0 + 176 * e, "#ffffff", raio=7 * e)
    t.retangulo(cx - 14 * e, y0 + 30 * e, cx + 14 * e, y0 + 150 * e, "#dfe4ea", raio=5 * e)
    desligado = alavanca >= 0.5
    y = y0 + (32 + 60 * alavanca) * e
    t.retangulo(cx - 12 * e, y, cx + 12 * e, y + 56 * e, PERIGO if desligado else "#2b2b33", raio=5 * e)
    t.elipse(cx, y0 + 163 * e, 6 * e, 6 * e, PERIGO if desligado else CERTO)


def _c_disjuntor(t, p=1.0):
    t.retangulo(60, 70, 340, 330, "#c9d1da", raio=18)
    t.retangulo(74, 84, 326, 316, "#e6ebf0", raio=12)
    _disjuntor(t, 130, 110, True)
    _disjuntor(t, 200, 110, False, destaque=True, alavanca=_suave(p))   # desce: desligando
    _disjuntor(t, 270, 110, True)


def _c_gelado(t, p=0.0):
    _gotas(t, 175, 225, "#9fd3ff", 3, p)
    _cabeca_chuveiro(t, 175, 150)
    cx, cy = 328, 72                                                   # floco de neve
    for ang in (0, 60, 120):
        a = np.deg2rad(ang + 30 * p)                                   # gira devagar
        t.linha(cx - 46 * np.cos(a), cy - 46 * np.sin(a), cx + 46 * np.cos(a), cy + 46 * np.sin(a), 10, AZUL)
    t.elipse(cx, cy, 12, 12, "#ffffff")


def _c_chuveiro(t):
    _cabeca_chuveiro(t, 200, 175, aberto=True)
    t.elipse(200, 318, 112, 36, "#c9d1da")                             # a tampa, já solta embaixo
    t.elipse(200, 314, 106, 31, "#f4f6f8")
    for i in range(-3, 4):
        t.elipse(200 + i * 26, 318, 5, 3, "#7d8794")


def _c_resistencia(t):
    t.elipse(200, 205, 150, 95, "#e9eef4")
    _mola(t, 95, 250, 305, 160)
    t.elipse(70, 262, 14, 14, METAL)
    t.elipse(330, 148, 14, 14, METAL)


def _c_pecas(t):
    _cabeca_chuveiro(t, 105, 205, escala=0.55)                         # o chuveiro...
    _etiqueta(t, 63, 250)
    t.retangulo(180, 175, 220, 187, AZUL_ESCURO, raio=4)               # ... "=" ...
    t.retangulo(180, 199, 220, 211, AZUL_ESCURO, raio=4)
    _mola(t, 255, 205, 335, 170, voltas=7, r=18)                       # ... a resistência
    _etiqueta(t, 258, 250)
    _check(t, 200, 330, 38)


def _c_encaixe(t, p=0.0):
    q = _suave(p)
    if q < 0.8:
        _seta_baixo(t, 200, 128, 215)
    _cabeca_chuveiro(t, 200, 290, aberto=True)
    _mola(t, 120, 95 + 70 * q, 280, 95 + 70 * q, cor="#e08c45", voltas=10, r=20)   # a nova desce


def _c_agua(t, p=0.0):
    _gotas(t, 165, 215, "#3b8fd9", 4, p)
    _cabeca_chuveiro(t, 165, 140)
    t.elipse(325, 120, 34, 34, AZUL)                                   # registro aberto
    t.retangulo(318, 60, 332, 120, AZUL_ESCURO, raio=6)
    t.elipse(325, 120, 12, 12, "#ffffff")
    _disjuntor(t, 325, 220, False, e=0.6)                              # e o disjuntor AINDA desligado


def _c_perigo(t, p=0.0):
    t.poligono([(200, 40), (330, 260), (70, 260)], "#1a1a1a")
    t.poligono([(200, 64), (308, 248), (92, 248)], AMARELO)
    t.retangulo(190, 120, 210, 200, "#1a1a1a", raio=8)
    t.elipse(200, 224, 12, 12, "#1a1a1a")
    t.linha(40, 330, 360, 330, 16, "#1a1a1a")                          # fio derretido
    t.elipse(200, 330, 40, 18, "#6b3a1a")
    t.elipse(194, 326, 24, 10, "#e0712b")
    t.elipse(290, 330, 18, 10, "#6b3a1a")                              # 2º ponto derretido, de onde sai a fumaça
    for i in range(3):                                                 # fumaça subindo
        k = (p + i / 3) % 1.0
        r = 10 + 10 * k
        t.elipse(290 + 18 * np.sin(6.28 * k + i), 312 - 80 * k, r, r * 0.75, "#a7adb5")


def _c_ok(t, p=1.0):
    k = 0.35 + 0.65 * _suave(p) + 0.12 * np.sin(np.pi * min(p, 1.0))  # cresce com um pulinho
    _check(t, 200, 190, 120 * k)
    t.elipse(200, 340, 70, 18, AMARELO)


# --- torneira pingando (16/11) ---

def _torneira(t, cx, cy, e=1.0, pingando=True, p=0.0):
    """Torneira de parede vista de lado: cano na parede, corpo, bica para baixo e a cruzeta em cima."""
    t.retangulo(cx - 150 * e, cy - 22 * e, cx - 120 * e, cy + 22 * e, "#c9d1da", raio=4 * e)   # canopla da parede
    t.retangulo(cx - 125 * e, cy - 14 * e, cx - 30 * e, cy + 14 * e, METAL, raio=6 * e)        # cano
    t.retangulo(cx - 40 * e, cy - 34 * e, cx + 40 * e, cy + 26 * e, "#b8c0c9", raio=12 * e)    # corpo
    t.retangulo(cx - 14 * e, cy - 90 * e, cx + 14 * e, cy - 30 * e, METAL, raio=5 * e)         # haste
    t.retangulo(cx - 52 * e, cy - 104 * e, cx + 52 * e, cy - 84 * e, "#7d8794", raio=8 * e)    # cruzeta
    t.retangulo(cx + 20 * e, cy - 12 * e, cx + 110 * e, cy + 12 * e, METAL, raio=6 * e)        # bica
    t.retangulo(cx + 88 * e, cy - 6 * e, cx + 110 * e, cy + 50 * e, METAL, raio=6 * e)
    if pingando:                                                       # 3 gotas caindo em fila
        for i in range(3):
            k = (p + i / 3) % 1.0
            t.elipse(cx + 99 * e, cy + (62 + 125 * k) * e, (5 + 4 * k) * e, (8 + 6 * k) * e, "#3b8fd9")


def _reparo(t, cx, cy, cor=METAL, e=1.0):
    """O reparo (mecanismo da torneira): rosca + corpo + borrachinha (vedante) embaixo."""
    t.retangulo(cx - 12 * e, cy - 70 * e, cx + 12 * e, cy - 30 * e, "#7d8794", raio=4 * e)     # pino da cruzeta
    t.retangulo(cx - 30 * e, cy - 34 * e, cx + 30 * e, cy + 10 * e, cor, raio=6 * e)           # sextavado
    for i in range(4):                                                                          # rosca
        t.retangulo(cx - 26 * e, cy + (14 + i * 12) * e, cx + 26 * e, cy + (20 + i * 12) * e, cor, raio=3 * e)
    t.retangulo(cx - 20 * e, cy + 10 * e, cx + 20 * e, cy + 62 * e, cor, raio=4 * e)
    t.elipse(cx, cy + 70 * e, 24 * e, 9 * e, "#2b2b33")                                        # vedante


def _c_torneira(t, p=0.0):
    t.retangulo(30, 60, 70, 360, "#e6ebf0", raio=6)                    # parede
    t.elipse(290, 345, 60, 12, "#9fd3ff")                              # poça
    t.elipse(290, 343, 40, 7, "#cfe9ff")
    _torneira(t, 190, 150, p=p)


def _registro(t, angulo, seta_horaria):
    """Registro de esfera: manopla NA LINHA do cano = aberto; ATRAVESSADA (90°) = fechado."""
    t.retangulo(40, 190, 360, 222, METAL, raio=10)                     # cano
    t.elipse(200, 206, 62, 62, "#7d8794")
    t.elipse(200, 206, 48, 48, "#b8c0c9")
    a = np.deg2rad(angulo)
    dx, dy = 52 * np.cos(a), 52 * np.sin(a)
    t.linha(200 - dx, 206 - dy, 200 + dx, 206 + dy, 30, PERIGO)        # manopla
    t.elipse(200, 206, 14, 14, "#2b2b33")
    if seta_horaria:                                                   # seta: sentido do giro
        t.linha(230, 110, 290, 110, 12, AZUL)
        t.poligono([(286, 92), (316, 110), (286, 128)], AZUL)
    else:
        t.linha(110, 110, 170, 110, 12, AZUL)
        t.poligono([(114, 92), (84, 110), (114, 128)], AZUL)


def _c_registro(t, p=1.0):
    q = _suave(p)
    _registro(t, 90 * q, seta_horaria=True)                            # gira até ATRAVESSAR = fechado
    if q > 0.95:
        _check(t, 200, 320, 36)


def _c_abre_registro(t, p=1.0):
    q = _suave(p)
    _registro(t, 90 * (1 - q), seta_horaria=False)                     # volta para a linha do cano = aberto
    if q > 0.95:
        for i, x in enumerate((372, 384)):                             # água voltando a passar
            t.elipse(x - 20, 206 + 14 * (i - 0.5), 8, 5, "#3b8fd9")


def _c_reparo(t):
    _reparo(t, 120, 170)                                               # velho...
    t.retangulo(180, 175, 220, 187, AZUL_ESCURO, raio=4)               # ... "=" ...
    t.retangulo(180, 199, 220, 211, AZUL_ESCURO, raio=4)
    _reparo(t, 280, 170, cor="#d9dee4")                                # ... novo, igualzinho
    _etiqueta(t, 78, 262)
    _etiqueta(t, 238, 262)
    _check(t, 200, 345, 30)


def _c_chave(t, p=0.0):
    _torneira(t, 170, 230, e=0.8, pingando=False)
    t.retangulo(132, 142, 208, 204, "#f2e6c9", raio=10)                # pano protegendo o metal
    giro = -32 * (0.5 - 0.5 * np.cos(2 * np.pi * p))                   # vai e volta para a esquerda
    a = np.deg2rad(-27 + giro)
    t.linha(170, 172, 170 + 180 * np.cos(a), 172 + 180 * np.sin(a), 22, "#5f6873")   # cabo da chave
    t.elipse(170, 172, 28, 28, "#5f6873")                              # boca da chave
    t.elipse(170, 172, 13, 13, "#f2e6c9")
    t.linha(90, 80, 150, 60, 12, AZUL)                                 # seta: gira para a esquerda (solta)
    t.poligono([(86, 62), (100, 96), (70, 92)], AZUL)


CARTOES = {"tomada": _tomada, "disjuntor": _c_disjuntor, "gelado": _c_gelado, "chuveiro": _c_chuveiro,
           "resistencia": _c_resistencia, "pecas": _c_pecas, "encaixe": _c_encaixe, "agua": _c_agua,
           "perigo": _c_perigo, "ok": _c_ok, "torneira": _c_torneira, "registro": _c_registro,
           "abre_registro": _c_abre_registro, "reparo": _c_reparo, "chave": _c_chave}

# Cartões que mexem (17/11): "laco" repete sem parar; "uma_vez" faz o movimento e para no fim.
ANIMADOS = {"torneira": "laco", "gelado": "laco", "agua": "laco", "perigo": "laco", "chave": "laco",
            "disjuntor": "uma_vez", "registro": "uma_vez", "abre_registro": "uma_vez", "encaixe": "uma_vez",
            "ok": "uma_vez"}
QUADROS_ANIMACAO = 12


def desenhar_cartao(nome, lado=760, p=None):
    """Cartão branco com borda amarela, sombra e o desenho do passo. Só nomes de CARTOES (allowlist).
    `p` (0 a 1) = momento da animação; sem `p`, o cartão sai no jeito parado de sempre."""
    if nome not in CARTOES:
        raise ValueError("cartão desconhecido")
    t = Tela(lado=lado)
    t.retangulo(26, 30, 390, 394, _cor("#000000", 70), raio=34)       # sombra
    t.retangulo(14, 14, 386, 386, AMARELO, raio=34)                   # borda amarela
    t.retangulo(22, 22, 378, 378, "#ffffff", raio=28)
    t.zoom(0.84)
    if p is None or nome not in ANIMADOS:
        CARTOES[nome](t)
    else:
        CARTOES[nome](t, p)
    return t.pronta()


def quadros_cartao(nome, lado=760, n=QUADROS_ANIMACAO):
    """([imagens], modo). Cartão parado → 1 imagem e modo "fixo"."""
    modo = ANIMADOS.get(nome)
    if modo is None:
        return [desenhar_cartao(nome, lado)], "fixo"
    if modo == "laco":
        ps = [i / n for i in range(n)]                  # o último não repete o primeiro (laço sem tranco)
    else:
        ps = [i / (n - 1) for i in range(n)]            # termina exatamente no fim do movimento
    return [desenhar_cartao(nome, lado, p) for p in ps], modo


def _borrar(a, raio):
    """Borrão de caixa (média de uma janela) nos dois eixos."""
    for eixo in (0, 1):
        pad = [(0, 0), (0, 0)]
        pad[eixo] = (raio + 1, raio)
        soma = np.cumsum(np.pad(a, pad), axis=eixo)
        n = soma.shape[eixo]
        a = (np.take(soma, np.arange(2 * raio + 1, n), axis=eixo)
             - np.take(soma, np.arange(0, n - 2 * raio - 1), axis=eixo)) / (2 * raio + 1)
    return a


def com_sombra(img, dx=14, dy=10, raio=10, forca=0.35):
    """Põe uma sombra leve atrás do desenho (o alfa borrado e deslocado)."""
    frente = img[..., 3].astype(np.float32) / 255.0
    borrado = _borrar(frente, raio)
    sombra = np.zeros_like(frente)
    h, w = frente.shape
    sombra[dy:, dx:] = borrado[:h - dy, :w - dx] * forca
    alfa = frente + sombra * (1 - frente)
    cor = img[..., :3].astype(np.float32) * (frente / np.maximum(alfa, 1e-6))[..., None]
    return np.concatenate([cor, alfa[..., None] * 255.0], axis=-1).clip(0, 255).astype(np.uint8)


MOLDURAS = {"brabo": ("#14141a", "#ff7a00"), "mestre": ("#eef3f8", "#1f6fd1"), "capi": ("#121a2c", "#18e0c8")}


def _sobre(baixo, cima):
    """Põe a imagem RGBA `cima` por cima de `baixo` (mesmo tamanho)."""
    a = cima[..., 3:4].astype(np.float32) / 255.0
    b = baixo[..., 3:4].astype(np.float32) / 255.0
    sai_a = a + b * (1 - a)
    cor = (cima[..., :3] * a + baixo[..., :3] * b * (1 - a)) / np.maximum(sai_a, 1e-6)
    return np.clip(np.concatenate([cor, sai_a * 255.0], axis=-1) + 0.5, 0, 255).astype(np.uint8)


def moldura(personagem, lado):
    """Quadradinho arredondado + anel colorido do personagem (fica ATRÁS do retrato 3D)."""
    fundo, anel = MOLDURAS[personagem]
    t = Tela(lado=lado)
    t.retangulo(0, 0, 400, 400, fundo, raio=36)
    t.anel(200, 200, 197, 184, anel)
    return t.pronta()


def emoldurar(personagem, retrato, base=None):
    """Retrato 3D por cima do anel (efeito 3D 'saindo' da moldura), sem passar do quadradinho."""
    base = moldura(personagem, retrato.shape[0]) if base is None else base
    img = _sobre(base, retrato)
    img[..., 3] = np.minimum(img[..., 3], base[..., 3])
    return img


def sprites(personagem, fundo=True, lado=LADO, retrato=True):
    """{(boca, piscando): imagem} — os 6 desenhos de um personagem, feitos uma vez só.
    Ticket 055: se existirem as 3 imagens 3D do personagem (retratos.py), usa elas no lugar do desenho.
    As imagens não têm olho fechado, então o retrato não pisca."""
    if personagem not in PERSONAGENS:
        raise ValueError("personagem desconhecido")
    if retrato:
        import retratos
        fotos = retratos.retratos(personagem, lado)
        if fotos is not None:
            if fundo:
                fotos = [emoldurar(personagem, f) for f in fotos]
            return {(b, p): fotos[b] for b in (0, 1, 2) for p in (False, True)}
    if personagem == "brabo":
        return {(b, p): desenhar_brabo(b, p, fundo=fundo) for b in (0, 1, 2) for p in (False, True)}
    if personagem == "capi":
        return {(b, p): desenhar_capi(b, p, fundo=fundo) for b in (0, 1, 2) for p in (False, True)}
    return {(b, p): desenhar_mestre(b, p, fundo=fundo, lado=lado) for b in (0, 1, 2) for p in (False, True)}


# ---------------------------- animação pela voz ----------------------------

def bocas_pela_voz(voz, taxa, fps=FPS):
    """Volume da voz em cada quadro → boca 0/1/2. Silêncio = boca fechada."""
    voz = np.asarray(voz, dtype=np.float32)
    passo = taxa / fps
    quadros = int(np.ceil(len(voz) / passo)) if len(voz) else 0
    rms = np.zeros(quadros, dtype=np.float32)
    for i in range(quadros):
        trecho = voz[int(i * passo):int((i + 1) * passo)]
        if trecho.size:
            rms[i] = float(np.sqrt(np.mean(trecho ** 2)))
    topo = float(np.percentile(rms[rms > 0], 90)) if np.any(rms > 0) else 0.0
    if topo <= 0:
        return np.zeros(quadros, dtype=np.int8)
    nivel = rms / topo
    bocas = np.where(nivel > 0.55, 2, np.where(nivel > 0.18, 1, 0)).astype(np.int8)
    # boca aberta segura no mínimo 2 quadros: sem isso ela "treme"
    for i in range(1, quadros):
        if bocas[i] == 0 and bocas[i - 1] > 0 and (i < 2 or bocas[i - 2] == 0):
            bocas[i] = 1
    return bocas


def piscadas(quadros, fps=FPS, semente=7):
    """Pisca a cada 2,5–4,5 s, por 3 quadros. Semente fixa → o mesmo vídeo sai sempre igual."""
    rng = np.random.default_rng(semente)
    marca = np.zeros(quadros, dtype=bool)
    t = int(fps * 1.5)
    while t < quadros:
        marca[t:t + 3] = True
        t += int(fps * rng.uniform(2.5, 4.5))
    return marca


def nivel_da_voz(voz, taxa, fps=FPS):
    """Volume da voz por quadro, 0..1 (1 = fala forte)."""
    voz = np.asarray(voz, dtype=np.float32)
    passo = taxa / fps
    n = int(np.ceil(len(voz) / passo)) if len(voz) else 0
    rms = np.zeros(n, dtype=np.float32)
    for i in range(n):
        trecho = voz[int(i * passo):int((i + 1) * passo)]
        if trecho.size:
            rms[i] = float(np.sqrt(np.mean(trecho ** 2)))
    topo = float(np.percentile(rms[rms > 0], 90)) if np.any(rms > 0) else 0.0
    return np.clip(rms / topo, 0, 1) if topo > 0 else np.zeros(n, dtype=np.float32)


def movimento(bocas, nivel, fps=FPS):
    """Ticket 055: o que deixa o retrato 3D vivo, quadro a quadro → lista de (boca, ângulo, escala, dy).
    - boca: passa de uma imagem para a outra em ~2 quadros (número fracionário: 0.5 = metade fechada/metade meia);
    - respira (sobe/desce devagar) e balança a cabeça de leve, mais quando fala;
    - 'tranco': zoom rápido quando a voz dá um pico (começo de palavra forte)."""
    saida = []
    boca = energia = tranco = 0.0
    anterior = 0.0
    for i, (b, v) in enumerate(zip(bocas, nivel)):
        t = i / fps
        boca += (float(b) - boca) * 0.6
        energia += (float(v) - energia) * (0.5 if v > energia else 0.12)
        tranco = max(tranco * 0.78, max(0.0, float(v) - anterior - 0.25) * 1.6)
        anterior = float(v)
        angulo = 1.1 * np.sin(2 * np.pi * t / 4.3) + 1.8 * energia * np.sin(2 * np.pi * t / 1.6)
        escala = 1.035 + 0.008 * np.sin(2 * np.pi * t / 3.4) + 0.05 * min(tranco, 1.0)
        dy = 2.5 * np.sin(2 * np.pi * t / 3.4) - 5.0 * energia
        saida.append((min(boca, 2.0), float(angulo), float(escala), float(dy)))
    return saida


def _mexer(img, angulo, escala, dy):
    """Gira (graus), aumenta e sobe/desce o retrato em volta do pescoço. Embaixo nunca abre buraco."""
    import retratos
    n = img.shape[0]
    px, py = n * 0.5, n * 0.78                         # pivô: pescoço
    a = np.deg2rad(angulo)
    c, s = np.cos(a), np.sin(a)
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    x = (xx - px) / escala
    y = (yy - py - dy * n / 400.0) / escala
    xs = c * x + s * y + px
    ys = -s * x + c * y + py
    ys = np.minimum(ys, n - 1)                         # o corpo continua até a borda de baixo
    return np.clip(retratos._amostrar(img, ys, xs) + 0.5, 0, 255).astype(np.uint8)


def quadros_retrato(personagem, fotos, voz, taxa, fundo=True, fps=FPS, boca_fechada=False):
    """Quadros do retrato 3D: boca suave + respiração + balanço + tranco (e moldura atrás, se `fundo`).
    `boca_fechada`: mexe a cabeça pela voz mas deixa a boca parada — entrada da IA de boca (boca_ia.py)."""
    fotos = [f.astype(np.float32) for f in fotos]
    base = moldura(personagem, fotos[0].shape[0]) if fundo else None
    bocas = bocas_pela_voz(voz, taxa, fps)
    if boca_fechada:
        bocas = np.zeros_like(bocas)
    for boca, angulo, escala, dy in movimento(bocas, nivel_da_voz(voz, taxa, fps)[:len(bocas)], fps):
        i = min(int(boca), 1)
        f = boca - i
        img = fotos[i] * (1 - f) + fotos[i + 1] * f    # mistura das 2 bocas vizinhas
        img = _mexer(img, angulo, escala, dy)
        yield emoldurar(personagem, img, base) if fundo else img


def quadros(personagem, voz, taxa, fundo=True, fps=FPS):
    """Gera os quadros (RGBA uint8) um por um, com a cabeça balançando de leve quando fala."""
    import retratos
    fotos = retratos.retratos(personagem, LADO)
    if fotos is not None:
        yield from quadros_retrato(personagem, fotos, voz, taxa, fundo, fps)
        return
    imgs = sprites(personagem, fundo=fundo)
    bocas = bocas_pela_voz(voz, taxa, fps)
    olhos = piscadas(len(bocas), fps)
    for b, p in zip(bocas, olhos):
        img = imgs[(int(b), bool(p))]
        if b and not fundo:
            img = np.roll(img, -2 * int(b), axis=0)       # leve "subida" ao falar (só sem moldura)
        yield img
