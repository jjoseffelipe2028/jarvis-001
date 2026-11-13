"""
Jarvis 001 — Verificador de propostas de marca 🤝🔎 (Ticket 030, fase V4)

"Chegou um e-mail de patrocínio: é de verdade ou querem roubar o canal?"

  python proposta.py --remetente contato@marca.com --marca "Nome da Marca"
      → cole o texto do e-mail, depois escreva FIM numa linha sozinha e aperte Enter

Quem analisa é a API Java (regras fixas, sem IA). Ela só lê: nunca abre link,
nunca manda e-mail e nada sai do PC. Este comando só mostra a resposta.
"""

import argparse
import sys

from bracos import Bracos, ErroBraco
from intencoes import seguro

TITULOS = {
    "alto": "🚨 Muita cara de golpe!",
    "suspeito": "⚠️ Suspeito: confirme antes de responder.",
    "baixo": "✅ Não vi sinais fortes de golpe.",
}
LIMITE = 4000


def ler_texto(linhas):
    """Junta as linhas coladas até a linha FIM (ou até acabar)."""
    partes, tamanho = [], 0
    for linha in linhas:
        if linha.strip().upper() == "FIM":
            break
        partes.append(linha)
        tamanho += len(linha)
        if tamanho > LIMITE * 2:  # não deixa colar um livro inteiro na memória
            break
    return "".join(partes).strip()


def formatar(parecer):
    """Resposta da API → texto para o terminal. Nível fora da lista → erro (não inventa veredito)."""
    if not isinstance(parecer, dict) or parecer.get("nivel") not in TITULOS:
        raise ErroBraco("A API respondeu num formato estranho.")
    linhas = [TITULOS[parecer["nivel"]]]
    motivos = [m for m in (parecer.get("motivos") or []) if isinstance(m, str)][:20]
    if motivos:
        linhas.append("")
        linhas.append("Por quê:")
        linhas += [f"  • {seguro(m)}" for m in motivos]
    if isinstance(parecer.get("conselho"), str):
        linhas += ["", seguro(parecer["conselho"])]
    passos = [p for p in (parecer.get("passos") or []) if isinstance(p, str)][:10]
    if passos:
        linhas += ["", "Antes de fechar qualquer parceria:"]
        linhas += [f"  {n}. {seguro(p)}" for n, p in enumerate(passos, start=1)]
    return "\n".join(linhas)


def main(argv=None, entrada=None, bracos=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(prog="proposta.py", description="Verificador de propostas de marca (Ticket 030)")
    p.add_argument("--remetente", help="o e-mail de quem mandou (ex.: contato@marca.com)")
    p.add_argument("--marca", help="a marca que a proposta diz ser")
    a = p.parse_args(argv)
    if entrada is None:
        print("Cole o texto do e-mail. Para terminar, escreva FIM numa linha sozinha e aperte Enter.")
        entrada = sys.stdin
    texto = ler_texto(entrada)
    try:
        if len(texto) > LIMITE:
            print(f"(o texto passou de {LIMITE} letras: analisei só o começo)")
        parecer = (bracos or Bracos()).proposta(texto, a.remetente, a.marca)
        print(formatar(parecer))
    except ErroBraco as e:
        print(f"❌ {seguro(str(e))}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
