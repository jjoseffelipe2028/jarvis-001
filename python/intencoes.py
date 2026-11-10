"""
Jarvis 001 — Intenções 🎯

Decide se a frase do José é um pedido de AÇÃO (da lista) ou só conversa.
Regras escritas à mão, sem IA: o Gemma nunca escolhe ação sozinho. 🔒

Lista (allowlist):
- "anota no daily: estudei Git"      → registrar no daily
- "quais minhas pendências?"          → pendências
- "tenho algo sem commit?"            → git-status
- "que horas são?"                    → hora
- "bom dia, Jarvis" (frase inteira)   → resumo do dia (rotinas.py)
- "vigia as portas" / "tem porta aberta?" / "sentinela" → Sentinela (Ticket 021, só avisa)
- "confere a integridade" / "alguém mexeu nas travas?"    → vigia de integridade (Ticket 022)
- "isso é golpe: <mensagem>" / "verifica essa mensagem: ..." → detector de golpe (Ticket 023, nunca abre link)
- Pedido de ação fora da lista ("apaga", "instala"...) → "não posso fazer isso".
"""

import re
import unicodedata

# Resultado da detecção: (acao, argumento). acao in ACOES ou "proibida".
ACOES = ("daily", "pendencias", "git_status", "hora", "bom_dia", "portas", "integridade", "golpe")


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
# Ticket 009: a frase INTEIRA tem que ser o cumprimento ("bom dia jarvis"), senão é conversa
_BOM_DIA = re.compile(r"^\s*(?:jarvis[\s,]+)?(?:bom\s+dia|boa\s+tarde|boa\s+noite|resumo\s+do\s+dia|como\s+(?:ta|esta)\s+meu\s+dia)"
                      r"(?:[\s,]+jarvis)?[\s.!?]*$")

# Ticket 021: Sentinela (só lê as portas abertas). "o que é uma porta aberta?" continua conversa.
_PORTAS = re.compile(
    r"^\s*(?:jarvis[\s,]+)?(?:"
    r"(?:vigia|vigie|confere|confira|verifica|verifique|checa|cheque|olha|olhe|mostra|mostre|lista|liste)"
    r"\s+(?:as\s+|minhas\s+)?portas\b"
    r"|(?:tem|tenho|ha|quais|que)\s+(?:alguma\s+)?portas?(?:\s+(?:estao|esta|ta|tao))?\s+abertas?\b"
    r"|(?:a\s+)?sentinela(?:[\s,]+jarvis)?[\s.!?]*$)")

# Ticket 022: vigia de integridade (só lê a impressão digital das travas)
_INTEGRIDADE = re.compile(
    r"^\s*(?:jarvis[\s,]+)?(?:"
    r"(?:vigia|vigie|confere|confira|verifica|verifique|checa|cheque)\s+(?:a\s+)?integridade\b"
    r"|(?:alguem|algo|alguma\s+coisa)\s+mexeu\s+(?:nas?|nos?)\s+(?:minhas\s+)?(?:travas|regras)\b"
    r"|(?:as\s+)?(?:minhas\s+)?travas\s+(?:estao|tao|ta|continuam)\s+(?:iguais|intactas|ok|inteiras)\b)")

# Ticket 023: detector de golpe. O começo da frase é o pedido; o RESTO é a mensagem colada.
# Precisa de separador (":", "?", ",", "-") depois do pedido: "golpe do pix é comum?" continua conversa.
_GOLPE = re.compile(
    r"^\s*(?:jarvis[\s,]+)?(?:"
    r"(?:sera\s+que\s+)?(?:isso|essa\s+mensagem|esta\s+mensagem|esse\s+link)?\s*e\s+golpe"
    r"|(?:verifica|verifique|confere|confira|analisa|analise|checa|cheque|ve|veja)\s+(?:se\s+)?"
    r"(?:essa|esta|a|esse|este|o)\s+(?:mensagem|msg|sms|link|zap)(?:\s+e\s+golpe)?"
    r"|detector\s+de\s+golpe|golpe)"
    r"\s*[?:,\-]\s*")

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
    m = _GOLPE.match(normal)
    if m:
        # Antes da lista proibida: a mensagem colada pode ter "faz um pix", e isso NÃO é pedido do José
        return ("golpe", frase[m.end():].strip())
    if _PROIBIDA.match(normal):
        return ("proibida", None)
    if _PORTAS.match(normal):
        return ("portas", None)
    if _INTEGRIDADE.match(normal):
        return ("integridade", None)
    if _PENDENCIAS.search(normal):
        return ("pendencias", None)
    if _GIT.search(normal):
        return ("git_status", None)
    if _HORA.search(normal):
        return ("hora", None)
    if _BOM_DIA.match(normal):
        return ("bom_dia", None)
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
    except (TypeError, ValueError, AttributeError, KeyError, OverflowError):
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
    if acao == "portas":
        return _responder_portas(dados)
    if acao == "integridade":
        return _responder_integridade(dados)
    if acao == "golpe":
        return _responder_golpe(dados)
    if acao == "proibida":
        return "Não posso fazer isso. Só executo as ações da minha lista."
    return "Não entendi o pedido."


