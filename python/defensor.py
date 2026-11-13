"""
Jarvis 001 — Super defensor 🛡️ (Ticket 044: a ronda completa num comando só)

Junta todos os vigias do Jarvis numa ronda e dá uma nota geral:
  Sentinela (portas, travas e isca) · processos (minerador escondido) · rede (aparelho novo)
  · backup 3-2-1 · placar do último duelo.

  python defensor.py

Regras:
- Só LÊ. Não fecha programa, não bloqueia aparelho, não apaga nada. Quem age é o José.
- Cada vigia roda sozinho: se um falhar, os outros continuam e a falha aparece na lista.
- A nota geral é a do PIOR vigia (um alerta não some atrás de quatro "tudo certo").
- Se JARVIS_PASTA_INCIDENTES existir, a ronda vira uma nota no cofre (nunca sobrescreve).
"""

import sys
from dataclasses import dataclass
from datetime import datetime

OK, ATENCAO, ALERTA, FALHA = "ok", "atencao", "alerta", "falha"
PESO = {OK: 0, FALHA: 1, ATENCAO: 2, ALERTA: 3}
ICONE = {OK: "✅", ATENCAO: "⚠️", ALERTA: "🚨", FALHA: "❔"}


@dataclass
class Resultado:
    vigia: str
    nivel: str
    frase: str


def ronda(vigias):
    """`vigias`: lista de (nome, função). Cada função devolve (nível, frase). Erro vira "falha", nunca derruba."""
    resultados = []
    for nome, funcao in vigias:
        try:
            nivel, texto = funcao()
            if nivel not in PESO or not isinstance(texto, str) or not texto.strip():
                raise ValueError("resposta estranha")
            resultados.append(Resultado(nome, nivel, " ".join(texto.split())[:300]))
        except Exception:  # noqa: BLE001 - um vigia quebrado não pode esconder os outros
            resultados.append(Resultado(nome, FALHA, "não consegui conferir agora"))
    return resultados


def nota_geral(resultados):
    if not resultados:
        return FALHA
    return max((r.nivel for r in resultados), key=lambda n: PESO[n])


def frase_geral(resultados):
    geral = nota_geral(resultados)
    alertas = [r.vigia for r in resultados if r.nivel == ALERTA]
    atencoes = [r.vigia for r in resultados if r.nivel == ATENCAO]
    falhas = [r.vigia for r in resultados if r.nivel == FALHA]
    if geral == ALERTA:
        texto = "Defensor: ALERTA em " + ", ".join(alertas) + ". Veja isso antes de tudo."
    elif geral == ATENCAO:
        texto = "Defensor: sem alerta, mas com atenção em " + ", ".join(atencoes) + "."
    elif geral == FALHA:
        texto = "Defensor: nenhum alerta visto."
    else:
        texto = "Defensor: tudo certo na ronda."
    if falhas:
        texto += " Não deu para conferir: " + ", ".join(falhas) + "."
    return texto


def nota_markdown(resultados, agora):
    linhas = [f"# 🛡️ Ronda do defensor ({agora:%d/%m/%Y %H:%M})", "",
              "_Feita pelo Jarvis (Ticket 044). Só leitura: nada foi fechado, bloqueado ou apagado._", "",
              f"**{frase_geral(resultados)}**", "", "| | Vigia | O que viu |", "|---|---|---|"]
    for r in resultados:
        frase = r.frase.replace("|", " ").replace("`", "'").replace("<", " ").replace(">", " ")
        linhas.append(f"| {ICONE[r.nivel]} | {r.vigia} | {frase} |")
    linhas += ["", "## O que eu fiz a respeito", "- [ ] ", ""]
    return "\n".join(linhas)


# ---------- os vigias de verdade ----------

def _sentinela():
    import rotinas
    from bracos import Bracos
    b = Bracos()
    frase = rotinas._sentinela(rotinas._tentar(b.portas), rotinas._tentar(b.integridade), rotinas._tentar(b.isca))
    if frase is None:
        raise OSError("API desligada")
    return (ALERTA if frase.startswith("Sentinela em alerta") else OK), frase


def _processos():
    import vigia_processos
    usos = vigia_processos.medir(5)
    niveis = {u.nivel for u in usos}
    nivel = ALERTA if "suspeito" in niveis else ATENCAO if "pesado" in niveis else OK
    return nivel, vigia_processos.frase(usos)


def _rede():
    import vigia_rede
    aparelhos = vigia_rede.olhar()
    conhecidos = vigia_rede.ler_conhecidos()
    novos, _ = vigia_rede.separar(aparelhos, conhecidos)
    nivel = ATENCAO if (novos and conhecidos) or not conhecidos else OK
    return nivel, vigia_rede.frase(novos, len(aparelhos), not conhecidos)


def _backup():
    import backup321
    import cortador
    import resumo_local
    itens = backup321.avaliar(
        backup321.cofre_na_nuvem(cortador.ler_pasta("JARVIS_PASTA_DAILY")),
        backup321.estado_do_git(cortador.ler_pasta("JARVIS_PASTA_GIT")),
        backup321.idade_da_copia(cortador.ler_pasta("JARVIS_BACKUP_OFFLINE")))
    return (OK if all(i.ok for i in itens) else ATENCAO), resumo_local.frase_do_backup(itens)


def _duelo():
    import cortador
    import resumo_local
    frase = resumo_local.frase_do_duelo(cortador.ler_pasta("JARVIS_PASTA_INCIDENTES"))
    if frase is None:
        return ATENCAO, "Ainda não tem duelo registrado. Rode: python duelo.py"
    return (ATENCAO if "escap" in frase else OK), frase


VIGIAS = [("Sentinela", _sentinela), ("Processos", _processos), ("Rede", _rede),
          ("Backup", _backup), ("Duelo", _duelo)]


def main(vigias=None, pasta_da_nota="ler-do-env", agora=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    print("🛡️ Ronda do defensor (uns 15 segundos)...")
    resultados = ronda(vigias if vigias is not None else VIGIAS)
    for r in resultados:
        print(f"{ICONE[r.nivel]} {r.vigia}: {r.frase}")
    print()
    print(frase_geral(resultados))
    if pasta_da_nota == "ler-do-env":
        try:
            import cortador
            pasta_da_nota = cortador.ler_pasta("JARVIS_PASTA_INCIDENTES")
        except Exception:  # noqa: BLE001
            pasta_da_nota = None
    if pasta_da_nota is not None:
        agora = agora or datetime.now()
        nota = pasta_da_nota / f"{agora:%Y-%m-%d-%H%M%S}-ronda.md"
        try:
            with open(nota, "x", encoding="utf-8") as f:
                f.write(nota_markdown(resultados, agora))
            print(f"🗂️ Nota no cofre: {nota.name}")
        except OSError:
            print("(não consegui gravar a nota da ronda)")
    return {OK: 0, FALHA: 0, ATENCAO: 0, ALERTA: 1}[nota_geral(resultados)]


if __name__ == "__main__":
    sys.exit(main())
