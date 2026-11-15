"""
Jarvis 001 — Vigia da inicialização 🚦 (Ticket 083: o que abre junto com o Windows?)

Lista os programas que ligam sozinhos quando o Windows abre e avisa o que é NOVO ou MUDOU desde a última vez.
Malware adora se esconder aqui para voltar a cada reinício (MITRE ATT&CK T1547.001: Run Keys / Startup Folder).

  python vigia_inicio.py             → mostra os itens e marca os novos e os que mudaram
  python vigia_inicio.py --lembrar   → guarda os de agora como "conhecidos"

Regras:
- SÓ LÊ. Abre o registro com KEY_READ (só leitura) e só LISTA os nomes da pasta Inicializar.
  Nunca desativa, apaga, cria ou altera nada; nunca roda o programa listado; não usa shell nem comando externo.
- Os conhecidos ficam num arquivo só deste PC (`.inicio-conhecido.json`, fora do Git), e guardam só o NOME do item
  e um RESUMO (hash) do comando: o caminho, que mostra o seu usuário do Windows, não é gravado.
- Quem decide o que fazer é o José (ex.: desativar no Gerenciador de Tarefas → Aplicativos de inicialização).
"""

import hashlib
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

ARQUIVO = Path(__file__).resolve().parent / ".inicio-conhecido.json"
MAXIMO = 500
_RUN = r"Software\Microsoft\Windows\CurrentVersion\Run"
_RUN_ONCE = r"Software\Microsoft\Windows\CurrentVersion\RunOnce"
_RUN_32 = r"Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run"
# (raiz, chave) lidas — lista FIXA. HKCU = só o seu usuário; HKLM = o PC todo.
CHAVES = [("HKCU", _RUN), ("HKCU", _RUN_ONCE), ("HKLM", _RUN), ("HKLM", _RUN_ONCE), ("HKLM", _RUN_32)]
_CONTROLE = re.compile(r"[\x00-\x1f\x7f  ‪-‮⁦-⁩]")


@dataclass(frozen=True)
class Item:
    onde: str      # ex.: "HKCU Run" ou "Pasta Inicializar (usuário)"
    nome: str
    comando: str   # o que roda (só para MOSTRAR na tela; nunca é gravado nem executado)

    @property
    def chave(self):
        return f"{self.onde}|{self.nome}"

    @property
    def resumo(self):
        """Hash curto do comando: detecta troca do programa sem gravar o caminho."""
        return hashlib.sha256(self.comando.encode("utf-8", "replace")).hexdigest()[:16]


def _limpo(texto, limite=200):
    """Tira caractere de controle e truque de direção (RLO) — um nome falso não engana a tela."""
    return _CONTROLE.sub("_", str(texto))[:limite]


# ---------- leitura (só leitura) ----------

def ler_registro_windows(raiz, chave):
    """Valores de uma chave Run/RunOnce. Só leitura (KEY_READ). Chave ausente → lista vazia."""
    import winreg  # só existe no Windows; importado aqui para os testes rodarem em qualquer sistema
    base = {"HKCU": winreg.HKEY_CURRENT_USER, "HKLM": winreg.HKEY_LOCAL_MACHINE}[raiz]
    try:
        with winreg.OpenKey(base, chave, 0, winreg.KEY_READ) as k:
            valores = []
            for i in range(winreg.QueryInfoKey(k)[1]):
                if i >= MAXIMO:
                    break
                nome, dado, _tipo = winreg.EnumValue(k, i)
                valores.append((nome, dado))
            return valores
    except OSError:
        return []


def pastas_inicializar(ambiente=os.environ):
    """As 2 pastas "Inicializar": a do usuário e a de todos. Variável ausente → pasta pulada."""
    fim = Path("Microsoft", "Windows", "Start Menu", "Programs", "Startup")
    pastas = []
    if ambiente.get("APPDATA"):
        pastas.append(("Pasta Inicializar (usuário)", Path(ambiente["APPDATA"]) / fim))
    if ambiente.get("PROGRAMDATA"):
        pastas.append(("Pasta Inicializar (todos)", Path(ambiente["PROGRAMDATA"]) / fim))
    return pastas


def listar_pasta(pasta):
    """Só os NOMES dos arquivos (não abre nenhum, não segue atalho). Pasta ausente → lista vazia."""
    try:
        return sorted(p.name for p in Path(pasta).iterdir() if p.name.lower() != "desktop.ini")[:MAXIMO]
    except OSError:
        return []