def _responder_portas(dados):
    """Ticket 021: resumo curto da Sentinela. Ela só AVISA; quem fecha porta é o José."""
    rede = int(dados.get("abertasParaARede", 0))
    portas = [p for p in (dados.get("portas") or []) if isinstance(p, dict)]
    if rede == 0:
        partes = ["Sentinela: nenhuma porta aberta para a rede. Tudo trancado."]
    else:
        partes = [f"Sentinela: {_plural(rede, 'porta aberta', 'portas abertas')} para a rede."]
        altas = [p for p in portas if p.get("risco") == "alto"][:2]
        atencao = [p for p in portas if p.get("risco") == "atencao"][:3]
        if altas:
            partes.append("Alerta: " + "; ".join(
                f"porta {int(p['porta'])}, {_limpar_para_falar(p.get('nota', ''))}" for p in altas) + ".")
        elif atencao:
            partes.append("Vale olhar as portas " + ", ".join(str(int(p["porta"])) for p in atencao) + ".")
        else:
            partes.append("Todas parecem do próprio Windows.")
    if dados.get("jarvisSoNoPc") is True:
        partes.append("A API do Jarvis está só no seu PC.")
    else:
        partes.append("Atenção: a API do Jarvis está aberta para a rede!")
    return " ".join(partes)


def _responder_integridade(dados):
    """Ticket 022: as travas mudaram desde que a API ligou? Fala só o nome curto do arquivo."""
    alterados = int(dados.get("alterados", 0))
    desde = str(dados.get("desde", ""))
    desde = desde if re.fullmatch(r"\d{2}:\d{2}", desde) else "a hora em que a API ligou"
    if alterados == 0:
        return f"Integridade: as {int(dados.get('vigiados', 0))} travas estão iguais desde {desde}."
    mudados = [a for a in (dados.get("arquivos") or []) if isinstance(a, dict) and a.get("estado") != "igual"][:3]
    nomes = ", ".join(_limpar_para_falar(str(a.get("nome", "")).rsplit("/", 1)[-1]) for a in mudados)
    return (f"Alerta de integridade: {_plural(alterados, 'trava mudou', 'travas mudaram')} desde {desde}: {nomes}. "
            "Se não foi você, investigue antes de usar o Jarvis.")


_TITULO_GOLPE = {
    "alto": "Cuidado: isso tem muita cara de golpe.",
    "suspeito": "Hum, isso é suspeito.",
}


def _responder_golpe(dados):
    """Ticket 023: nível + até 2 motivos + o conselho. A mensagem colada NUNCA é repetida."""
    nivel = dados.get("nivel")
    conselho = _limpar_para_falar(str(dados.get("conselho", "")))
    if nivel == "baixo":
        return conselho or "Não vi sinais fortes de golpe."
    if nivel not in _TITULO_GOLPE:
        raise ValueError("nivel desconhecido")
    motivos = [_limpar_para_falar(m) for m in (dados.get("motivos") or []) if isinstance(m, str)][:2]
    return " ".join([_TITULO_GOLPE[nivel], *motivos, conselho])


def _limpar_para_falar(texto):
    """Tira marcação do Markdown (**, `, \\) para a voz não ler símbolos."""
    texto = re.sub(r"[*`_\\#>\[\]]", "", texto)
    return re.sub(r"\s+", " ", texto).strip()[:120]
