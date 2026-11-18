"""
Jarvis 001 — Anti-molde 🧩 (Ticket 095)

O YouTube corta o anúncio do CANAL INTEIRO se ele parecer feito "em massa, por molde"
(conteúdo inautêntico). Este vigia guarda o histórico dos roteiros e BARRA, antes de gravar:
  1. abertura repetida: as primeiras palavras (tirando o bordão) iguais às de um roteiro recente;
  2. roteiro parecido demais com um recente (trechos de 3 palavras em comum).

O bordão ("Chegou o Brabo, e hoje ninguém escapa!") pode repetir: ele é a marca, não o molde.
Tudo local: o histórico é um arquivo de texto na pasta dos cortes. Nada vai para a internet.
"""

import json
import re
import unicodedata
from datetime import datetime
from pathlib import Path

BORDOES = ("chegou o brabo e hoje ninguem escapa", "chegou o brabo")  # o mais longo primeiro
RECENTES = 30            # compara com os últimos 30 roteiros do mesmo canal
PALAVRAS_ABERTURA = 6
LIMITE_PARECIDO = 0.35   # 35% dos trechos de 3 palavras em comum = parecido demais
NOME_HISTORICO = ".historico-roteiros.jsonl"


class ErroMolde(Exception):
    """Mensagem curta para o José."""


def normalizar(texto):
    """Minúsculas, sem acento, sem pontuação e sem o bordão."""
    t = unicodedata.normalize("NFKD", str(texto or "")).encode("ascii", "ignore").decode().lower()
    t = " ".join(re.sub(r"[^a-z0-9]+", " ", t).split())
    for b in BORDOES:
        t = t.replace(b, " ")
    return " ".join(t.split())


def texto_do_roteiro(roteiro):
    """Junta as falas do roteiro (dict validado do brabo.py) num texto só."""
    return " ".join(f["texto"] for f in roteiro.get("falas", []))


def abertura(texto):
    return " ".join(normalizar(texto).split()[:PALAVRAS_ABERTURA])


def _trechos(texto):
    p = normalizar(texto).split()
    return {" ".join(p[i:i + 3]) for i in range(len(p) - 2)}


def parecido(a, b):
    """0 a 1: quanto dos trechos de 3 palavras os dois têm em comum (Jaccard)."""
    ta, tb = _trechos(a), _trechos(b)
    if not ta or not tb:
        return 0.0
    return len(ta & tb) / len(ta | tb)


def ler_historico(caminho):
    """Lista de registros (o mais novo por último). Linha estragada é pulada."""
    registros = []
    try:
        linhas = Path(caminho).read_text(encoding="utf-8").splitlines()
    except OSError:
        return []
    for linha in linhas:
        try:
            r = json.loads(linha)
        except ValueError:
            continue
        if isinstance(r, dict) and isinstance(r.get("texto"), str):
            registros.append(r)
    return registros


def conferir(texto, modo, historico, arquivo=""):
    """Barra (ErroMolde) se o roteiro repete a abertura ou é parecido demais com um recente
    do mesmo canal. O mesmo `arquivo` (refazer depois de uma falha) não conta."""
    recentes = [r for r in historico if r.get("modo") == modo and r.get("arquivo") != arquivo][-RECENTES:]
    minha = abertura(texto)
    for r in reversed(recentes):
        if minha and minha == abertura(r["texto"]):
            raise ErroMolde(f"A abertura \"{minha}\" já foi usada em {r.get('arquivo', '?')}. "
                            "Comece de outro jeito (o bordão pode ficar).")
        nota = parecido(texto, r["texto"])
        if nota >= LIMITE_PARECIDO:
            raise ErroMolde(f"Roteiro {round(nota * 100)}% parecido com {r.get('arquivo', '?')}. "
                            "Mude o ângulo da piada ou o quadro.")


def registrar(caminho, texto, modo, arquivo, agora=None):
    """Acrescenta o roteiro no histórico (só depois do vídeo pronto)."""
    agora = agora or datetime.now()
    linha = json.dumps({"data": agora.isoformat(timespec="seconds"), "modo": modo,
                        "arquivo": str(arquivo), "texto": texto}, ensure_ascii=False)
    with open(caminho, "a", encoding="utf-8", newline="\n") as f:
        f.write(linha + "\n")