def olhar(ler_registro=ler_registro_windows, pastas=None, listar=listar_pasta):
    """Junta tudo que liga com o Windows. Cada fonte que falhar é pulada (não derruba o resto)."""
    itens = []
    for raiz, chave in CHAVES:
        onde = f"{raiz} {chave.rsplit(chr(92), 1)[-1]}" + (" (32 bits)" if "WOW6432Node" in chave else "")
        try:
            valores = ler_registro(raiz, chave)
        except OSError:
            continue
        for nome, dado in valores:
            itens.append(Item(onde, _limpo(nome or "(padrão)"), _limpo(dado, 400)))
    for onde, pasta in (pastas_inicializar() if pastas is None else pastas):
        for nome in listar(pasta):
            itens.append(Item(onde, _limpo(nome), _limpo(nome)))
    return itens[:MAXIMO]


# ---------- conhecidos ----------

def ler_conhecidos(arquivo=ARQUIVO):
    """{chave: resumo}. Arquivo ausente ou torto → vazio (tudo aparece como novo)."""
    try:
        dados = json.loads(Path(arquivo).read_text(encoding="utf-8"))["conhecidos"]
        return {str(k)[:300]: v for k, v in list(dados.items())[:MAXIMO]
                if isinstance(v, str) and re.fullmatch(r"[0-9a-f]{16}", v)}
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        return {}


def guardar_conhecidos(itens, arquivo=ARQUIVO):
    """Guarda SÓ os de agora (o que sumiu sai da lista). Só nome + hash, nunca o caminho."""
    conhecidos = {i.chave: i.resumo for i in itens[:MAXIMO]}
    Path(arquivo).write_text(json.dumps({"conhecidos": conhecidos}, indent=1, ensure_ascii=False), encoding="utf-8")
    return len(conhecidos)


def comparar(itens, conhecidos):
    """→ (novos, mudaram, iguais, sumiram). "Mudou" = mesmo nome, programa diferente (truque comum de malware)."""
    novos, mudaram, iguais = [], [], []
    for i in itens:
        if i.chave not in conhecidos:
            novos.append(i)
        elif conhecidos[i.chave] != i.resumo:
            mudaram.append(i)
        else:
            iguais.append(i)
    agora = {i.chave for i in itens}
    sumiram = sorted(c for c in conhecidos if c not in agora)
    return novos, mudaram, iguais, sumiram


def frase(novos, mudaram, total, primeira_vez):
    if primeira_vez:
        return (f"Vigia da inicialização: {total} item(ns) ligam com o Windows. "
                "Ainda não tem lista de conhecidos: confira e rode com --lembrar.")
    if novos or mudaram:
        partes = []
        if novos:
            partes.append(f"{len(novos)} NOVO(S)")
        if mudaram:
            partes.append(f"{len(mudaram)} que MUDOU(ARAM) de programa")
        return f"Vigia da inicialização: {' e '.join(partes)} de {total}. Você reconhece?"
    return f"Vigia da inicialização: {total} item(ns), todos conhecidos."


def resumo(arquivo=ARQUIVO, **olhar_opcoes):
    """Para o relatório semanal (Ticket 084): só números, nada de nome ou caminho."""
    itens = olhar(**olhar_opcoes)
    conhecidos = ler_conhecidos(arquivo)
    novos, mudaram, _, sumiram = comparar(itens, conhecidos)
    return {"total": len(itens), "novos": len(novos), "mudaram": len(mudaram), "sumiram": len(sumiram),
            "temLista": bool(conhecidos)}


def main(argv=None, arquivo=ARQUIVO, **olhar_opcoes):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    argv = sys.argv[1:] if argv is None else argv
    if argv not in ([], ["--lembrar"]):
        print("Uso: python vigia_inicio.py [--lembrar]")
        return 2
    itens = olhar(**olhar_opcoes)
    conhecidos = ler_conhecidos(arquivo)
    novos, mudaram, iguais, sumiram = comparar(itens, conhecidos)
    for marca, grupo in (("🆕", novos), ("⚠️ mudou", mudaram), ("✅", iguais)):
        for i in grupo:
            print(f"{marca} [{i.onde}] {i.nome} → {i.comando[:120]}")
    for chave in sumiram:
        print(f"➖ saiu: {_limpo(chave)}")
    print()
    if argv == ["--lembrar"]:
        total = guardar_conhecidos(itens, arquivo)
        print(f"📒 Guardei. Agora são {total} item(ns) conhecido(s) neste PC.")
        return 0
    print(frase(novos, mudaram, len(itens), not conhecidos))
    if (novos or mudaram) and conhecidos:
        print("👉 Não reconhece? Pesquise o nome do programa e, se for o caso, desative você mesmo em "
              "Gerenciador de Tarefas → Aplicativos de inicialização. O Jarvis não mexe em nada.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
