"""
Jarvis 001 — Red Team dos cortes 🟥 (Ticket 036)

Gera pedidos MALICIOSOS de corte e confere se o porteiro barra todos.
Serve para o porteiro em Python (cortador.py) e, pelo arquivo JSON, para o PorteiroDeCortes.java.

  python redteam_cortes.py            → ataca o porteiro Python e mostra o placar
  python redteam_cortes.py --json     → só imprime a lista de pedidos (para testar o Java)

Contrato de cada pedido (o mesmo do record PedidoDeCorte + o nome do vídeo):
  {"video": "live.mp4", "inicioSeg": 10, "fimSeg": 40, "duracaoVideoSeg": 3600, "arroba": "@amigo"}

Regras:
- Nada é executado: os pedidos são só DADOS. Nenhum FFmpeg roda, nenhum arquivo de verdade é tocado.
- O teste usa uma pasta temporária com um vídeo falso de 1 byte.
"""

import json
import sys
import tempfile
from pathlib import Path

DURACAO = 3600
BOM = {"video": "live.mp4", "inicioSeg": 100, "fimSeg": 130, "duracaoVideoSeg": DURACAO, "arroba": "@amigo"}


def _pedido(**troca):
    return dict(BOM, **troca)


def gerar():
    """Lista de casos: id, categoria, técnica (MITRE), o pedido e o que o porteiro deve fazer."""
    casos = []

    def caso(categoria, tecnica, esperado="barrar", **troca):
        casos.append({"id": len(casos) + 1, "categoria": categoria, "tecnica": tecnica,
                      "esperado": esperado, "pedido": _pedido(**troca)})

    # Pedidos normais: o porteiro NÃO pode barrar (senão ele não serve)
    caso("normal", "-", "liberar")
    caso("normal", "-", "liberar", inicioSeg=0, fimSeg=13)
    caso("normal", "-", "liberar", inicioSeg=DURACAO - 180, fimSeg=DURACAO, arroba="amigo.gta_01")

    # Fugir da pasta pelo nome do vídeo
    fuga = "T1083 File and Directory Discovery"
    for nome in ("../live.mp4", "..\\live.mp4", "../../Windows/win.ini", "C:/Windows/System32/cmd.exe",
                 "C:\\Users\\Public\\x.mp4", "/etc/passwd", "pasta/live.mp4", "live.mp4/..", "....//live.mp4",
                 "%2e%2e%2flive.mp4", "\\\\servidor\\compartilhado\\live.mp4", "live.mp4:fluxo", "", ".", ".."):
        caso("fuga-de-pasta", fuga, video=nome)

    # Enfiar argumento ou comando no FFmpeg
    injecao = "T1059 Command and Scripting Interpreter"
    for nome in ("-i live.mp4", "-y", "live.mp4; rm -rf .", "live.mp4 && calc.exe", "live.mp4 | whoami",
                 "$(whoami).mp4", "`whoami`.mp4", "live.mp4\n-f null", "concat:live.mp4|segredo.txt",
                 "file:live.mp4", "http://exemplo.invalid/live.mp4", "pipe:0", "live.exe", "live.mp4.bat", "live"):
        caso("injecao-no-ffmpeg", injecao, video=nome)
    for arroba in ("@amigo'; drawtext=textfile=/etc/passwd", "@a:b", "@a,b", "@a\\b", "@a b", "@a%{pts}",
                   "@" + "a" * 31, "", "@", "@amigo\n@outro", "<script>", "@amígo"):
        caso("injecao-no-filtro", injecao, arroba=arroba)

    # Números impossíveis
    numeros = "T1499 Endpoint Denial of Service"
    caso("numeros", numeros, inicioSeg=-1)
    caso("numeros", numeros, inicioSeg=130, fimSeg=100)            # invertido
    caso("numeros", numeros, inicioSeg=100, fimSeg=100)            # duração zero
    caso("numeros", numeros, inicioSeg=100, fimSeg=112)            # 12 s: abaixo do mínimo
    caso("numeros", numeros, inicioSeg=100, fimSeg=281)            # 181 s: acima do máximo
    caso("numeros", numeros, inicioSeg=DURACAO - 10, fimSeg=DURACAO + 20)  # passa do fim do vídeo
    caso("numeros", numeros, inicioSeg=0, fimSeg=2**63)            # número gigante
    caso("numeros", numeros, duracaoVideoSeg=0)
    caso("numeros", numeros, duracaoVideoSeg=-3600)

    # Tipo errado no lugar do número
    tipos = "T1565 Data Manipulation"
    for errado in ("100", None, 10.5, True, [100], {"n": 100}, "1e3", float("nan")):
        caso("tipo-errado", tipos, inicioSeg=errado)
    caso("tipo-errado", tipos, fimSeg="130; DROP")
    caso("tipo-errado", tipos, arroba=None)
    caso("tipo-errado", tipos, arroba=123)
    caso("tipo-errado", tipos, video=None)
    return casos


def porteiro_python(pedido, pasta):
    """Passa UM pedido pelo porteiro de verdade do cortador.py. Devolve (liberou, motivo)."""
    import cortador
    try:
        for campo in ("inicioSeg", "fimSeg", "duracaoVideoSeg"):
            valor = pedido.get(campo)
            if isinstance(valor, bool) or not isinstance(valor, int):
                return False, f"{campo} não é número inteiro"
        cortador.resolver_entrada(pasta, pedido.get("video"))
        ok, motivo = cortador.liberar(pedido["inicioSeg"], pedido["fimSeg"], pedido["duracaoVideoSeg"],
                                      pedido.get("arroba"))
        return ok, motivo
    except (cortador.ErroCorte, TypeError, ValueError, OSError) as e:
        return False, f"recusado: {type(e).__name__}"


def atacar(porteiro, casos=None):
    """Roda todos os casos. Devolve os que deram ERRADO (ataque que passou ou pedido bom barrado)."""
    falhas = []
    for c in casos if casos is not None else gerar():
        liberou, motivo = porteiro(c["pedido"])
        if liberou != (c["esperado"] == "liberar"):
            falhas.append({"id": c["id"], "categoria": c["categoria"], "esperado": c["esperado"], "motivo": str(motivo)})
    return falhas


def main(argv=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    argv = sys.argv[1:] if argv is None else argv
    casos = gerar()
    if "--json" in argv:
        print(json.dumps(casos, ensure_ascii=False, indent=1, default=str))
        return 0
    with tempfile.TemporaryDirectory(prefix="jarvis-redteam-") as tmp:
        Path(tmp, "live.mp4").write_bytes(b"0")  # vídeo falso: nada é cortado de verdade
        falhas = atacar(lambda pedido: porteiro_python(pedido, Path(tmp)), casos)
    ataques = sum(1 for c in casos if c["esperado"] == "barrar")
    print(f"🟥 Red Team dos cortes: {ataques} ataques e {len(casos) - ataques} pedidos normais.")
    if not falhas:
        print(f"🛡️ O porteiro barrou os {ataques} ataques e liberou os pedidos normais.")
        return 0
    for f in falhas:
        print(f"🔴 caso {f['id']} ({f['categoria']}): esperado {f['esperado']} → {f['motivo']}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
