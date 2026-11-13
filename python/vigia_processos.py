"""
Jarvis 001 — Vigia de processos ⛏️ (Ticket 040: detector de minerador escondido)

Minerador de criptomoeda escondido usa o teu processador o tempo todo: o PC esquenta e fica lento.
O vigia mede, por alguns segundos, quais programas mais usam o processador e aponta os suspeitos.

  python vigia_processos.py            (mede por 5 segundos)
  python vigia_processos.py --segundos 15

Regras:
- Só LÊ a lista de processos do Windows (comando fixo, sem shell). Não fecha, não apaga, não muda nada.
- Suspeito = nome de minerador conhecido, OU muito processador vindo de pasta temporária / Downloads / sem caminho.
- Programa pesado conhecido (jogo, navegador, o próprio Java) aparece só como "pesado".
- Quem decide fechar é o José. Técnica do MITRE: T1496 (Resource Hijacking).
"""

import csv
import io
import os
import subprocess
import sys
import time
from dataclasses import dataclass

COMANDO = ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
           "Get-Process | Select-Object Id,ProcessName,CPU,Path | ConvertTo-Csv -NoTypeInformation"]
LIMITE_PESADO = 25.0     # % do processador inteiro, na média da medição
LIMITE_SUSPEITO = 10.0   # vindo de lugar estranho, já chama atenção com menos
MINERADORES = ("xmrig", "xmr-stak", "minerd", "cpuminer", "nbminer", "phoenixminer", "t-rex", "lolminer",
               "ethminer", "nicehash", "gminer", "teamredminer", "srbminer", "wildrig", "nanominer")
# Processos do próprio Windows que escondem o caminho de quem não é administrador: sem caminho é o normal deles
DO_WINDOWS = ("system", "idle", "registry", "memcompression", "secure system", "csrss", "smss", "wininit",
              "services", "lsass", "msmpeng", "nissrv", "securityhealthservice", "sgrmbroker", "dwm", "winlogon")
LUGARES_ESTRANHOS = ("\\appdata\\local\\temp\\", "\\windows\\temp\\", "\\downloads\\", "\\users\\public\\",
                     "\\programdata\\", "\\$recycle.bin\\")


@dataclass
class Uso:
    pid: int
    nome: str
    porcento: float      # % do processador inteiro
    nivel: str           # "suspeito", "pesado" ou "normal"
    motivo: str


def ler_amostra(texto_csv):
    """CSV do PowerShell → {pid: (nome, segundos de CPU, caminho)}. Linha torta é ignorada."""
    amostra = {}
    for linha in csv.DictReader(io.StringIO(str(texto_csv))):
        try:
            pid = int(linha.get("Id", ""))
            cpu = float((linha.get("CPU") or "0").replace(",", "."))
        except (TypeError, ValueError):
            continue
        if pid <= 0 or cpu < 0:
            continue
        amostra[pid] = (str(linha.get("ProcessName") or "?")[:60], cpu, str(linha.get("Path") or ""))
    return amostra


def classificar(nome, caminho, porcento):
    """(nível, motivo). O motivo é texto fixo daqui."""
    baixo = nome.lower()
    if any(m in baixo for m in MINERADORES):
        return "suspeito", "nome de minerador conhecido"
    lugar = caminho.lower().replace("/", "\\")
    if porcento >= LIMITE_SUSPEITO:
        if not lugar and baixo not in DO_WINDOWS:
            return "suspeito", "usa muito processador e o Windows não mostra de onde ele roda"
        if any(l in lugar for l in LUGARES_ESTRANHOS):
            return "suspeito", "usa muito processador e roda de pasta temporária ou de download"
    if porcento >= LIMITE_PESADO:
        return "pesado", "usa muito processador (se for jogo, navegador ou programa teu, é normal)"
    return "normal", ""


def calcular(antes, depois, segundos, nucleos):
    """Quanto cada processo usou entre as duas amostras, do maior para o menor."""
    usos = []
    if segundos <= 0 or nucleos <= 0:
        return usos
    for pid, (nome, cpu_depois, caminho) in depois.items():
        if pid not in antes or antes[pid][0] != nome:
            continue  # nasceu no meio da medição (ou o número foi reaproveitado): fica para a próxima
        gasto = cpu_depois - antes[pid][1]
        if gasto <= 0:
            continue
        porcento = round(min(100.0, gasto / segundos / nucleos * 100), 1)
        nivel, motivo = classificar(nome, caminho, porcento)
        usos.append(Uso(pid, nome, porcento, nivel, motivo))
    ordem = {"suspeito": 0, "pesado": 1, "normal": 2}
    return sorted(usos, key=lambda u: (ordem[u.nivel], -u.porcento))


def frase(usos):
    suspeitos = [u for u in usos if u.nivel == "suspeito"]
    if suspeitos:
        return f"Vigia de processos: {len(suspeitos)} suspeito(s). Veja a lista antes de tudo."
    pesados = [u for u in usos if u.nivel == "pesado"]
    if pesados:
        return f"Vigia de processos: nenhum suspeito. {len(pesados)} programa(s) pesado(s) agora."
    return "Vigia de processos: nenhum suspeito e nada pesado agora."


def _amostrar(executar):
    r = executar(COMANDO, capture_output=True, text=True, timeout=60, shell=False, stdin=subprocess.DEVNULL)
    if r.returncode != 0:
        raise OSError("não consegui ler os processos")
    return ler_amostra(r.stdout)


def medir(segundos=5, executar=subprocess.run, dormir=time.sleep, relogio=time.monotonic):
    inicio = relogio()
    antes = _amostrar(executar)
    dormir(segundos)
    depois = _amostrar(executar)
    # O tempo de verdade entre as amostras (a 1ª leitura também demora)
    return calcular(antes, depois, max(relogio() - inicio, 0.5) - 0.0, os.cpu_count() or 1)


def main(argv=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    argv = sys.argv[1:] if argv is None else argv
    segundos = 5
    if len(argv) == 2 and argv[0] == "--segundos" and argv[1].isdigit() and 2 <= int(argv[1]) <= 60:
        segundos = int(argv[1])
    elif argv:
        print("Uso: python vigia_processos.py [--segundos 2 a 60]")
        return 2
    if os.name != "nt":
        print("❌ O vigia de processos só funciona no Windows.")
        return 2
    print(f"⛏️ Medindo o uso do processador por {segundos} segundos...")
    try:
        usos = medir(segundos)
    except (OSError, subprocess.SubprocessError) as e:
        print(f"❌ {e}")
        return 2
    icone = {"suspeito": "🚨", "pesado": "⚠️", "normal": "•"}
    for u in [u for u in usos if u.nivel != "normal"] + [u for u in usos if u.nivel == "normal"][:5]:
        linha = f"{icone[u.nivel]} {u.porcento:>5.1f}%  {u.nome}  (PID {u.pid})"
        print(linha + (f"  → {u.motivo}" if u.motivo else ""))
    print()
    print(frase(usos))
    if any(u.nivel == "suspeito" for u in usos):
        print("👉 Não feche no susto. Anote o nome e o PID, rode `python incidentes.py novo \"processo suspeito\"` e investigue.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
