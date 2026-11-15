"""
Jarvis 001 — Relatório semanal de segurança 📋 (Ticket 084)

Junta numa nota só, para o cofre:
  - a ronda do defensor (Sentinela com portas/travas/isca/alarme, processos, rede, inicialização, backup, duelo);
  - o gitleaks no histórico do Git (algum segredo foi commitado?);
  - quantas rondas e incidentes a semana teve (contando as notas da pasta de incidentes).

  python relatorio_semanal.py

Regras:
- Só LÊ. Não fecha, não bloqueia, não apaga. Quem age é o José (a nota tem a lista "O que eu fiz a respeito").
- O gitleaks roda com comando FIXO (sem shell) e com --redact: o segredo nunca aparece. A nota leva só a
  QUANTIDADE e o TIPO de achado (ex.: "generic-api-key"), nunca o valor nem a linha.
- A nota vai para JARVIS_PASTA_INCIDENTES com nome gerado aqui (data), e nunca sobrescreve outra.
"""

import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

import defensor
from defensor import ALERTA, ATENCAO, ICONE, OK

RAIZ = Path(__file__).resolve().parent.parent
GITLEAKS_PADRAO = Path.home() / "ferramentas" / "gitleaks" / "gitleaks.exe"
LIMITE_RELATORIO = 2 * 1024 * 1024
MAXIMO_NOTAS = 2000


def caminho_gitleaks(ambiente=os.environ):
    """JARVIS_GITLEAKS (variável de ambiente) ou o lugar de sempre. Precisa ser um arquivo gitleaks(.exe)."""
    valor = ambiente.get("JARVIS_GITLEAKS", "").strip()
    caminho = Path(valor) if valor else GITLEAKS_PADRAO
    return caminho if caminho.is_file() and caminho.name.lower() in ("gitleaks.exe", "gitleaks") else None


def rodar_gitleaks(executavel, raiz=RAIZ, executar=subprocess.run):
    """→ lista de tipos de achado (RuleID). Comando fixo, sem shell, segredo redigido. Falha → OSError."""
    with tempfile.TemporaryDirectory() as pasta:
        relatorio = Path(pasta) / "gitleaks.json"
        comando = [str(executavel), "git", str(raiz), "--no-banner", "--redact", "--exit-code", "0",
                   "--report-format", "json", "--report-path", str(relatorio)]
        r = executar(comando, capture_output=True, text=True, errors="replace", timeout=120, shell=False,
                     stdin=subprocess.DEVNULL)
        if r.returncode != 0 or not relatorio.is_file() or relatorio.stat().st_size > LIMITE_RELATORIO:
            raise OSError("o gitleaks não terminou")
        achados = json.loads(relatorio.read_text(encoding="utf-8") or "[]")
    if not isinstance(achados, list):
        raise OSError("relatório do gitleaks estranho")
    return sorted(str(a.get("RuleID", "?"))[:60] for a in achados if isinstance(a, dict))


def _gitleaks(ambiente=os.environ, executar=subprocess.run):
    executavel = caminho_gitleaks(ambiente)
    if executavel is None:
        return ATENCAO, "gitleaks não encontrado (instale ou configure JARVIS_GITLEAKS)"
    tipos = rodar_gitleaks(executavel, executar=executar)
    if not tipos:
        return OK, "nenhum segredo no histórico do Git"
    contagem = {t: tipos.count(t) for t in sorted(set(tipos))}
    detalhe = ", ".join(f"{n}× {t}" for t, n in contagem.items())
    return ALERTA, f"{len(tipos)} possível(is) segredo(s) no histórico do Git ({detalhe}): rode o gitleaks e troque o segredo"


VIGIAS = defensor.VIGIAS + [("Gitleaks", _gitleaks)]


