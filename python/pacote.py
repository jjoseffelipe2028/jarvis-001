"""
Jarvis 001 — Pacote do dia da live 📦 (Ticket 028)

Um comando só faz a fila inteira, para a live de 24h:
  momentos da caçada → corta os N mais fortes → textos da Gemma → gancho na tela → PAINEL no cofre.

Regras:
- Cada corte passa pelo MESMO porteiro do V5 (duração, limites e o @ do amigo).
- Um corte que falha NÃO derruba os outros: o motivo vai para o painel.
- A Gemma só sugere. O painel tem caixinhas para o José aprovar um por um.
- Nada é postado sozinho. Nenhum arquivo é sobrescrito.
"""

import json
from dataclasses import dataclass

import cortador
from cacador import _desarmar, hms
from cortador import ErroCorte
from textos import ErroTextos

TOP_PADRAO = 8


@dataclass
class Item:
    numero: int            # número do momento na caçada (1 = o mais forte)
    inicio: int
    fim: int
    forca: float
    corte: str = ""        # nome do arquivo do corte ("" = não saiu)
    com_gancho: str = ""   # nome do arquivo com o gancho na tela
    textos: dict | None = None
    aviso: str = ""        # o que deu errado (se deu)


def montar(video, duracao, momentos, arroba, saida, robo, sugerir, gravar_gancho,
           top=TOP_PADRAO, avisar=print):
    """Corta os `top` momentos mais fortes e prepara os textos e o gancho de cada um.
    `momentos`: lista de dicts da caçada (numero, inicio, fim, forca), já em ordem de força.
    `robo`, `sugerir` e `gravar_gancho` vêm de fora: nos testes são falsos."""
    if not cortador.ARROBA.fullmatch(arroba or ""):
        raise ErroCorte("O @ do amigo é obrigatório e só aceita letras, números, _ e ponto.")
    if not isinstance(top, int) or not 1 <= top <= cortador.MAX_CORTES:
        raise ErroCorte(f"O pacote aceita de 1 a {cortador.MAX_CORTES} cortes.")
    if not momentos:
        raise ErroCorte("A caçada não achou nenhum momento forte nesse vídeo.")

    itens = []
    for m in momentos[:top]:
        try:
            item = Item(int(m["numero"]), int(m["inicio"]), int(m["fim"]), float(m.get("forca", 0)))
        except (KeyError, TypeError, ValueError, AttributeError) as e:
            raise ErroCorte("A lista da caçada veio num formato estranho. Rode a caçada de novo.") from e
        itens.append(item)

        ok, motivo = cortador.liberar(item.inicio, item.fim, duracao, arroba)
        if not ok:
            item.aviso = f"porteiro barrou: {motivo}"
            continue

        destino = cortador.nome_saida(saida, item.numero)
        avisar(f"✂️  Momento {item.numero}: {hms(item.inicio)} → {hms(item.fim)} ...")
        try:
            robo.cortar(video, item.inicio, item.fim, duracao, arroba, destino)
        except ErroCorte as e:
            item.aviso = f"o corte falhou: {e}"
            continue
        item.corte = destino.name

        try:
            srt = destino.with_suffix(".srt").read_text(encoding="utf-8")
            item.textos = sugerir(srt, arroba)
        except (ErroTextos, OSError) as e:
            item.aviso = f"sem textos: {e}"
            continue
        # Mesmo arquivo do comando "textos": o comando "gancho" continua funcionando depois
        destino.with_name(f"{destino.stem}.textos.json").write_text(
            json.dumps(item.textos, ensure_ascii=False), encoding="utf-8")

        try:
            item.com_gancho = gravar_gancho(destino, item.textos["gancho"]).name
        except ErroCorte as e:
            item.aviso = f"sem gancho na tela: {e}"
    return itens


def painel_markdown(nome_video, arroba, itens):
    """A nota do cofre: um bloco por corte, com caixinhas para o José aprovar."""
    prontos = [i for i in itens if i.corte]
    linhas = [f"# 📦 Pacote da live: {_desarmar(nome_video)}", "",
              "_Feito pelo Jarvis (Ticket 028). A Gemma só sugere: quem aprova e posta é o José._", "",
              f"- Crédito em todo corte: **{_desarmar(cortador.arroba_normal(arroba))}**",
              f"- Cortes prontos: **{len(prontos)} de {len(itens)}**", ""]
    for i in itens:
        linhas.append(f"## Momento {i.numero} · {hms(i.inicio)} → {hms(i.fim)} (+{i.forca} dB)")
        if i.aviso:
            linhas.append(f"- ⚠️ {_desarmar(i.aviso)}")
        if not i.corte:
            linhas.append("")
            continue
        linhas.append(f"- 🎬 Corte: `{_desarmar(i.corte)}`")
        if i.com_gancho:
            linhas.append(f"- 🪝 Com gancho na tela: `{_desarmar(i.com_gancho)}`")
        if i.textos:
            t = i.textos
            linhas.append("- Títulos:")
            linhas += [f"  {n}. {_desarmar(titulo)}" for n, titulo in enumerate(t["titulos"], start=1)]
            linhas.append(f"- Gancho: {_desarmar(t['gancho'])}")
            linhas += ["- Legenda do post:", "", "```",
                       str(t["legenda"]).replace("`", "'"), "```", "",
                       "- Hashtags: " + " ".join(str(h).replace("`", "'") for h in t["hashtags"])]
        linhas += ["- [ ] Assisti o corte inteiro",
                   "- [ ] Sem música da rádio do GTA",
                   "- [ ] Aprovado para postar", ""]
    linhas += ["---", "⚠️ Nada foi postado. Marque \"feito com IA\" se a plataforma pedir.", ""]
    return "\n".join(linhas)
