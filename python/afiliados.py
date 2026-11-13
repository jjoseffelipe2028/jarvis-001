"""
Jarvis 001 — Catálogo de afiliados 🔗 (Ticket 042, fase V3 do módulo de vídeos)

Guarda os teus links de afiliado numa nota do cofre e monta o texto pronto para a descrição do vídeo.

  python afiliados.py adicionar "Mouse gamer X" "https://amzn.to/abc123" --loja amazon
  python afiliados.py listar
  python afiliados.py texto "mouse"        → texto com o link e o aviso de afiliado, para colar no post

Precisa da linha JARVIS_PASTA_AFILIADOS no .env (uma pasta que exista, ex.: a pasta `companies` do cofre).

Regras:
- O Jarvis NUNCA abre o link: só confere o texto dele.
- Loja só da lista fechada, e o endereço tem que ser da própria loja (link que imita loja é recusado).
- Só `https`. Sem usuário/senha no endereço, sem espaço, sem caractere estranho.
- A nota só cresce (linha nova no fim). O nome do produto é limpo antes de ir para a nota.
- O texto do post sempre leva o aviso "link de afiliado": é regra das plataformas e da lei.
"""

import argparse
import re
import sys
from datetime import datetime
from pathlib import Path
from urllib.parse import urlsplit

NOTA = "afiliados.md"
MAXIMO_LINK = 500
MAXIMO_LINHAS = 2000

# Loja → endereços oficiais (o endereço do link precisa ser um deles ou terminar com ".<um deles>")
LOJAS = {
    "amazon": ("amazon.com.br", "amzn.to"),
    "mercadolivre": ("mercadolivre.com.br", "mercadolivre.com"),
    "shopee": ("shopee.com.br", "shope.ee"),
    "magalu": ("magazineluiza.com.br", "magazinevoce.com.br", "magalu.com"),
    "aliexpress": ("aliexpress.com", "s.click.aliexpress.com"),
    "kabum": ("kabum.com.br",),
}
CABECALHO = ("# 🔗 Afiliados\n\n"
             "_Catálogo do Jarvis (Ticket 042). O Jarvis nunca abre estes links: só guarda._\n\n"
             "| Data | Loja | Produto | Link |\n|---|---|---|---|\n")
_LINHA = re.compile(r"^\| (\d{4}-\d{2}-\d{2}) \| ([a-z]+) \| (.+?) \| (https://\S+) \|$")
_HOST = re.compile(r"[a-z0-9-]{1,63}(\.[a-z0-9-]{1,63}){1,5}")


class ErroAfiliado(Exception):
    pass


def limpar_nome(nome):
    """Nome do produto → uma linha sem marcação de Markdown/HTML e sem barra de tabela."""
    texto = "".join(c if c.isprintable() else " " for c in str(nome or ""))
    for c in "|[]`*<>#\\\"!{}$":
        texto = texto.replace(c, " ")
    texto = " ".join(texto.split())[:80]
    if not texto:
        raise ErroAfiliado("Diga o nome do produto.")
    return texto


def conferir_link(link, loja):
    """Devolve o link conferido ou levanta ErroAfiliado. Nada é aberto: só o texto é analisado."""
    if loja not in LOJAS:
        raise ErroAfiliado("Loja fora da lista. Use uma destas: " + ", ".join(sorted(LOJAS)) + ".")
    link = str(link or "").strip()
    if not link or len(link) > MAXIMO_LINK:
        raise ErroAfiliado(f"O link está vazio ou tem mais de {MAXIMO_LINK} letras.")
    if any(c.isspace() or not c.isprintable() or ord(c) > 126 or c in "|<>\"'`\\" for c in link):
        raise ErroAfiliado("O link tem espaço ou caractere estranho.")
    try:
        partes = urlsplit(link)
        porta = partes.port
    except ValueError as e:
        raise ErroAfiliado("Não entendi esse link.") from e
    if partes.scheme != "https":
        raise ErroAfiliado("Só aceito link com https://.")
    if partes.username or partes.password or "@" in partes.netloc:
        raise ErroAfiliado("Link com \"@\" no endereço: é truque para levar a outro site.")
    if porta not in (None, 443):
        raise ErroAfiliado("Link com porta estranha.")
    host = (partes.hostname or "").lower()
    if not _HOST.fullmatch(host):
        raise ErroAfiliado("O endereço do link é estranho (letra disfarçada ou número no lugar do nome).")
    if not any(host == oficial or host.endswith("." + oficial) for oficial in LOJAS[loja]):
        raise ErroAfiliado(f"Esse endereço não é da loja {loja}. Confira se o link não é uma imitação.")
    return link


