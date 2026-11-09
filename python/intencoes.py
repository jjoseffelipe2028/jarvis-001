"""
Jarvis 001 — Intenções 🎯

Decide se a frase do José é um pedido de AÇÃO (da lista) ou só conversa.
Regras escritas à mão, sem IA: o Gemma nunca escolhe ação sozinho. 🔒

Lista (allowlist):
- "anota no daily: estudei Git"      → registrar no daily
- "quais minhas pendências?"          → pendências
- "tenho algo sem commit?"            → git-status
- "que horas são?"                    → hora
- Pedido de ação fora da lista ("apaga", "instala"...) → "não posso fazer isso".
"""

import re
import unicodedata

# Resultado da detecção: (acao, argumento). acao in ACOES ou "proibida".
ACOES = ("daily", "pendencias", "git_status", "hora")


def _sem_acento(texto):
    """Tira acentos e põe em minúscula mantendo o MESMO tamanho (1 letra → 1 letra),
    para cortar o texto original no lugar certo (ex.: "İ".lower() viraria 2 letras)."""
    saida = []
    for c in texto:
        base = unicodedata.normalize("NFKD", c)[0]
        minuscula = base.lower()
        saida.append(minuscula[0] if minuscula else base)
    return "".join(saida)


_DAILY = re.compile(
    r"^\s*(?:jarvis[\s,]+)?(?:por favor[\s,]+)?"
    r"(?:anota|anote|registra|registre|escreve|escreva|coloca|coloque|bota|salva|salve)"
    r"(?:\s+(?:isso|ai|aqui))?\s+(?:no|na|em)\s+(?:meu\s+|minha\s+)?(?:daily|diario|nota do dia)"
    r"\s*(?:que|:|,|-)?\s*")

# Pedido de leitura só quando tem jeito de PERGUNTA sobre mim ("quais minhas...", "tenho...").
# "Me explica o que é um commit" é conversa, não ação.
_PEDIDO = r"^\s*(?:jarvis[\s,]+)?(?:e\s+)?(?:quais|tenho|tem|mostra|mostre|lista|liste|ve|veja|fiz|esqueci|como\s+(?:ta|esta))\b"
_PENDENCIAS = re.compile(_PEDIDO + r".*\b(?:pendencias?|tarefas?\s+(?:abertas?|pendentes?))\b"
                         r"|^\s*(?:jarvis[\s,]+)?o\s+que\s+(?:falta|tenho\s+pra\s+fazer)\b")
_GIT = re.compile(_PEDIDO + r".*\b(?:commit\w*|git\s*status)\b")
_HORA = re.compile(r"^\s*(?:jarvis[\s,]+)?(?:que\s+horas?\s+(?:sao|e)|que\s+dia\s+e\s+hoje)\b")

# Verbos de ação perigosa ou fora da lista: o Jarvis recusa em vez de "conversar" sobre fazer
_PROIBIDA = re.compile(
    r"^\s*(?:jarvis[\s,]+)?(?:por favor[\s,]+)?"
    r"(?:apaga|apague|deleta|delete|exclui|exclua|remove|remova|formata|formate|executa|execute|roda|rode"
    r"|instala|instale|desinstala|baixa|baixe|manda|mande|envia|envie|desliga|desligue|reinicia|reinicie"
    r"|abre|abra|fecha|feche|compra|compre|transfere|transfira|faz um pix|commita|commite|da push|faz push)\b")


def detectar(frase):
    """Devolve (acao, argumento) ou None se for só conversa."""
    if not frase:
        return None
    normal = _sem_acento(frase)

    m = _DAILY.match(normal)
    if m:
        texto = frase[m.end():].strip()  # corta no ORIGINAL: os acentos ficam
        return ("daily", texto)
    if _PROIBIDA.match(normal):
        return ("proibida", None)
    if _PENDENCIAS.search(normal):
        return ("pendencias", None)
    if _GIT.search(normal):
        return ("git_status", None)
    if _HORA.search(normal):
        return ("hora", None)
    return None


# ---------- respostas faladas (curtas) ----------

def _plural(n, um, varios):
    return f"{n} {um if n == 1 else varios}"


LIMITE_FALA = 400
_CONTROLE = re.compile(r"[\x00-\x1f\x7f-\x9f  ‪-‮⁦-⁩]")


def seguro(texto):
    """Tira caracteres de controle (ex.: ESC, que reescreve o terminal) e limita o tamanho."""
    texto = _CONTROLE.sub(" ", str(texto))
    texto = re.sub(r"\s+", " ", texto).strip()
    return texto if len(texto) <= LIMITE_FALA else texto[:LIMITE_FALA] + "..."


def responder(acao, dados):
    """Frase curta e SEGURA para falar. Resposta torta da API não derruba o Jarvis."""
    try:
        return seguro(_responder(acao, dados))
    except (TypeError, ValueError, AttributeError, KeyError):
        return "A API respondeu algo inesperado."


def _responder(acao, dados):
    if acao == "daily":
        return "Anotado no daily de hoje."
    if acao == "hora":
        return f"Agora são {dados.get('hora', '?')} de {dados.get('data', '?')}."
    if acao == "git_status":
        branch = dados.get("branch", "?")
        if dados.get("limpo"):
            return f"Tudo commitado na branch {branch}. Pode dormir tranquilo."
        partes = []
        if dados.get("alterados"):
            partes.append(_plural(int(dados["alterados"]), "arquivo alterado", "arquivos alterados"))
        if dados.get("novos"):
            partes.append(_plural(int(dados["novos"]), "arquivo novo", "arquivos novos"))
        return f"Na branch {branch} você tem {' e '.join(partes)} sem commit."
    if acao == "pendencias":
        total = int(dados.get("total", 0))
        if total == 0:
            return "Nenhuma pendência nos últimos 7 dias. Mandou bem."
        itens = dados.get("pendencias") or []
        primeiras = "; ".join(_limpar_para_falar(p.get("texto", "")) for p in itens[:3])
        return f"Você tem {_plural(total, 'pendência', 'pendências')}. As primeiras: {primeiras}."
    if acao == "proibida":
        return "Não posso fazer isso. Só executo as ações da minha lista."
    return "Não entendi o pedido."


def _limpar_para_falar(texto):
    """Tira marcação do Markdown (**, `, \\) para a voz não ler símbolos."""
    texto = re.sub(r"[*`_\\#>\[\]]", "", texto)
    return re.sub(r"\s+", " ", texto).strip()[:120]
