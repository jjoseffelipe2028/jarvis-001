"""/dupla — cria o par plano + registro com nome LIVRE (Ticket 072, achado 4 da 002).

Se docs/planos/<slug>.md ou docs/reviews/<slug>.md já existir, usa <slug>-2, <slug>-3...
Nunca sobrescreve (abre com "x"). Só aceita slug no formato AAAA-MM-DD-nome (minúsculas, números, hífen).
Recebe SÓ o slug (achado 1 da 002): texto livre no comando vira Command Injection no Git Bash
($(...) e crase rodam antes do Python). O título de verdade a 001 escreve depois, editando o arquivo.

Uso (na raiz do repositório):
    python .claude/skills/dupla/scripts/novo_registro.py 2026-11-15-qual-lista-usar
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

SLUG_OK = re.compile(r"^\d{4}-\d{2}-\d{2}-[a-z0-9][a-z0-9-]{0,55}$")
MAX_TENTATIVAS = 50


def nome_livre(raiz: Path, slug: str) -> str:
    """Primeiro nome em que NEM o plano NEM o registro existem."""
    if not SLUG_OK.match(slug):
        raise ValueError(f"Slug recusado: {slug} (precisa ser AAAA-MM-DD-nome).")
    for n in range(1, MAX_TENTATIVAS + 1):
        nome = slug if n == 1 else f"{slug}-{n}"
        if not (raiz / "docs" / "planos" / f"{nome}.md").exists() and \
           not (raiz / "docs" / "reviews" / f"{nome}.md").exists():
            return nome
    raise ValueError(f"Mais de {MAX_TENTATIVAS} arquivos com o slug {slug}.")


def titulo_do_slug(slug: str) -> str:
    """'2026-11-15-qual-lista-usar' → 'qual lista usar' (só letras, números e espaço: seguro e de 1 linha)."""
    return slug[11:].replace("-", " ")[:80]


def criar(raiz: Path, slug: str) -> tuple[Path, Path]:
    nome = nome_livre(raiz, slug)  # valida o slug antes de qualquer coisa
    titulo = titulo_do_slug(slug)
    plano = raiz / "docs" / "planos" / f"{nome}.md"
    registro = raiz / "docs" / "reviews" / f"{nome}.md"
    with plano.open("x", encoding="utf-8") as f:
        f.write(f"# Plano — {titulo}\n\n")
    with registro.open("x", encoding="utf-8") as f:
        f.write(f"# Registro da dupla — {titulo}\n\n- **Plano:** `docs/planos/{nome}.md`\n")
    return plano, registro


def main() -> None:
    for fluxo in (sys.stdout, sys.stderr):  # terminal do Windows (cp1252) estraga acento
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8")
    if len(sys.argv) != 2:
        sys.exit("Uso: novo_registro.py AAAA-MM-DD-nome (só o slug)")
    import subprocess
    r = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("Rode dentro do repositório jarvis-001.")
    try:
        plano, registro = criar(Path(r.stdout.strip()).resolve(), sys.argv[1])
    except ValueError as e:
        sys.exit(str(e))
    print(f"Criados: docs/planos/{plano.name} e docs/reviews/{registro.name}")
    print("Use ESTES caminhos nos próximos passos (o nome pode ter ganho -2, -3).")


if __name__ == "__main__":
    main()
