"""
Jarvis 001 — Caderno do Brabo 📓 (Ticket 096)

A Gemma repetia frases porque só recebia uma instrução genérica. Agora ela recebe um TEMPERO
sorteado do caderno (caderno_brabo.json): 1 quadro, 1 tipo de piada, algumas gírias,
1 comparação e 1 veredito. Cada rascunho sai com um sabor diferente; o anti-molde (Ticket 095)
barra o que mesmo assim sair repetido.

O caderno é DADO conferido (pode ser editado pelo José): só listas de texto curto,
sem link, sem @, sem palavrão. Item ruim é recusado com o motivo.
"""

import json
import random
import re
from pathlib import Path

CADERNO = Path(__file__).parent / "caderno_brabo.json"
CATEGORIAS = ("girias", "comparacoes", "vereditos", "tipos_de_piada", "quadros")
QUANTOS = {"girias": 3, "comparacoes": 1, "vereditos": 1, "tipos_de_piada": 1, "quadros": 1}
MAX_LETRAS = 80
MAX_ITENS = 200

_LINK = re.compile(r"https?://|www\.|\b[\w-]+\.(?:com|net|org|br|ly|io|me|gg|tv|xyz|top)\b", re.I)
_PALAVRAO = re.compile(r"\b(?:porra|caralho|merda|puta|fdp|buceta|cacete|foda)\w*", re.I)


class ErroCaderno(Exception):
    """Mensagem curta para o José."""


def validar(dado):
    """dict do JSON → {categoria: [textos]}. Qualquer coisa fora do combinado → ErroCaderno."""
    if not isinstance(dado, dict):
        raise ErroCaderno("o caderno precisa ser um objeto JSON")
    limpo = {}
    for cat in CATEGORIAS:
        itens = dado.get(cat)
        if not isinstance(itens, list) or not 1 <= len(itens) <= MAX_ITENS:
            raise ErroCaderno(f"'{cat}' precisa ser uma lista de 1 a {MAX_ITENS} textos")
        lista = []
        for n, item in enumerate(itens, start=1):
            texto = " ".join(str(item).split()) if isinstance(item, str) else ""
            if not texto or len(texto) > MAX_LETRAS:
                raise ErroCaderno(f"{cat} nº {n}: texto vazio ou com mais de {MAX_LETRAS} letras")
            if _LINK.search(texto) or "@" in texto or _PALAVRAO.search(texto):
                raise ErroCaderno(f"{cat} nº {n}: sem link, sem @ e sem palavrão")
            if any(c in texto for c in "{}<>`\\") or not texto.isprintable():
                raise ErroCaderno(f"{cat} nº {n}: tem símbolo estranho")
            if texto not in lista:
                lista.append(texto)
        limpo[cat] = lista
    return limpo


def carregar(caminho=CADERNO):
    try:
        return validar(json.loads(Path(caminho).read_text(encoding="utf-8")))
    except OSError as e:
        raise ErroCaderno("Não achei o caderno do Brabo (caderno_brabo.json).") from e
    except ValueError as e:
        raise ErroCaderno("O caderno do Brabo não é um JSON válido.") from e


def sortear(caderno, rng=None):
    """Sorteia o tempero de um rascunho: {categoria: [itens]}."""
    rng = rng or random.SystemRandom()
    return {cat: rng.sample(caderno[cat], min(QUANTOS[cat], len(caderno[cat]))) for cat in CATEGORIAS}


def tempero(sorteio):
    """Texto que vai junto das instruções da Gemma. Só sugestão: ela usa 1 ou 2, sem copiar tudo."""
    return (
        " TEMPERO DESTE ROTEIRO (use como inspiração, não copie tudo, julgue a jogada e nunca a pessoa): "
        f"estrutura = {sorteio['quadros'][0]}; "
        f"estilo = {sorteio['tipos_de_piada'][0]}; "
        f"gírias que combinam = {', '.join(sorteio['girias'])}; "
        f"uma comparação possível = {sorteio['comparacoes'][0]}; "
        f"um veredito possível = {sorteio['vereditos'][0]}. "
        "Não comece pelas mesmas palavras de sempre."
    )
