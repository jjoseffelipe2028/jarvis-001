"""
Jarvis 001 — Caçador de momentos 🔎 (Ticket 025, V5)

Lê o VOLUME de um vídeo longo (ex.: live de 24h) e acha onde o som explode:
grito, risada, tiroteio. Sugere os trechos; quem escolhe é o José.

Só LÊ o vídeo (nunca altera). Tudo local, com o PyAV que já veio com o Whisper.
Não precisa do FFmpeg instalado para caçar.
"""

import math
import re
from dataclasses import dataclass, replace

import av
import numpy as np

TAXA = 8000          # amostras por segundo (volume não precisa de mais)
PISO_DB = -90.0      # silêncio total vira -90 dB (em vez de -infinito)
BLOCO_BASE = 300     # o "normal" de cada trecho = mediana de 5 min
LIMIAR_DB = 6.0      # pico precisa estar 6 dB acima do normal (som ~2x mais forte)
DISTANCIA_MIN = 45   # dois momentos a menos de 45 s viram um só
ANTES, DEPOIS = 10, 15  # corte sugerido: 10 s antes do pico e 15 s depois (25 s)
# Ticket 089: versão TikTok. O Creator Rewards só paga vídeo com MAIS de 1 min,
# então a janela é de 70 s (45 antes do pico, 25 depois) e nunca sai com menos de 62 s.
TIKTOK_ANTES, TIKTOK_DEPOIS = 45, 25
TIKTOK_MINIMO = 62
# Ticket 092: o volume acha os candidatos; a FALA desempata. O Whisper só ouve os candidatos
# (CANDIDATOS x mais do que vai entregar), nunca a live inteira.
PALAVRAS_FORTES = re.compile(
    r"n[aã]o acredito|olha isso|clipa|que isso|caraca|meu deus|mano do c[eé]u|socorro|"
    r"\bk{3,}\b|\b(?:ha){2,}h?\b|\b(?:rs){2,}\b|\brisos?\b", re.I)
PESO_PALAVRA = 3.0   # cada palavra forte vale como +3 dB de volume
MAX_PALAVRAS = 3     # no máximo +9 dB: a fala ajuda, mas não inventa momento do nada
CANDIDATOS = 3


class ErroCacador(Exception):
    """Mensagem curta para o José."""


@dataclass(frozen=True)
class Momento:
    numero: int     # 1 = o mais forte
    inicio: int     # segundo em que o corte começa
    fim: int        # segundo em que o corte termina
    pico: int       # segundo do pico
    forca: float    # quantos dB acima do normal
    bonus: float = 0.0       # Ticket 092: pontos das palavras fortes na fala
    palavras: tuple = ()     # as palavras fortes achadas (para o José ver o porquê)


def nota_fala(texto):
    """(bônus, palavras achadas) de uma fala. Cada palavra forte diferente vale PESO_PALAVRA."""
    achadas = []
    for m in PALAVRAS_FORTES.finditer(str(texto or "")):
        palavra = m.group(0).lower()
        if palavra not in achadas:
            achadas.append(palavra)
    achadas = achadas[:MAX_PALAVRAS]
    return len(achadas) * PESO_PALAVRA, tuple(achadas)


def reordenar(momentos, ouvir, maximo=20, avisar=None):
    """Ouve cada candidato (`ouvir(momento)` → texto), soma o bônus da fala e reordena.
    Candidato que falha ao ouvir fica só com o volume (não derruba a caçada)."""
    pontuados = []
    for m in momentos:
        try:
            bonus, palavras = nota_fala(ouvir(m))
        except Exception as e:  # FFmpeg/Whisper falhou nesse trecho: segue sem bônus
            if avisar:
                avisar(f"   ⚠️ não consegui ouvir {hms(m.inicio)}: {str(e)[:80]}")
            bonus, palavras = 0.0, ()
        pontuados.append(replace(m, bonus=bonus, palavras=palavras))
    pontuados.sort(key=lambda m: m.forca + m.bonus, reverse=True)  # estável: empate mantém a ordem
    return [replace(m, numero=n) for n, m in enumerate(pontuados[:maximo], start=1)]


def energia_por_segundo(caminho, progresso=None, bloco=TAXA, limite=None):
    """Volume (dB) de cada segundo do áudio. Lê aos pedaços: serve para 24h sem estourar a memória.
    Ticket 093: `bloco` em amostras (TAXA = 1 s; 400 = 50 ms) e `limite` = para depois de N blocos."""
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
                pedaco = amostras[pos:pos + (bloco - contagem)]
                soma += float(np.dot(pedaco, pedaco))
                contagem += len(pedaco)
                pos += len(pedaco)
                if contagem == bloco:
                    energias.append(_db(soma, contagem))
                    soma, contagem = 0.0, 0
                    if progresso and len(energias) % 600 == 0:
                        progresso(len(energias))

        try:
            for quadro in conteiner.decode(audio):
                for q in reamostrar.resample(quadro):
                    somar(q.to_ndarray().astype(np.float64).ravel())
                if limite and len(energias) >= limite:
                    return energias[:limite]
            for q in reamostrar.resample(None):
                somar(q.to_ndarray().astype(np.float64).ravel())
        except av.FFmpegError as e:
            raise ErroCacador("O áudio do vídeo está corrompido.") from e
        if contagem >= bloco // 2:  # sobra de meio bloco ou mais conta como 1 bloco
            energias.append(_db(soma, contagem))
    return energias[:limite] if limite else energias


# Ticket 093: tiro tem ataque SECO (o som pula de uma vez em 50 ms); grito e música sobem devagar.
BLOCO_ESTOURO = TAXA // 20   # 50 ms
SALTO_DB = 10.0              # pulo em relação aos 50 ms anteriores
ACIMA_DB = 12.0              # e bem acima do normal do trecho
INICIO_SEGURO = 7            # o YouTube tira o anúncio se a violência aparece nos 7 s iniciais


def estouros(caminho, ate=INICIO_SEGURO):
    """Segundos (arredondados para baixo) com estouro seco nos `ate` s iniciais. Só LÊ o arquivo."""
    e = energia_por_segundo(caminho, bloco=BLOCO_ESTOURO, limite=ate * 20)
    if len(e) < 2:
        return []
    normal = float(np.median(e))
    achados = []
    for i in range(1, len(e)):
        if e[i] - e[i - 1] >= SALTO_DB and e[i] - normal >= ACIMA_DB:
            segundo = i // 20
            if segundo not in achados:
                achados.append(segundo)
    return achados


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


def janela_tiktok(pico, duracao_video, antes=TIKTOK_ANTES, depois=TIKTOK_DEPOIS, minimo=TIKTOK_MINIMO):
    """(inicio, fim) da versão longa do momento, para o TikTok pagar. None se o vídeo não dá 62 s.
    Se o pico está perto do começo ou do fim do vídeo, a janela escorrega para o outro lado
    (em vez de encolher e cair abaixo de 1 min)."""
    tamanho = antes + depois
    inicio = max(0, int(pico) - antes)
    fim = min(int(duracao_video), inicio + tamanho)
    inicio = max(0, fim - tamanho)  # bateu no fim do vídeo: puxa o começo para trás
    return (inicio, fim) if fim - inicio >= minimo else None


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
        fala = f" · 🗣️ {_desarmar(', '.join(m.palavras))}" if m.palavras else ""  # Ticket 092
        linhas.append(f"| {m.numero} | {hms(m.inicio)} → {hms(m.fim)} | {hms(m.pico)} | +{m.forca} dB{fala} |")
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
