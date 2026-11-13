"""
Jarvis 001 — Caderno de Incidentes 🗂️ (Ticket 031: mini SOC para treinar)

Cada alerta da Sentinela vira uma nota no cofre, com as perguntas de um analista de SOC:
  é ataque ou só incidente? · qual pilar (C, I ou D)? · quem seria o ator? · o que fazer?
O José responde PRIMEIRO. O gabarito fica dobrado no fim da nota.

  python incidentes.py
      → faz a ronda (portas + travas) e cria uma nota para cada alerta de hoje
  python incidentes.py novo "Popup dizendo que o antivírus venceu"
      → anota um caso que você viu (sem gabarito: a 001 corrige depois)

Regras:
- Só LÊ a Sentinela e só ESCREVE notas novas na pasta JARVIS_PASTA_INCIDENTES (.env).
- Nunca sobrescreve: mesma nota no mesmo dia → pula.
- O que vem da API é limpo antes de ir para a nota; o gabarito é texto fixo daqui.
"""

import argparse
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

from bracos import Bracos, ErroBraco

MAXIMO_POR_RONDA = 8
RISCOS_QUE_VIRAM_NOTA = ("alto", "atencao")


class ErroIncidente(Exception):
    pass


@dataclass
class Alerta:
    slug: str                 # vira o nome do arquivo (só a-z, 0-9 e hífen)
    titulo: str
    fatos: list = field(default_factory=list)
    gabarito: list = field(default_factory=list)   # vazio = caso manual


GABARITO_PORTA = [
    "**Ataque ou incidente?** Incidente (uma exposição). Ninguém explorou ainda: não é ataque.",
    "**Pilar:** Confidencialidade em risco (alguém da rede pode alcançar o serviço). Dependendo do serviço, também Disponibilidade.",
    "**Ator provável:** nenhum confirmado. Quem acha porta aberta costuma ser cibercriminoso oportunista varrendo a rede.",
    "**MITRE ATT&CK:** T1046 (Network Service Discovery): é a técnica que acharia essa porta.",
    "**O que fazer:** descobrir o programa dono (`resmon` como admin → Rede → Portas de Escuta). Se você não usa, fechar o programa ou bloquear no firewall.",
]
GABARITO_API = [
    "**Ataque ou incidente?** Incidente de configuração (uma vulnerabilidade aberta), não um ataque.",
    "**Pilar:** Confidencialidade (a API lê as tuas notas) e Integridade (ela escreve no daily).",
    "**Ator provável:** qualquer aparelho da mesma rede (Wi-Fi de casa, rede pública).",
    "**MITRE ATT&CK:** T1190 (Exploit Public-Facing Application): serviço exposto que não devia estar.",
    "**O que fazer:** desligar a API e conferir `server.address=127.0.0.1`. Só religar depois de o teste de porta passar.",
]
GABARITO_TRAVA = [
    "**Ataque ou incidente?** Depende de QUEM mudou. Foi você (edição ou commit)? Então é só um evento normal. Não foi? Trate como possível ataque.",
    "**Pilar:** Integridade (um arquivo de segurança mudou sem aviso).",
    "**Ator provável:** você mesmo (o mais comum), um malware, ou uma IA enganada por prompt injection.",
    "**MITRE ATT&CK:** T1565.001 (Stored Data Manipulation).",
    "**O que fazer:** `git status` e `git diff` no arquivo. Não reconhece a mudança? `git log` para ver o último commit bom e restaurar.",
]

PERGUNTAS = [
    "1. **É ataque ou só incidente?** Por quê?",
    "2. **Qual pilar** está em risco: Confidencialidade, Integridade ou Disponibilidade?",
    "3. **Quem seria o ator provável?** (insider, cibercriminoso, hacktivista, Estado, ninguém ainda)",
    "4. **O que você faria agora?** (1 ação concreta)",
]


def limpo(texto, limite=160):
    """Texto de fora (API ou digitado) → uma linha sem marcação ativa de Markdown/HTML."""
    texto = "".join(c if c.isprintable() else " " for c in str(texto))
    for c in "|[]`*<>#\\\"!{}$%":
        texto = texto.replace(c, " ")
    return " ".join(texto.split())[:limite]


def slug(texto):
    """'Popup do antivírus!' → 'popup-do-antivirus' (nunca vira caminho: só a-z, 0-9 e hífen)."""
    sem_acento = unicodedata.normalize("NFD", str(texto))
    sem_acento = "".join(c for c in sem_acento if unicodedata.category(c) != "Mn").lower()
    return re.sub(r"[^a-z0-9]+", "-", sem_acento).strip("-")[:40].strip("-")


def coletar(bracos):
    """Pergunta à Sentinela e devolve os alertas de agora. Braço fora do ar não derruba o outro."""
    alertas, falhas = [], []
    try:
        alertas += _das_portas(bracos.portas())
    except ErroBraco as e:
        if getattr(e, "alarme", False):
            raise
        falhas.append("portas")
    try:
        alertas += _das_travas(bracos.integridade())
    except ErroBraco as e:
        if getattr(e, "alarme", False):
            raise
        falhas.append("travas")
    return alertas[:MAXIMO_POR_RONDA], falhas


