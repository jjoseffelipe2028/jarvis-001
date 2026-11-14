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
PERSONAGENS = ("brabo", "mestre")


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


def desenhar_mestre(boca=0, piscando=False, fundo=True, braco=True):
    t = Tela()
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


def desenhar_tomada(lado=600):
    """Tomada padrão brasileiro (3 pinos) com os 3 fios: fase, neutro e terra no MEIO. Para o quadro de ensino."""
    t = Tela(lado=lado)
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
    return t.pronta()


def sprites(personagem, fundo=True):
    """{(boca, piscando): imagem} — os 6 desenhos de um personagem, feitos uma vez só."""
    if personagem not in PERSONAGENS:
        raise ValueError("personagem desconhecido")
    desenhar = desenhar_brabo if personagem == "brabo" else desenhar_mestre
    return {(b, p): desenhar(b, p, fundo=fundo) for b in (0, 1, 2) for p in (False, True)}


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


def quadros(personagem, voz, taxa, fundo=True, fps=FPS):
    """Gera os quadros (RGBA uint8) um por um, com a cabeça balançando de leve quando fala."""
    imgs = sprites(personagem, fundo=fundo)
    bocas = bocas_pela_voz(voz, taxa, fps)
    olhos = piscadas(len(bocas), fps)
    for b, p in zip(bocas, olhos):
        img = imgs[(int(b), bool(p))]
        if b and not fundo:
            img = np.roll(img, -2 * int(b), axis=0)       # leve "subida" ao falar (só sem moldura)
        yield img