def contar_semana(pasta, agora):
    """Quantas rondas, incidentes e duelos nos últimos 7 dias (pelo nome das notas: começam com a data)."""
    contagem = {"rondas": 0, "incidentes": 0, "duelos": 0}
    if pasta is None:
        return None
    limite = (agora - timedelta(days=7)).strftime("%Y-%m-%d")
    try:
        nomes = sorted(p.name for p in Path(pasta).iterdir() if p.is_file() and not p.is_symlink())[-MAXIMO_NOTAS:]
    except OSError:
        return None
    for nome in nomes:
        if nome[:10] < limite or not nome.endswith(".md"):
            continue
        if nome.endswith("-ronda.md"):
            contagem["rondas"] += 1
        elif "duelo" in nome:
            contagem["duelos"] += 1
        elif "relatorio-seguranca" not in nome:
            contagem["incidentes"] += 1
    return contagem


def _limpo(texto):
    return str(texto).replace("|", " ").replace("`", "'").replace("<", " ").replace(">", " ")


def nota_markdown(resultados, semana, agora):
    ano, numero, _ = agora.isocalendar()
    linhas = [f"# 📋 Relatório semanal de segurança — {ano}, semana {numero:02d}",
              f"_Gerado pelo Jarvis em {agora:%d/%m/%Y %H:%M} (Ticket 084). Só leitura: nada foi fechado, "
              "bloqueado ou apagado._", "",
              f"**{defensor.frase_geral(resultados)}**", "",
              "| | Vigia | O que viu |", "|---|---|---|"]
    linhas += [f"| {ICONE[r.nivel]} | {r.vigia} | {_limpo(r.frase)} |" for r in resultados]
    linhas += ["", "## A semana em números"]
    if semana is None:
        linhas.append("- (pasta de incidentes não configurada: sem contagem)")
    else:
        linhas += [f"- Rondas do defensor: {semana['rondas']}",
                   f"- Notas de incidente: {semana['incidentes']}",
                   f"- Duelos Hacker × Sentinela: {semana['duelos']}"]
    alertas = [r for r in resultados if r.nivel in (ALERTA, ATENCAO)]
    linhas += ["", "## O que eu fiz a respeito"]
    linhas += [f"- [ ] {r.vigia}: " for r in alertas] or ["- [x] Nada pendente nesta semana."]
    linhas += ["", "## Para estudar (perguntas de entrevista)",
               "- Qual destes vigias detectaria um malware que se instala para voltar a cada reinício? Qual técnica do MITRE?",
               "- Por que o relatório guarda só o TIPO do segredo achado pelo gitleaks, e não o valor?", ""]
    return "\n".join(linhas)


def gravar(pasta, texto, agora):
    """Nome gerado aqui. Já existe um igual? Põe -2, -3... (nunca sobrescreve)."""
    base = f"{agora:%Y-%m-%d}-relatorio-seguranca"
    for n in range(1, 50):
        nota = Path(pasta) / (base + ("" if n == 1 else f"-{n}") + ".md")
        try:
            with open(nota, "x", encoding="utf-8") as f:
                f.write(texto)
            return nota
        except FileExistsError:
            continue
    raise OSError("muitas notas no mesmo dia")


def main(vigias=None, pasta="ler-do-env", agora=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    agora = agora or datetime.now()
    print("📋 Relatório semanal de segurança (uns 20 segundos)...")
    resultados = defensor.ronda(VIGIAS if vigias is None else vigias)
    for r in resultados:
        print(f"{ICONE[r.nivel]} {r.vigia}: {r.frase}")
    print()
    print(defensor.frase_geral(resultados))
    if pasta == "ler-do-env":
        try:
            import cortador
            pasta = cortador.ler_pasta("JARVIS_PASTA_INCIDENTES")
        except Exception:  # noqa: BLE001
            pasta = None
    if pasta is None:
        print("(sem JARVIS_PASTA_INCIDENTES: a nota não foi gravada no cofre)")
    else:
        try:
            nota = gravar(pasta, nota_markdown(resultados, contar_semana(pasta, agora), agora), agora)
            print(f"🗂️ Nota no cofre: {nota.name}")
        except OSError:
            print("(não consegui gravar a nota do relatório)")
    return {OK: 0, defensor.FALHA: 0, ATENCAO: 0, ALERTA: 1}[defensor.nota_geral(resultados)]


if __name__ == "__main__":
    sys.exit(main())
