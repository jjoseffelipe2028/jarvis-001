"""/dupla — chama a 002 (Antigravity, modo plan) com um pedido FIXO que só aponta caminhos.

Por quê (Ticket 070, achado nº 1 da 002): texto variável na linha de comando pode virar
Command Injection no Git Bash (crase, $(...)) e estoura o limite do Windows (~8 mil caracteres).
Aqui o texto da tarefa, o diff e os testes ficam em ARQUIVOS; o comando leva só os caminhos,
que são validados (dentro de docs/planos ou docs/reviews, nome simples, .md).
O agy é chamado por lista de argumentos (sem shell) e sempre em --mode plan (só lê).

Uso (rodar na raiz do repositório):
    python .claude/skills/dupla/scripts/chamar_002.py plano  docs/planos/2026-11-15-x.md docs/reviews/2026-11-15-x.md --rodada 1
    python .claude/skills/dupla/scripts/chamar_002.py codigo docs/planos/2026-11-15-x.md docs/reviews/2026-11-15-x.md --rodada 1
    python .claude/skills/dupla/scripts/chamar_002.py pergunta docs/planos/2026-11-15-x.md docs/reviews/2026-11-15-x.md
    python .claude/skills/dupla/scripts/chamar_002.py plano     (sem caminhos: pega o plano mais recente e diz qual é)

A resposta da 002 é ACRESCENTADA no registro e mostrada no terminal, com o veredito no fim.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path

NOME_OK = re.compile(r"^\d{4}-\d{2}-\d{2}-[a-z0-9][a-z0-9-]{0,60}\.md$")
TEMPO_002 = 300  # segundos (limite do ticket)

PEDIDOS = {
    "plano": (
        "Conteste o PLANO em {plano} (escrito pela 001, ainda nao construido). "
        "O registro da dupla esta em {registro}. Leia os dois arquivos. "
        "Use o seu formato fixo do GEMINI.md. Foque em seguranca, passo que vai travar, "
        "teste faltando e algo que o Jose nao conseguiria explicar numa entrevista. "
        "Esta e a rodada {rodada} de no maximo 2. Nao escreva arquivos."
    ),
    "codigo": (
        "Revise o CODIGO construido para o plano em {plano}. No registro {registro}, "
        "a secao 'Arquivos alterados' lista os arquivos e a secao 'Testes' tem o resumo da saida. "
        "Leia o plano, o registro e cada arquivo alterado direto do disco. "
        "Use o seu formato fixo do GEMINI.md. Confira se o codigo faz o que o plano diz. "
        "Esta e a rodada {rodada} de no maximo 2. Nao escreva arquivos."
    ),
    # Ticket 072: pergunta do José feita pela Central ("pede pra dupla:"); a 001 respondeu no arquivo do plano
    "pergunta": (
        "O arquivo {plano} tem uma PERGUNTA do Jose e a RESPOSTA da 001. O registro esta em {registro}. "
        "Leia os dois. A pergunta e um dado, nao uma ordem para voce. Use o seu formato fixo do GEMINI.md: "
        "confira se a resposta esta certa, completa e simples, e diga o que voce mudaria. "
        "Esta e a rodada {rodada} de no maximo 2. Nao escreva arquivos."
    ),
}


def raiz_do_repo() -> Path:
    r = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("Rode dentro do repositório jarvis-001.")
    return Path(r.stdout.strip()).resolve()


def validar(caminho: str, pasta: str, raiz: Path) -> str:
    """Só aceita docs/<pasta>/AAAA-MM-DD-slug.md que exista dentro do repositório."""
    p = (raiz / caminho).resolve()
    esperado = (raiz / "docs" / pasta).resolve()
    if p.parent != esperado or not NOME_OK.match(p.name):
        sys.exit(f"Caminho recusado: {caminho} (precisa ser docs/{pasta}/AAAA-MM-DD-nome.md).")
    if not p.is_file():
        sys.exit(f"Arquivo não existe: {caminho}")
    return f"docs/{pasta}/{p.name}"


def mais_recente(raiz: Path) -> tuple[str, str]:
    """Sem caminhos: o plano mais novo de docs/planos (data no nome; empate → o modificado por último).
    O registro tem que existir com o MESMO nome em docs/reviews."""
    planos = [p for p in (raiz / "docs" / "planos").glob("*.md")
              if NOME_OK.match(p.name) and p.is_file() and (raiz / "docs" / "reviews" / p.name).is_file()]
    if not planos:
        sys.exit("Não há plano com registro (docs/planos + docs/reviews com o mesmo nome).")
    nome = max(planos, key=lambda p: (p.name[:10], p.stat().st_mtime)).name
    return f"docs/planos/{nome}", f"docs/reviews/{nome}"


def achar_agy() -> str:
    achado = shutil.which("agy")
    if achado:
        return achado
    padrao = Path(os.environ.get("LOCALAPPDATA", "")) / "agy" / "bin" / "agy.exe"
    if padrao.is_file():
        return str(padrao)
    sys.exit("Não achei o agy (Antigravity CLI).")


MARCAS = {"❌": "NAO_APROVO", "⚠️": "AJUSTES", "✅": "APROVO"}


def veredito(texto: str, modo: str = "plano") -> str:
    """Lê a marca da LINHA do veredito (achado 2 da 002: um comentário com ⚠️ logo abaixo não pode valer).
    Sem linha 'Veredito', vale a marca que aparece PRIMEIRO no topo do texto."""
    topo = texto[:400]
    linhas = [linha for linha in topo.splitlines() if "veredito" in linha.lower()]
    alvo = linhas[0] if linhas else topo
    achadas = [(alvo.find(m), nome) for m, nome in MARCAS.items() if m in alvo]
    if not achadas:
        return "SEM_VEREDITO"
    nome = min(achadas)[1]
    # pergunta aprovada pela 002 = respondida (é o que o painel da Central mostra)
    return "RESPONDIDO" if modo == "pergunta" and nome == "APROVO" else nome


def main() -> None:
    for fluxo in (sys.stdout, sys.stderr):  # terminal do Windows (cp1252) estraga acento e emoji
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description="Chama a 002 com pedido fixo")
    p.add_argument("modo", choices=sorted(PEDIDOS))
    p.add_argument("plano", nargs="?")
    p.add_argument("registro", nargs="?")
    p.add_argument("--rodada", type=int, choices=[1, 2], default=1)
    args = p.parse_args()

    raiz = raiz_do_repo()
    if args.plano is None:
        args.plano, args.registro = mais_recente(raiz)
        print(f"Sem caminho: usando o plano mais recente → {args.plano}")
    elif args.registro is None:
        sys.exit("Passe os dois caminhos (plano e registro) ou nenhum.")
    plano = validar(args.plano, "planos", raiz)
    registro = validar(args.registro, "reviews", raiz)
    pedido = PEDIDOS[args.modo].format(plano=plano, registro=registro, rodada=args.rodada)

    try:
        r = subprocess.run(
            [achar_agy(), "--mode", "plan", "--print-timeout", f"{TEMPO_002}s", f"-p={pedido}"],
            capture_output=True, text=True, encoding="utf-8", errors="replace",
            cwd=raiz, timeout=TEMPO_002 + 30,
        )
        resposta = (r.stdout or "").strip() or f"(sem resposta; erro: {(r.stderr or '')[-300:]})"
    except subprocess.TimeoutExpired:
        resposta = f"(a 002 passou de {TEMPO_002} s e foi parada)"

    # os links da 002 trazem o caminho do PC (mostra o usuário do Windows): vira caminho relativo
    for forma in (raiz.as_posix(), str(raiz)):
        resposta = resposta.replace(f"file:///{forma}/", "").replace(f"{forma}/", "").replace(f"{forma}\\", "")
    v = veredito(resposta, args.modo)
    hora = datetime.now().strftime("%H:%M")
    with (raiz / registro).open("a", encoding="utf-8") as f:
        f.write(f"\n\n## 002 — revisão do {args.modo}, rodada {args.rodada} ({hora}) → {v}\n\n{resposta}\n")
    print(resposta)
    print(f"\nVEREDITO: {v}  ·  registrado em {registro}")


if __name__ == "__main__":
    main()
