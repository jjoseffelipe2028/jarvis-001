"""
Jarvis 001 — Vigia da rede 📡 (Ticket 041: quem está na minha rede?)

Lista os aparelhos que o teu PC enxergou na rede (roteador, celular, TV...) e avisa quando aparece um NOVO.
Serve em casa e em viagem (hotel, Airbnb): aparelho que você não conhece merece uma olhada.

  python vigia_rede.py             → mostra os aparelhos e marca os novos
  python vigia_rede.py --lembrar   → guarda os de agora como "conhecidos"

Regras:
- É PASSIVO: só lê a lista que o Windows já tem (`arp -a`, comando fixo, sem shell).
  Não envia nada para a rede, não varre portas e não tenta entrar em nenhum aparelho.
- Os aparelhos conhecidos ficam num arquivo só deste PC (`.rede-conhecida.json`, fora do Git).
- A lista só mostra aparelhos com que o PC falou há pouco: aparelho quieto pode não aparecer.
- Só AVISA. Técnica do MITRE que isto ajuda a perceber: T1200 (aparelho estranho na rede).
"""

import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

COMANDO = ["arp", "-a"]
ARQUIVO = Path(__file__).resolve().parent / ".rede-conhecida.json"
MAXIMO = 500
_LINHA = re.compile(r"^\s*(\d{1,3}(?:\.\d{1,3}){3})\s+((?:[0-9a-f]{2}[-:]){5}[0-9a-f]{2})\b", re.IGNORECASE)


@dataclass(frozen=True)
class Aparelho:
    ip: str
    mac: str   # minúsculo, com hífen: aa-bb-cc-dd-ee-ff


def _de_verdade(ip, mac):
    """Tira endereços que não são aparelho: transmissão para todos e grupos (multicast)."""
    primeiro = int(ip.split(".")[0])
    if any(int(p) > 255 for p in ip.split(".")):
        return False
    if primeiro >= 224 or ip.endswith(".255") or mac == "ff-ff-ff-ff-ff-ff":
        return False
    return not mac.startswith(("01-00-5e", "33-33")) and mac != "00-00-00-00-00-00"


def ler_arp(texto):
    """Saída do `arp -a` → aparelhos únicos (por MAC). Funciona em qualquer idioma do Windows."""
    vistos, aparelhos = set(), []
    for linha in str(texto).splitlines()[:5000]:
        achado = _LINHA.match(linha[:200])
        if not achado:
            continue
        ip, mac = achado.group(1), achado.group(2).lower().replace(":", "-")
        if _de_verdade(ip, mac) and mac not in vistos:
            vistos.add(mac)
            aparelhos.append(Aparelho(ip, mac))
    return aparelhos[:MAXIMO]


def ler_conhecidos(arquivo=ARQUIVO):
    """MACs guardados. Arquivo ausente ou torto → conjunto vazio (tudo aparece como novo)."""
    try:
        dados = json.loads(Path(arquivo).read_text(encoding="utf-8"))
        return {m for m in dados["conhecidos"] if isinstance(m, str) and re.fullmatch(r"(?:[0-9a-f]{2}-){5}[0-9a-f]{2}", m)}
    except (OSError, ValueError, KeyError, TypeError):
        return set()


def guardar_conhecidos(aparelhos, arquivo=ARQUIVO):
    macs = sorted(ler_conhecidos(arquivo) | {a.mac for a in aparelhos})[:MAXIMO]
    Path(arquivo).write_text(json.dumps({"conhecidos": macs}, indent=1), encoding="utf-8")
    return len(macs)


def separar(aparelhos, conhecidos):
    novos = [a for a in aparelhos if a.mac not in conhecidos]
    return novos, [a for a in aparelhos if a.mac in conhecidos]


def frase(novos, total, primeira_vez):
    if total == 0:
        return "Vigia da rede: não vi nenhum aparelho (o PC está sem rede?)."
    if primeira_vez:
        return f"Vigia da rede: {total} aparelho(s). Ainda não tem lista de conhecidos: confira e rode com --lembrar."
    if novos:
        return f"Vigia da rede: {len(novos)} aparelho(s) NOVO(S) de {total}. Você reconhece?"
    return f"Vigia da rede: {total} aparelho(s), todos conhecidos."


def olhar(executar=subprocess.run):
    r = executar(COMANDO, capture_output=True, text=True, errors="replace", timeout=20, shell=False,
                 stdin=subprocess.DEVNULL)
    if r.returncode != 0:
        raise OSError("não consegui ler a lista da rede")
    return ler_arp(r.stdout)


def main(argv=None, arquivo=ARQUIVO, executar=subprocess.run):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    argv = sys.argv[1:] if argv is None else argv
    if argv not in ([], ["--lembrar"]):
        print("Uso: python vigia_rede.py [--lembrar]")
        return 2
    try:
        aparelhos = olhar(executar)
    except (OSError, subprocess.SubprocessError) as e:
        print(f"❌ {e}")
        return 2
    conhecidos = ler_conhecidos(arquivo)
    novos, velhos = separar(aparelhos, conhecidos)
    for a in novos:
        print(f"🆕 {a.ip:<16} {a.mac}")
    for a in velhos:
        print(f"✅ {a.ip:<16} {a.mac}")
    print()
    if argv == ["--lembrar"]:
        total = guardar_conhecidos(aparelhos, arquivo)
        print(f"📒 Guardei. Agora são {total} aparelho(s) conhecido(s) neste PC.")
        return 0
    print(frase(novos, len(aparelhos), not conhecidos))
    if novos and conhecidos:
        print("👉 Não reconhece? Veja os aparelhos ligados na tela do roteador e troque a senha do Wi-Fi.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
