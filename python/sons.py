"""Sons do Brabo feitos por código (Ticket 055 — Kit do Editor). Nada baixado, sem direito autoral de ninguém.

- punch: soco grave + estalo (toca junto com o pop-up)
- riser: "subida" antes do primeiro impacto
- ding: sininho da nota final
- batida: bumbo, chimbal e baixo a 100 BPM, que PARA um instante no impacto (quebra de ritmo)

`trilha_de_sons` junta tudo numa faixa só (float32 mono). O FFmpeg abaixa essa faixa quando o Brabo fala.
"""
import numpy as np

TAXA = 44100
BPM = 100
QUEBRA = 0.6          # segundos de silêncio da batida depois do impacto


def _t(seg, taxa):
    return np.arange(int(seg * taxa)) / taxa


def punch(taxa=TAXA, semente=7):
    t = _t(0.35, taxa)
    f = 110 * np.exp(-t * 9) + 38
    corpo = np.sin(2 * np.pi * np.cumsum(f) / taxa) * np.exp(-t * 7)
    estalo = np.random.default_rng(semente).normal(0, 1, len(t)) * np.exp(-t * 60) * 0.5
    return (corpo + estalo).astype(np.float32)


def riser(seg=0.9, taxa=TAXA, semente=8):
    t = _t(seg, taxa)
    f = 180 + 1100 * (t / seg) ** 2
    s = np.sin(2 * np.pi * np.cumsum(f) / taxa) * 0.4 + np.random.default_rng(semente).normal(0, 0.25, len(t))
    return (s * (t / seg) ** 2).astype(np.float32)


def ding(taxa=TAXA):
    t = _t(0.9, taxa)
    return ((np.sin(2 * np.pi * 1320 * t) + 0.6 * np.sin(2 * np.pi * 1980 * t)) * np.exp(-t * 5) * 0.5).astype(np.float32)


def _por(destino, som, em, taxa, ganho=1.0):
    i = int(round(em * taxa))
    if i < 0:
        som, i = som[-i:], 0
    j = min(len(destino), i + len(som))
    if j > i:
        destino[i:j] += ganho * som[: j - i]


def batida(duracao, taxa=TAXA, inicio=0.0, semente=9):
    """Batida simples a 100 BPM, do `inicio` até perto do fim."""
    saida = np.zeros(int(duracao * taxa) + 1, np.float32)
    passo = 60 / BPM
    kt = _t(0.3, taxa)
    bumbo = np.sin(2 * np.pi * np.cumsum(55 + 90 * np.exp(-kt * 30)) / taxa) * np.exp(-kt * 9)
    ht = _t(0.05, taxa)
    chimbal = np.diff(np.random.default_rng(semente).normal(0, 1, len(ht) + 1)) * np.exp(-ht * 80) * 0.35
    notas = (41.2, 41.2, 49.0, 36.7)
    bt = _t(passo, taxa)
    b = 0
    while inicio + b * passo < duracao - 0.3:
        tb = inicio + b * passo
        if b % 2 == 0:
            _por(saida, bumbo, tb, taxa, 0.9)
        _por(saida, chimbal, tb + passo / 2, taxa)
        _por(saida, np.sin(2 * np.pi * notas[(b // 4) % 4] * bt) * np.exp(-bt * 2.5) * 0.45, tb, taxa)
        b += 1
    return saida


def trilha_de_sons(duracao, impactos=(), nota_em=None, taxa=TAXA):
    """Efeitos + batida numa faixa só. `impactos` = segundos dos pop-ups; `nota_em` = quando a nota aparece."""
    fundo = batida(duracao, taxa)
    for em in impactos:                                   # quebra de ritmo: a batida some no impacto
        a, b = int(max(0, em - 0.05) * taxa), int((em + QUEBRA) * taxa)
        fundo[a:b] = 0
    pico = float(np.abs(fundo).max()) or 1.0
    fundo = fundo / pico * 0.35
    efeitos = np.zeros_like(fundo)
    for em in impactos:
        _por(efeitos, punch(taxa), em, taxa, 0.9)
    if impactos and impactos[0] >= 1.0:
        _por(efeitos, riser(0.85, taxa), impactos[0] - 0.85, taxa, 0.35)
    if nota_em is not None:
        _por(efeitos, ding(taxa), nota_em, taxa, 0.6)
    return np.clip(fundo + efeitos, -1, 1).astype(np.float32)


def expressoes_punch(impactos):
    """Expressões do FFmpeg para zoom, tremida e clarão perto de cada impacto (vazio se não há impacto)."""
    if not impactos:
        return None
    zoom = "+".join(f"0.16*exp(-pow((t-{p:.3f})*7,2))" for p in impactos)
    tx = "+".join(f"22*sin(t*95)*exp(-pow((t-{p:.3f})*6,2))" for p in impactos)
    ty = "+".join(f"16*cos(t*83)*exp(-pow((t-{p:.3f})*6,2))" for p in impactos)
    luz = "+".join(f"0.2*exp(-pow((t-{p:.3f})*14,2))" for p in impactos)
    return zoom, tx, ty, luz