def adicionar(pasta, nome, link, loja, hoje=None):
    """Acrescenta uma linha na nota. Link repetido → recusa (não duplica)."""
    nome, link = limpar_nome(nome), conferir_link(link, loja)
    nota = Path(pasta) / NOTA
    if nota.is_symlink():
        raise ErroAfiliado("A nota de afiliados é um atalho: recusei por segurança.")
    itens = listar(pasta)
    if len(itens) >= MAXIMO_LINHAS:
        raise ErroAfiliado("O catálogo está cheio.")
    if any(i["link"] == link for i in itens):
        raise ErroAfiliado("Esse link já está no catálogo.")
    linha = f"| {(hoje or datetime.now()):%Y-%m-%d} | {loja} | {nome} | {link} |\n"
    novo = not nota.exists()
    with open(nota, "a", encoding="utf-8", newline="\n") as f:
        f.write((CABECALHO if novo else "") + linha)
    return nome


def listar(pasta):
    """Itens do catálogo. Linha que não segue o formato (ou com link que não passa mais na conferência) é ignorada."""
    nota = Path(pasta) / NOTA
    itens = []
    try:
        if nota.is_symlink() or nota.stat().st_size > 1024 * 1024:
            return itens
        linhas = nota.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return itens
    for linha in linhas[:MAXIMO_LINHAS + 10]:
        achado = _LINHA.match(linha.strip())
        if not achado or achado.group(2) not in LOJAS:
            continue
        try:  # a nota pode ter sido mexida à mão: confere de novo antes de confiar
            link = conferir_link(achado.group(4), achado.group(2))
            nome = limpar_nome(achado.group(3))
        except ErroAfiliado:
            continue
        itens.append({"data": achado.group(1), "loja": achado.group(2), "produto": nome, "link": link})
    return itens


def texto_do_post(itens, busca):
    """Texto pronto para a descrição, com o aviso de afiliado. Procura pelo nome (sem diferenciar maiúscula)."""
    busca = str(busca or "").strip().lower()
    achados = [i for i in itens if busca and busca in i["produto"].lower()][:5]
    if not achados:
        raise ErroAfiliado("Não achei produto com esse nome no catálogo.")
    linhas = ["🛒 O que eu uso no vídeo:"]
    linhas += [f"• {i['produto']}: {i['link']}" for i in achados]
    linhas += ["", "ℹ️ Link de afiliado: se você comprar por ele, eu ganho uma comissão e você não paga nada a mais."]
    return "\n".join(linhas)


def _pasta():
    import cortador  # só para ler UMA linha do .env
    pasta = cortador.ler_pasta("JARVIS_PASTA_AFILIADOS")
    if pasta is None:
        raise ErroAfiliado("Falta configurar no .env (pasta que exista): JARVIS_PASTA_AFILIADOS")
    return pasta


def main(argv=None, pasta=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(prog="afiliados.py", description="Catálogo de afiliados (Ticket 042)")
    sub = p.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("adicionar", help="guarda um link de afiliado")
    a.add_argument("produto")
    a.add_argument("link")
    a.add_argument("--loja", required=True, choices=sorted(LOJAS))
    sub.add_parser("listar", help="mostra o catálogo")
    t = sub.add_parser("texto", help="monta o texto do post com o aviso de afiliado")
    t.add_argument("busca")
    args = p.parse_args(argv)
    try:
        pasta = pasta or _pasta()
        if args.cmd == "adicionar":
            nome = adicionar(pasta, args.produto, args.link, args.loja)
            print(f"✅ Guardado: {nome} ({args.loja}). O link não foi aberto.")
        elif args.cmd == "listar":
            itens = listar(pasta)
            for i in itens:
                print(f"• {i['data']}  {i['loja']:<13} {i['produto']}")
            print(f"\n{len(itens)} link(s) no catálogo.")
        else:
            print(texto_do_post(listar(pasta), args.busca))
    except ErroAfiliado as e:
        print(f"❌ {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
