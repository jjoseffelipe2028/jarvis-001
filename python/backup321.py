"""
Jarvis 001 — Guarda do Backup 3-2-1 💾 (Ticket 032: defesa contra ransomware)

A regra: 3 cópias · 2 mídias diferentes · 1 fora de casa.
O guarda confere 3 coisas e dá a nota "X de 3":
  1. NUVEM    → o cofre do Obsidian está dentro da pasta do OneDrive?
  2. GITHUB   → o repositório do Jarvis está em dia (nada sem commit, nada sem push)?
  3. OFFLINE  → existe uma cópia num pendrive/HD (JARVIS_BACKUP_OFFLINE) com menos de 7 dias?

  python backup321.py

Só LÊ. Não copia, não apaga e não usa a internet (não faz fetch).
"""

import os
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

DIAS_OFFLINE = 7
MAXIMO_ARQUIVOS = 5000   # olha no máximo isso na cópia offline (não trava em HD gigante)
TEMPO_GIT = 15


@dataclass
class Item:
    nome: str
    ok: bool
    frase: str


def cofre_na_nuvem(cofre):
    """True se algum pedaço do caminho for a pasta do OneDrive ("OneDrive" ou "OneDrive - Empresa")."""
    if cofre is None:
        return None
    return any(parte == "OneDrive" or parte.startswith("OneDrive - ") for parte in Path(cofre).parts)


def estado_do_git(repo, executar=subprocess.run):
    """{"sem_commit": n, "sem_push": n} ou None se não deu para saber. Comandos fixos, sem shell, sem rede."""
    if repo is None:
        return None
    ambiente = dict(os.environ, GIT_OPTIONAL_LOCKS="0", GIT_TERMINAL_PROMPT="0")

    def git(*argumentos):
        r = executar(["git", "-C", str(repo), *argumentos], capture_output=True, text=True,
                     timeout=TEMPO_GIT, shell=False, env=ambiente)
        if r.returncode != 0:
            raise OSError("git falhou")
        return r.stdout

    try:
        sem_commit = len([linha for linha in git("status", "--porcelain").splitlines() if linha.strip()])
        sem_push = int(git("rev-list", "--count", "@{u}..HEAD").strip())
    except (OSError, ValueError, subprocess.SubprocessError):
        return None
    return {"sem_commit": sem_commit, "sem_push": sem_push}


def idade_da_copia(pasta, agora=None):
    """Dias desde o arquivo mais novo da cópia offline. Pasta vazia ou ausente → None."""
    if pasta is None:
        return None
    agora = agora if agora is not None else time.time()
    mais_novo, vistos = None, 0
    try:
        for raiz, pastas, arquivos in os.walk(pasta, followlinks=False):
            for nome in arquivos:
                vistos += 1
                try:
                    quando = os.lstat(os.path.join(raiz, nome)).st_mtime
                except OSError:
                    continue
                mais_novo = quando if mais_novo is None else max(mais_novo, quando)
                if vistos >= MAXIMO_ARQUIVOS:
                    break
            if vistos >= MAXIMO_ARQUIVOS:
                break
    except OSError:
        return None
    if mais_novo is None:
        return None
    return max(0, int((agora - mais_novo) // 86400))


def avaliar(na_nuvem, git, dias_offline):
    """Junta as 3 conferências. Na dúvida, NÃO dá o ponto (falha fechada)."""
    itens = []
    if na_nuvem is None:
        itens.append(Item("Nuvem", False, "não achei a pasta do cofre (JARVIS_PASTA_DAILY no .env)"))
    elif na_nuvem:
        itens.append(Item("Nuvem", True, "o cofre está na pasta do OneDrive (confira o ícone verde)"))
    else:
        itens.append(Item("Nuvem", False, "o cofre está fora do OneDrive: só existe neste PC"))

    if git is None:
        itens.append(Item("GitHub", False, "não consegui conferir o repositório (JARVIS_PASTA_GIT no .env)"))
    elif git["sem_commit"] or git["sem_push"]:
        partes = []
        if git["sem_commit"]:
            partes.append(f"{git['sem_commit']} arquivo(s) sem commit")
        if git["sem_push"]:
            partes.append(f"{git['sem_push']} commit(s) sem push")
        itens.append(Item("GitHub", False, "repositório atrasado: " + " e ".join(partes)))
    else:
        itens.append(Item("GitHub", True, "repositório em dia (tudo commitado e enviado)"))

    if dias_offline is None:
        itens.append(Item("Offline", False, "falta a cópia offline (pendrive ou HD em JARVIS_BACKUP_OFFLINE)"))
    elif dias_offline > DIAS_OFFLINE:
        itens.append(Item("Offline", False, f"a cópia offline tem {dias_offline} dias: refaça (máximo {DIAS_OFFLINE})"))
    else:
        itens.append(Item("Offline", True, f"cópia offline com {dias_offline} dia(s)"))
    return itens


def frase(itens):
    """Uma linha, do jeito que o bom dia falaria."""
    prontos = sum(1 for i in itens if i.ok)
    faltas = [i.frase for i in itens if not i.ok]
    if not faltas:
        return f"Backup 3-2-1: {prontos} de {len(itens)}. Tudo certo."
    return f"Backup 3-2-1: {prontos} de {len(itens)}. Falta: " + "; ".join(faltas) + "."


def main():
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    import cortador  # só para ler linhas específicas do .env (mesma regra dos cortes)
    cofre = cortador.ler_pasta("JARVIS_PASTA_DAILY")
    itens = avaliar(cofre_na_nuvem(cofre),
                    estado_do_git(cortador.ler_pasta("JARVIS_PASTA_GIT")),
                    idade_da_copia(cortador.ler_pasta("JARVIS_BACKUP_OFFLINE")))
    for i in itens:
        print(f"{'✅' if i.ok else '❌'} {i.nome}: {i.frase}")
    print()
    print(frase(itens))
    return 0 if all(i.ok for i in itens) else 1


if __name__ == "__main__":
    sys.exit(main())
