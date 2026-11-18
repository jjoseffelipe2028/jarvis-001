"""
Jarvis 001 — Retratos 3D dos personagens (Ticket 055, Parte 2)

O José gera no Gemini 3 imagens de cada personagem, com fundo verde liso:
    C:\\Users\\jjose\\Videos\\personagens\\<personagem>\\fechada.jpeg | meia.jpeg | aberta.jpeg
(.png também vale). Este módulo:
  1. lê as imagens com o FFmpeg (sem PIL), já em tamanho pequeno;
  2. tira o fundo verde (vira transparente) e limpa o verde que vaza na borda;
  3. alinha as 3 bocas pela largura e pelo topo da cabeça: o Gemini às vezes muda o zoom ou mexe a cabeça
     um pouco, e sem isso a cabeça "pula" quando o personagem fala.

Se faltar alguma das 3 imagens, devolve None e o avatar.py usa o desenho antigo, feito por código.
Tudo local: nada é baixado nem enviado.
"""
import os
import subprocess
from functools import lru_cache
from pathlib import Path

import numpy as np

BOCAS = ("fechada", "meia", "aberta")
EXTENSOES = (".png", ".jpeg", ".jpg")
TRABALHO = 800          # tamanho em que as imagens são lidas e alinhadas (depois reduz para o tamanho pedido)


def pasta_padrao():
    return Path(os.environ.get("JARVIS_RETRATOS", Path.home() / "Videos" / "personagens"))


def arquivos(personagem, pasta=None):
    """[fechada, meia, aberta] com o caminho de cada uma, ou None se faltar alguma."""
    base = Path(pasta or pasta_padrao()) / personagem
    achados = []
    for boca in BOCAS:
        caminho = next((base / (boca + ext) for ext in EXTENSOES if (base / (boca + ext)).is_file()), None)
        if caminho is None:
            return None
        achados.append(caminho)
    return achados


def ler(caminho, lado=TRABALHO, ffmpeg=None):
    """Imagem → array RGB uint8 lado x lado (corta ao centro se não for quadrada)."""
    if ffmpeg is None:
        import cortador
        ffmpeg = cortador.achar_ffmpeg()
    filtro = f"crop='min(iw,ih)':'min(iw,ih)',scale={lado}:{lado}:flags=lanczos"
    r = subprocess.run([ffmpeg, "-nostdin", "-v", "error", "-i", f"file:{caminho}", "-vf", filtro,
                        "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"],
                       capture_output=True, timeout=60, check=False)
    if r.returncode != 0 or len(r.stdout) != lado * lado * 3:
        raise ValueError(f"não consegui ler a imagem {Path(caminho).name}")
    return np.frombuffer(r.stdout, dtype=np.uint8).reshape(lado, lado, 3)


def tirar_verde(rgb):
    """RGB → RGBA: o verde do fundo vira transparente, com borda suave e sem 'aura' verde."""
    c = rgb.astype(np.float32)
    r, g, b = c[..., 0], c[..., 1], c[..., 2]
    domina = g - np.maximum(r, b)                      # quanto o verde passa das outras cores
    alfa = np.clip((70.0 - domina) / 40.0, 0.0, 1.0)   # >70 = fundo; <30 = personagem; no meio = borda
    c[..., 1] = np.minimum(g, np.maximum(r, b) + 8)    # tira o verde que vazou no cabelo/borda
    saida = np.empty(rgb.shape[:2] + (4,), dtype=np.uint8)
    saida[..., :3] = np.clip(c, 0, 255).astype(np.uint8)
    saida[..., 3] = (alfa * 255).astype(np.uint8)
    return saida


