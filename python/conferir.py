"""
Jarvis 001 — Conferidor da instalação 🧰 (Ticket 033: "o Jarvis roda no meu PC?")

Para quem acabou de clonar o projeto (ex.: um recrutador). Um comando diz o que já está
pronto e o que falta, com o passo para resolver:

  python python/conferir.py

Só usa a biblioteca padrão do Python (roda antes de instalar qualquer coisa).
Só LÊ: não instala, não baixa e não muda nada. Do .env ele olha só os NOMES das chaves,
nunca os valores. A única conversa de rede é com o próprio PC (127.0.0.1).
"""

import json
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CHAVES_OBRIGATORIAS = ("JARVIS_TOKEN", "JARVIS_PASTA_DAILY", "JARVIS_PASTA_GIT")
CHAVES_OPCIONAIS = ("JARVIS_PASTA_DECISOES", "JARVIS_CORTES_ENTRADA", "JARVIS_CORTES_SAIDA",
                    "JARVIS_PASTA_INCIDENTES", "JARVIS_BACKUP_OFFLINE")
OLLAMA = "http://127.0.0.1:11434/api/tags"
MODELO = "gemma"

OK, FALTA, AVISO = "✅", "❌", "⚠️"


def versao_java(texto):
    """'openjdk version "21.0.4" 2024-07-16' → 21 · 'java version "1.8.0_391"' → 8 · estranho → None."""
    achado = re.search(r'version "(\d+)(?:\.(\d+))?', str(texto))
    if not achado:
        return None
    principal = int(achado.group(1))
    return int(achado.group(2)) if principal == 1 and achado.group(2) else principal


def chaves_do_env(texto):
    """Só os NOMES das chaves que têm algum valor. O valor nunca sai desta função."""
    nomes = set()
    for linha in str(texto).splitlines():
        linha = linha.strip()
        if linha.startswith("export "):
            linha = linha[len("export "):].strip()
        if not linha or linha.startswith("#") or "=" not in linha:
            continue
        nome, valor = linha.split("=", 1)
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", nome.strip()) and valor.strip().strip("\"'"):
            nomes.add(nome.strip())
    return nomes


def modelos_do_ollama(resposta):
    """JSON de /api/tags → nomes dos modelos (texto torto → lista vazia)."""
    try:
        return [str(m["name"]) for m in json.loads(resposta)["models"] if isinstance(m, dict) and "name" in m]
    except (ValueError, KeyError, TypeError):
        return []


def conferir_java(executar=subprocess.run):
    if shutil.which("java") is None:
        return FALTA, "Java: não achei. Instale o JDK 21 (Temurin, em adoptium.net)."
    try:
        r = executar(["java", "-version"], capture_output=True, text=True, timeout=20, shell=False)
        versao = versao_java(r.stderr + r.stdout)
    except (OSError, subprocess.SubprocessError):
        versao = None
    if versao is None:
        return AVISO, "Java: achei, mas não consegui ler a versão."
    if versao < 21:
        return FALTA, f"Java {versao}: o projeto precisa do 21 ou mais novo."
    return OK, f"Java {versao}."


def conferir_python(versao=sys.version_info):
    if (versao[0], versao[1]) < (3, 10):
        return FALTA, f"Python {versao[0]}.{versao[1]}: precisa do 3.10 ou mais novo."
    return OK, f"Python {versao[0]}.{versao[1]}."


def conferir_venv(raiz=RAIZ):
    for caminho in ("python/.venv/Scripts/python.exe", "python/.venv/bin/python"):
        if (raiz / caminho).is_file():
            return OK, "Ambiente do Python (.venv) criado."
    return FALTA, ("Ambiente do Python: falta. Na pasta python: `python -m venv .venv`, ative e rode "
                   "`pip install -r requirements.txt`.")


def conferir_env(raiz=RAIZ):
    arquivo = raiz / ".env"
    if not arquivo.is_file():
        return [(FALTA, "Arquivo .env: falta. Copie o `.env.example` para `.env` e preencha (cada pessoa cria o seu).")]
    try:
        nomes = chaves_do_env(arquivo.read_text(encoding="utf-8-sig"))
    except OSError:
        return [(FALTA, "Arquivo .env: existe, mas não consegui ler.")]
    linhas = []
    faltam = [c for c in CHAVES_OBRIGATORIAS if c not in nomes]
    if faltam:
        linhas.append((FALTA, ".env: falta preencher " + ", ".join(faltam) + "."))
    else:
        linhas.append((OK, ".env: as chaves principais estão preenchidas."))
    opcionais = [c for c in CHAVES_OPCIONAIS if c not in nomes]
    if opcionais:
        linhas.append((AVISO, ".env (opcional, só para vídeos, incidentes e backup): " + ", ".join(opcionais) + "."))
    return linhas


def conferir_ollama(abrir=urllib.request.urlopen):
    try:
        with abrir(OLLAMA, timeout=3) as r:
            modelos = modelos_do_ollama(r.read(200_000).decode("utf-8", "replace"))
    except (OSError, ValueError):
        return FALTA, "Ollama: não respondeu em 127.0.0.1:11434. Instale (ollama.com) e deixe aberto."
    if not any(m.startswith(MODELO) for m in modelos):
        return FALTA, "Ollama: ligado, mas sem o modelo Gemma. Rode o `ollama pull` do modelo que está no README."
    return OK, "Ollama ligado e com o modelo Gemma."


def conferir_programa(nome, para_que, obrigatorio):
    if shutil.which(nome):
        return OK, f"{nome}: instalado."
    return (FALTA if obrigatorio else AVISO), f"{nome}: não achei ({para_que})."


def conferir_tudo():
    linhas = [conferir_python(), conferir_java(), conferir_programa("git", "controle de versão", True),
              conferir_venv()]
    linhas += conferir_env()
    linhas += [conferir_ollama(),
               conferir_programa("ffmpeg", "só o robô de cortes e as historinhas precisam", False)]
    return linhas


def main():
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    linhas = conferir_tudo()
    print("🧰 Jarvis 001: conferindo a instalação (só leitura)\n")
    for marca, texto in linhas:
        print(f"{marca} {texto}")
    faltas = sum(1 for marca, _ in linhas if marca == FALTA)
    print()
    if faltas:
        print(f"Faltam {faltas} item(ns). Resolva os ❌ de cima para baixo e rode de novo.")
        return 1
    print("Tudo pronto. Para ligar: `Jarvis-Central.bat` (Windows) ou `java/bracos/iniciar.sh`.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
