"""
Jarvis 001 — Caçador de momentos 🔎 (Ticket 025, V5)

Lê o VOLUME de um vídeo longo (ex.: live de 24h) e acha onde o som explode:
grito, risada, tiroteio. Sugere os trechos; quem escolhe é o José.

Só LÊ o vídeo (nunca altera). Tudo local, com o PyAV que já veio com o Whisper.
Não precisa do FFmpeg instalado para caçar.
"""

import math
from dataclasses import dataclass

import av
import numpy as np

TAXA = 8000          # amostras por segundo (volume não precisa de mais)
PISO_DB = -90.0      # silêncio total vira -90 dB (em vez de -infinito)
BLOCO_BASE = 300     # o "normal" de cada trecho = mediana de 5 min
LIMIAR_DB = 6.0      # pico precisa estar 6 dB acima do normal (som ~2x mais forte)
DISTANCIA_MIN = 45   # dois momentos a menos de 45 s viram um só
ANTES, DEPOIS = 10, 15  # corte sugerido: 10 s antes do pico e 15 s depois (25 s)


class ErroCacador(Exception):
    """Mensagem curta para o José."""


@dataclass(frozen=True)
class Momento:
    numero: int     # 1 = o mais forte
    inicio: int     # segundo em que o corte começa
    fim: int        # segundo em que o corte termina
    pico: int       # segundo do pico
    forca: float    # quantos dB acima do normal


def energia_por_segundo(caminho, progresso=None):
    """Volume (dB) de cada segundo do áudio. Lê aos pedaços: serve para 24h sem estourar a memória."""
    try:
        conteiner = av.open(str(caminho))
    except (av.FFmpegError, OSError) as e:
        raise ErroCacador("Não consegui abrir o vídeo.") from e
    with conteiner:
        audio = next((s for s in conteiner.streams if s.type == "audio"), None)
        if audio is None:
            raise ErroCacador("Esse vídeo não tem áudio.")
        reamostrar = av.AudioResampler(format="flt", layout="mono", rate=TAXA)
        energias, soma, contagem = [], 0.0, 0

        def somar(amostras):
            nonlocal soma, contagem
            pos = 0
            while pos < len(amostras):
                pedaco = amostras[pos:pos + (TAXA - contagem)]
                soma += float(np.dot(pedaco, pedaco))
                contagem += len(pedaco)
                pos += len(pedaco)
                if contagem == TAXA:
                    energias.append(_db(soma, contagem))
                    soma, contagem = 0.0, 0
                    if progresso and len(energias) % 600 == 0:
                        progresso(len(energias))

        try:
            for quadro in conteiner.decode(audio):
                for q in reamostrar.resample(quadro):
                    somar(q.to_ndarray().astype(np.float64).ravel())
            for q in reamostrar.resample(None):
                somar(q.to_ndarray().astype(np.float64).ravel())
        except av.FFmpegError as e:
            raise ErroCacador("O áudio do vídeo está corrompido.") from e
        if contagem >= TAXA // 2:  # sobra de meio segundo ou mais conta como 1 segundo
            energias.append(_db(soma, contagem))
    return energias


def _db(soma, contagem):
    rms = math.sqrt(soma / contagem) if contagem else 0.0
    return max(PISO_DB, 20 * math.log10(rms)) if rms > 0 else PISO_DB


def achar_picos(energias, maximo=20, limiar=LIMIAR_DB, distancia=DISTANCIA_MIN,
                antes=ANTES, depois=DEPOIS):
    """Os `maximo` momentos mais fortes, do mais forte para o mais fraco."""
    e = np.asarray(energias, dtype=np.float64)
    duracao = len(e)
    if duracao == 0:
        return []
    # "Normal" de cada segundo: mediana do bloco de 5 min em que ele está.
    base = np.empty(duracao)
    for i in range(0, duracao, BLOCO_BASE):
        base[i:i + BLOCO_BASE] = np.median(e[i:i + BLOCO_BASE])
    # Média de 2 s: um estalo de 1 quadro não vale como momento.
    # (Na ponta, repete o 1º segundo: completar com zero inventava um "pico" no começo do vídeo.)
    anterior = np.concatenate(([e[0]], e[:-1]))
    suave = (e + anterior) / 2
    forca = suave - base

    escolhidos = []
    for i in np.argsort(-forca, kind="stable"):
        if forca[i] < limiar or len(escolhidos) >= maximo:
            break
        if all(abs(int(i) - p) >= distancia for p, _ in escolhidos):
            escolhidos.append((int(i), float(forca[i])))

    momentos = []
    for numero, (pico, f) in enumerate(escolhidos, start=1):
        inicio = max(0, pico - antes)
        fim = min(duracao, pico + depois)
        momentos.append(Momento(numero, inicio, fim, pico, round(f, 1)))
    return momentos


def hms(segundos):
    """3725 → '1:02:05'; 65 → '1:05'."""
    segundos = int(segundos)
    h, resto = divmod(segundos, 3600)
    m, s = divmod(resto, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"


def ler_hms(texto):
    """'1:02:05' → 3725; '45' → 45. Formato estranho → ValueError."""
    partes = str(texto).strip().split(":")
    if not 1 <= len(partes) <= 3 or not all(p.isdigit() and len(p) <= 6 for p in partes):
        raise ValueError("tempo inválido")
    total = 0
    for p in partes:
        total = total * 60 + int(p)
    return total


def nota_markdown(nome_video, momentos, duracao):
    """Nota do cofre com a lista de momentos. O nome do vídeo é "desarmado" antes de entrar."""
    nome = _desarmar(nome_video)
    linhas = [
        f"# 🔎 Momentos: {nome}",
        "",
        f"Vídeo de {hms(duracao)} · {len(momentos)} momento(s) acima de {LIMIAR_DB:.0f} dB do normal.",
        "_Feito pelo caçador do Jarvis (Ticket 025). Quem escolhe é o José._",
        "",
        "| Nº | Corte | Pico | Força |",
        "|---|---|---|---|",
    ]
    for m in momentos:
        linhas.append(f"| {m.numero} | {hms(m.inicio)} → {hms(m.fim)} | {hms(m.pico)} | +{m.forca} dB |")
    if not momentos:
        linhas.append("| — | nenhum pico forte | — | — |")
    linhas += [
        "",
        "## Como cortar",
        "```",
        f"python cortes.py cortar \"{nome}\" 1 2 3 --arroba @amigo",
        "```",
        "",
        "⚠️ Antes de postar: confira se tem **música da rádio do GTA** no trecho (tem dono, pode bloquear o vídeo).",
        "",
    ]
    return "\n".join(linhas)


def _desarmar(texto):
    """Tira controle e símbolos de Markdown/link de um texto que vai para a nota."""
    limpo = "".join(c if c.isprintable() else " " for c in str(texto))
    for c in "|[]`*<>#\\\"":
        limpo = limpo.replace(c, " ")
    return " ".join(limpo.split())[:120]