def _das_portas(dados):
    alertas = []
    if not isinstance(dados, dict):
        return alertas
    if dados.get("jarvisSoNoPc") is not True:
        alertas.append(Alerta("api-do-jarvis-aberta-para-a-rede", "A API do Jarvis está aberta para a rede",
                              ["A Sentinela viu a porta da API fora do 127.0.0.1."], GABARITO_API))
    for p in dados.get("portas") or []:
        try:
            numero = int(p.get("porta"))
            risco = str(p.get("risco", ""))
        except (TypeError, ValueError, AttributeError):
            continue
        if risco not in RISCOS_QUE_VIRAM_NOTA or not 0 < numero < 65536:
            continue
        if any(a.slug == "api-do-jarvis-aberta-para-a-rede" for a in alertas) and risco == "alto":
            continue  # a porta do próprio Jarvis já virou o alerta de cima
        alertas.append(Alerta(f"porta-{numero}-aberta", f"Porta {numero} aberta para a rede",
                              [f"Porta: {numero}", f"Alcance: {limpo(p.get('alcance', '?'), 40)}",
                               f"Nota da Sentinela: {limpo(p.get('nota', '?'))}"], GABARITO_PORTA))
    return alertas


def _das_travas(dados):
    if not isinstance(dados, dict):
        return []
    mudados = []
    for a in dados.get("arquivos") or []:
        try:
            estado = str(a.get("estado", ""))
            if estado and estado != "igual":
                mudados.append(f"`{limpo(a.get('nome', '?'), 80)}`: {limpo(estado, 20)}")
        except AttributeError:
            continue
    if not mudados:
        return []
    return [Alerta("travas-do-projeto-mudaram", "Travas do projeto mudaram desde que a API ligou",
                   mudados[:20], GABARITO_TRAVA)]


def nota_markdown(alerta, agora):
    linhas = [f"# 🗂️ Incidente: {limpo(alerta.titulo, 80)}", "",
              f"_Aberto pelo Jarvis em {agora:%d/%m/%Y às %H:%M} (Ticket 031). Classifique ANTES de abrir o gabarito._", "",
              "## O que aconteceu"]
    linhas += [f"- {f}" for f in alerta.fatos] or ["- (sem detalhes)"]
    linhas += ["", "## Sua classificação"]
    for pergunta in PERGUNTAS:
        linhas += [pergunta, "   - ", ""]
    if alerta.gabarito:
        linhas += ["> [!success]- Gabarito (só abra depois de responder)"]
        linhas += [f"> - {g}" for g in alerta.gabarito]
    else:
        linhas += ["> [!question] Sem gabarito: caso anotado por você. Mostre para a 001 corrigir."]
    linhas += ["", "## Como terminou", "- [ ] Resolvido · o que eu fiz: ", ""]
    return "\n".join(linhas)


def gravar(pasta, alertas, agora=None):
    """Uma nota por alerta. Devolve (criadas, puladas). Nunca sobrescreve."""
    agora = agora or datetime.now()
    criadas, puladas = [], []
    for alerta in alertas:
        nome = slug(alerta.slug)
        if not nome:
            raise ErroIncidente("O título precisa ter pelo menos uma letra ou número.")
        destino = Path(pasta) / f"{agora:%Y-%m-%d}-{nome}.md"
        try:
            with open(destino, "x", encoding="utf-8") as f:
                f.write(nota_markdown(alerta, agora))
            criadas.append(destino.name)
        except FileExistsError:
            puladas.append(destino.name)
    return criadas, puladas


def _pasta():
    import cortador  # só para ler UMA linha do .env (a mesma regra dos cortes)
    pasta = cortador.ler_pasta("JARVIS_PASTA_INCIDENTES")
    if pasta is None:
        raise ErroIncidente("Falta configurar no .env (pasta que exista): JARVIS_PASTA_INCIDENTES")
    return pasta


def main(argv=None, bracos=None, pasta=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(prog="incidentes.py", description="Caderno de Incidentes (Ticket 031)")
    sub = p.add_subparsers(dest="cmd")
    n = sub.add_parser("novo", help="anota um caso que você viu")
    n.add_argument("titulo")
    a = p.parse_args(argv)
    try:
        pasta = pasta or _pasta()
        if a.cmd == "novo":
            titulo = limpo(a.titulo, 80)
            alertas, falhas = [Alerta(slug(titulo), titulo, ["Caso anotado pelo José. Descreva aqui o que você viu."])], []
        else:
            alertas, falhas = coletar(bracos or Bracos())
        criadas, puladas = gravar(pasta, alertas)
    except (ErroIncidente, ErroBraco) as e:
        print(f"❌ {e}")
        return 1
    for f in falhas:
        print(f"⚠️ Não consegui ler: {f} (a API está ligada?)")
    for nome in criadas:
        print(f"🗂️ Nota nova: {nome}")
    for nome in puladas:
        print(f"   (já anotado hoje: {nome})")
    if not criadas and not puladas and not falhas:
        print("✅ Ronda feita: nenhum alerta da Sentinela agora.")
    elif criadas:
        print("👉 Abra a nota no Obsidian e classifique ANTES de olhar o gabarito.")
    return 1 if falhas and not criadas and not puladas else 0


if __name__ == "__main__":
    sys.exit(main())
