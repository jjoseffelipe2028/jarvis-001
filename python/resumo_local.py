"""
Jarvis 001 — Frases locais do "bom dia" 🌅 (Ticket 038)

Duas frases que não vêm da API Java, e sim do próprio PC:
  - o placar do ÚLTIMO duelo Hacker × Sentinela (lido da nota em JARVIS_PASTA_INCIDENTES);
  - a nota do backup 3-2-1.

Regras:
- Só LÊ. Não roda o duelo (ele leva mais de 20 s e liga uma cópia da API): só lê o último placar.
- Da nota do duelo saem apenas NÚMEROS (ataques e defesas). Nenhum texto da nota é falado.
- Qualquer falha vira "sem frase": o bom dia nunca cai por causa disto.
"""

import re
from pathlib import Path

_PLACAR = re.compile(r"O hacker tentou (\d{1,3}) ataques\. A defesa pegou (\d{1,3})\.")
_NOME_DO_DUELO = re.compile(r"\d{4}-\d{2}-\d{2}-\d{6}-duelo\.md")
LIMITE_LEITURA = 4000


def frase_do_duelo(pasta):
    """'Último duelo: a defesa pegou 13 de 13.' ou None. O nome do arquivo tem a data: o maior é o mais novo."""
    if pasta is None:
        return None
    try:
        notas = sorted(p for p in Path(pasta).iterdir()
                       if p.is_file() and not p.is_symlink() and _NOME_DO_DUELO.fullmatch(p.name))
        if not notas:
            return None
        with open(notas[-1], encoding="utf-8", errors="replace") as f:
            achado = _PLACAR.search(f.read(LIMITE_LEITURA))
    except OSError:
        return None
    if not achado:
        return None
    ataques, pegou = int(achado.group(1)), int(achado.group(2))
    if ataques == 0 or pegou > ataques:
        return None  # nota estranha: melhor não falar
    if pegou == ataques:
        return f"Último duelo: a defesa pegou {pegou} de {ataques}."
    escaparam = ataques - pegou
    return (f"Último duelo: a defesa pegou {pegou} de {ataques}. "
            f"{escaparam} ataque{'s escaparam' if escaparam != 1 else ' escapou'}: veja a nota.")


def frase_do_backup(itens):
    """'Backup 3-2-1: 2 de 3. Falta: Offline.' (curta, para falar). `itens` vem do backup321.avaliar."""
    try:
        prontos = sum(1 for i in itens if i.ok)
        faltam = [str(i.nome) for i in itens if not i.ok]
        if not itens:
            return None
    except (TypeError, AttributeError):
        return None
    if not faltam:
        return f"Backup 3-2-1: {prontos} de {len(itens)}. Tudo certo."
    return f"Backup 3-2-1: {prontos} de {len(itens)}. Falta: {', '.join(faltam)}."


def frases_locais():
    """As frases de verdade deste PC. Cada uma falha sozinha, sem derrubar a outra."""
    frases = []
    try:
        import cortador  # só para ler linhas específicas do .env
        frases.append(frase_do_duelo(cortador.ler_pasta("JARVIS_PASTA_INCIDENTES")))
    except Exception:  # noqa: BLE001 - o bom dia não pode cair por causa de um extra
        pass
    try:
        import backup321
        import cortador
        itens = backup321.avaliar(
            backup321.cofre_na_nuvem(cortador.ler_pasta("JARVIS_PASTA_DAILY")),
            backup321.estado_do_git(cortador.ler_pasta("JARVIS_PASTA_GIT")),
            backup321.idade_da_copia(cortador.ler_pasta("JARVIS_BACKUP_OFFLINE")))
        frases.append(frase_do_backup(itens))
    except Exception:  # noqa: BLE001
        pass
    return [f for f in frases if f]