def medir(rgba):
    """(topo, centro_x, largura) da cabeça: largura medida na faixa de cima (boné/capacete), que não muda com a boca."""
    a = rgba[..., 3] > 128
    linhas = np.where(a.sum(axis=1) > a.shape[1] * 0.02)[0]
    if linhas.size == 0:
        raise ValueError("a imagem ficou vazia depois de tirar o verde")
    topo = int(linhas[0])
    faixa = a[topo:topo + int(a.shape[0] * 0.25)]
    colunas = np.where(faixa.any(axis=0))[0]
    return topo, (colunas[0] + colunas[-1]) / 2.0, float(colunas[-1] - colunas[0] + 1)


def _amostrar(img, ys, xs):
    """Bilinear: pega a imagem nos pontos (ys, xs) fracionários; fora da imagem = transparente."""
    h, w = img.shape[:2]
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = (ys - y0)[..., None]
    fx = (xs - x0)[..., None]
    pad = np.zeros((h + 2, w + 2, img.shape[2]), dtype=np.float32)
    pad[1:-1, 1:-1] = img
    y0c = np.clip(y0 + 1, 0, h + 1)
    x0c = np.clip(x0 + 1, 0, w + 1)
    y1c = np.clip(y0 + 2, 0, h + 1)
    x1c = np.clip(x0 + 2, 0, w + 1)
    cima = pad[y0c, x0c] * (1 - fx) + pad[y0c, x1c] * fx
    baixo = pad[y1c, x0c] * (1 - fx) + pad[y1c, x1c] * fx
    return cima * (1 - fy) + baixo * fy


def encaixar(rgba, medida, alvo):
    """Escala e move a imagem para a cabeça ficar com o mesmo topo, centro e largura do `alvo`."""
    topo, cx, larg = medida
    topo_a, cx_a, larg_a = alvo
    s = larg / larg_a                                  # pixels da origem por pixel do destino
    n = rgba.shape[0]
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    ys = topo + (yy - topo_a) * s
    xs = cx + (xx - cx_a) * s
    return np.clip(_amostrar(rgba.astype(np.float32), ys, xs), 0, 255).astype(np.uint8)


def reduzir(rgba, lado):
    """Reduz por média de blocos (área), com cor pré-multiplicada para a borda não escurecer."""
    n = rgba.shape[0]
    if lado == n:
        return rgba
    c = rgba.astype(np.float32)
    c[..., :3] *= c[..., 3:4] / 255.0
    ys = (np.arange(lado) + 0.5) * n / lado - 0.5
    yy, xx = np.meshgrid(ys, ys, indexing="ij")
    if lado < n:   # pré-borra para não serrilhar
        k = max(1, n // lado)
        cum = np.cumsum(np.cumsum(np.pad(c, ((1, 0), (1, 0), (0, 0))), axis=0), axis=1)
        m = n - k + 1
        c = (cum[k:k + m, k:k + m] - cum[:m, k:k + m] - cum[k:k + m, :m] + cum[:m, :m]) / (k * k)
        yy = np.clip(yy - (k - 1) / 2.0, 0, m - 1)
        xx = np.clip(xx - (k - 1) / 2.0, 0, m - 1)
    out = _amostrar(c, yy, xx)
    a = out[..., 3:4]
    out[..., :3] = np.where(a > 0, out[..., :3] * 255.0 / np.maximum(a, 1e-3), 0)
    return np.clip(out, 0, 255).astype(np.uint8)


@lru_cache(maxsize=8)
def _carregar(caminhos, lado):
    imgs = [tirar_verde(ler(c)) for c in caminhos]
    medidas = [medir(i) for i in imgs]
    alvo = medidas[0]                                   # a boca fechada manda: as outras se encaixam nela
    alinhadas = [imgs[0]] + [encaixar(i, m, alvo) for i, m in zip(imgs[1:], medidas[1:])]
    return tuple(reduzir(i, lado) for i in alinhadas)


def retratos(personagem, lado, pasta=None):
    """(fechada, meia, aberta) em RGBA lado x lado, ou None se o personagem não tem as 3 imagens."""
    achados = arquivos(personagem, pasta)
    if achados is None:
        return None
    return _carregar(tuple(str(c) for c in achados), int(lado))
