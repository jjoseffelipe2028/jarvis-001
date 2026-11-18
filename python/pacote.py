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
import re
from dataclasses import dataclass, field

import cortador
import olhos
from cacador import ANTES, INICIO_SEGURO, _desarmar, hms, janela_tiktok
from cortador import ErroCorte
from textos import ErroTextos

TOP_PADRAO = 8
# Ticket 093: fala de violência nos 7 s iniciais (o YouTube tira o anúncio). Só AVISA, não bloqueia.
VIOLENCIA = re.compile(
    r"\b(?:tiros?|atir\w*|mat(?:ei|ou|a|aram)|morr\w*|sangue|bala|metralh\w*|atropel\w*|"
    r"explod\w*|bomba|faca|esfaque\w*|degol\w*)\b", re.I)
_TEMPO_SRT = re.compile(r"(\d+):(\d\d):(\d\d),(\d{3})\s*-->")


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
    pico: int = 0
    corte_tiktok: str = ""  # Ticket 089: versão de ~70 s ("" = não pedida ou não saiu)
    aviso_tiktok: str = ""
    alertas_7s: list = field(default_factory=list)  # Ticket 093: tiro/sangue no começo do corte


def montar(video, duracao, momentos, arroba, saida, robo, sugerir, gravar_gancho,
           top=TOP_PADRAO, avisar=print, tiktok=False, estouros=None):
    """Corta os `top` momentos mais fortes e prepara os textos e o gancho de cada um.
    `momentos`: lista de dicts da caçada (numero, inicio, fim, forca), já em ordem de força.
    `robo`, `sugerir` e `gravar_gancho` vêm de fora: nos testes são falsos.
    `tiktok=True` (Ticket 089): cada momento ganha também a versão de ~70 s. Se ela falhar,
    o Short continua (o aviso vai separado).
    `estouros(caminho)` (Ticket 093): segundos com estouro seco no começo do corte (None = só a fala)."""
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
            item.pico = int(m.get("pico", item.inicio + ANTES))  # caçada antiga sem "pico": estima
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
        if tiktok:
            _cortar_tiktok(item, video, duracao, arroba, destino, robo, avisar)
        item.alertas_7s = checar_7s(destino, estouros)
        if item.corte_tiktok:
            item.alertas_7s += [f"TikTok: {a}" for a in checar_7s(destino.with_name(item.corte_tiktok), estouros)]

        try:
            srt = destino.with_suffix(".srt").read_text(encoding="utf-8")
            cenas = None
            if olhos.precisa_dos_olhos(srt):  # Ticket 097: corte mudo → a Gemma olha as fotos
                try:
                    cenas = olhos.texto_das_cenas(olhos.cenas_do_corte(destino, avisar=avisar))
                except (olhos.ErroOlhos, ErroCorte, OSError) as e:
                    avisar(f"👀 Os olhos falharam ({e}); seguindo só com a legenda.")
            item.textos = sugerir(srt, arroba, cenas=cenas) if cenas else sugerir(srt, arroba)
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


def _cortar_tiktok(item, video, duracao, arroba, destino, robo, avisar):
    """Versão longa do momento, ao lado do Short: <corte>-tiktok.mp4. Passa pelo mesmo porteiro."""
    janela = janela_tiktok(item.pico, duracao)
    if janela is None:
        item.aviso_tiktok = "o vídeo é curto demais para uma versão de mais de 1 min"
        return
    inicio, fim = janela
    ok, motivo = cortador.liberar(inicio, fim, duracao, arroba)
    if not ok:
        item.aviso_tiktok = f"porteiro barrou: {motivo}"
        return
    alvo = destino.with_name(f"{destino.stem}-tiktok.mp4")
    if alvo.exists() or alvo.with_suffix(".srt").exists():
        item.aviso_tiktok = "já existe um arquivo com esse nome (nada foi sobrescrito)"
        return
    avisar(f"📱 Momento {item.numero} (TikTok): {hms(inicio)} → {hms(fim)} ...")
    try:
        robo.cortar(video, inicio, fim, duracao, arroba, alvo)
    except ErroCorte as e:
        item.aviso_tiktok = f"a versão TikTok falhou: {e}"
        return
    item.corte_tiktok = alvo.name


def falas_violentas(texto_srt, ate=INICIO_SEGURO):
    """Palavras de violência nas falas que COMEÇAM nos `ate` s iniciais do .srt."""
    achadas, comeco = [], None
    for linha in str(texto_srt).splitlines():
        t = _TEMPO_SRT.search(linha)
        if t:
            h, m, s, ms = (int(x) for x in t.groups())
            comeco = h * 3600 + m * 60 + s + ms / 1000
            continue
        if comeco is not None and comeco < ate:
            for p in VIOLENCIA.findall(linha):
                if p.lower() not in achadas:
                    achadas.append(p.lower())
    return achadas


def checar_7s(corte, estouros=None):
    """Alertas dos 7 s iniciais de um corte: fala de violência (.srt) e estouro seco (áudio)."""
    alertas = []
    try:
        palavras = falas_violentas(corte.with_suffix(".srt").read_text(encoding="utf-8"))
    except OSError:
        palavras = []
    if palavras:
        alertas.append("fala de violência: " + ", ".join(palavras[:4]))
    if estouros:
        try:
            segundos = estouros(corte)
        except Exception:  # áudio estranho: a checagem não derruba o pacote
            segundos = []
        if segundos:
            alertas.append("estouro seco (tiro?) no segundo " + ", ".join(str(s) for s in segundos[:4]))
    return alertas


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
        if i.corte_tiktok:
            linhas.append(f"- 📱 Versão TikTok (> 1 min, a que paga): `{_desarmar(i.corte_tiktok)}`")
        elif i.aviso_tiktok:
            linhas.append(f"- 📱 Sem versão TikTok: {_desarmar(i.aviso_tiktok)}")
        for alerta in i.alertas_7s:  # Ticket 093
            linhas.append(f"- 🩸 Nos {INICIO_SEGURO} s iniciais: {_desarmar(alerta)}")
        if i.textos:
            t = i.textos
            linhas.append("- Títulos:")
            linhas += [f"  {n}. {_desarmar(titulo)}" for n, titulo in enumerate(t["titulos"], start=1)]
            linhas.append(f"- Gancho: {_desarmar(t['gancho'])}")
            linhas += ["- Legenda do post:", "", "```",
                       str(t["legenda"]).replace("`", "'"), "```", "",
                       "- Hashtags: " + " ".join(str(h).replace("`", "'") for h in t["hashtags"])]
        linhas += ["- [ ] Assisti o corte inteiro",
                   f"- [ ] Sem tiro/sangue nos {INICIO_SEGURO} s iniciais e na thumb (senão: sem anúncio)",
                   "- [ ] Sem música da rádio do GTA",
                   "- [ ] Aprovado para postar", ""]
    linhas += ["---", "⚠️ Nada foi postado. Marque \"feito com IA\" se a plataforma pedir.", ""]
    return "\n".join(linhas)
