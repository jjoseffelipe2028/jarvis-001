"""raio-x-video — bloco 1: detector de cortes.

Acha onde cada cena muda num vídeo e mede o ritmo da edição.
Usa só ffmpeg/ffprobe (já instalados) e a biblioteca padrão do Python.

Uso:
    python cortes.py VIDEO.mp4 [--limiar 0.3] [--modo normal|jogo] [--saida pasta]

--modo jogo: para gameplay com câmera do streamer/legenda fixas na tela (os cortes mudam só parte da imagem).

Saída: cortes.json na pasta de saída + resumo no terminal.
Segurança: o ffmpeg é chamado com lista de argumentos (sem shell) e só lê o vídeo.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import statistics
import subprocess
import sys
from pathlib import Path

CENA_MINIMA = 0.25  # segundos: dois cortes mais perto que isso viram um só (flash, piscada)


def precisa(programa: str) -> str:
    caminho = shutil.which(programa)
    if not caminho:
        sys.exit(f"Falta o {programa}. Instale pelo winget (Gyan.FFmpeg).")
    return caminho


def duracao(video: Path) -> float:
    saida = subprocess.run(
        [precisa("ffprobe"), "-v", "error", "-show_entries", "format=duration",
         "-of", "default=nw=1:nk=1", str(video)],
        capture_output=True, text=True, check=True,
    )
    return float(saida.stdout.strip())


def notas_de_mudanca(video: Path) -> list[tuple[float, float]]:
    """(tempo, nota 0-1 de quanto a imagem mudou do quadro anterior) para TODOS os quadros."""
    saida = subprocess.run(
        [precisa("ffmpeg"), "-hide_banner", "-nostats", "-i", str(video), "-an",
         "-vf", "select='gte(scene,0)',metadata=print:file=-", "-f", "null", "-"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if saida.returncode != 0:
        sys.exit("O ffmpeg não conseguiu ler o vídeo:\n" + saida.stderr[-800:])
    notas, tempo = [], None
    for linha in saida.stdout.splitlines():
        if m := re.search(r"pts_time:([\d.]+)", linha):
            tempo = float(m.group(1))
        elif (m := re.search(r"scene_score=([\d.]+)", linha)) and tempo is not None:
            notas.append((tempo, float(m.group(1))))
    return notas


def cortes_modo_jogo(notas: list[tuple[float, float]], minimo: float = 0.1) -> list[float]:
    """Vídeo de jogo com câmera/legenda fixas: o corte muda só PARTE da tela (nota 0.1-0.2).
    Corte = pico isolado: bem maior que o normal dos vizinhos (±0,5 s) e o maior por perto."""
    valores = [n for _, n in notas]
    achados = []
    for i, (t, n) in enumerate(notas):
        if n < minimo or i == 0:
            continue
        perto = valores[max(0, i - 3) : i + 4]
        if n < max(perto):
            continue
        vizinhos = valores[max(0, i - 15) : max(0, i - 1)] + valores[i + 2 : i + 16]
        normal = statistics.median(vizinhos) if vizinhos else 0.0
        if n >= 6 * max(normal, 0.005):
            achados.append(t)
    return achados


def achar_cortes(video: Path, limiar: float, modo: str = "normal") -> list[float]:
    """normal: corte = imagem muda mais que o limiar (0 a 1). jogo: picos isolados (ver acima)."""
    notas = notas_de_mudanca(video)
    if modo == "jogo":
        tempos = cortes_modo_jogo(notas)
    else:
        tempos = [t for t, n in notas if n > limiar]
    limpos: list[float] = []
    for t in sorted(tempos):
        if not limpos or t - limpos[-1] >= CENA_MINIMA:
            limpos.append(round(t, 3))
    return limpos


def montar_cenas(cortes: list[float], total: float) -> list[dict]:
    marcos = [0.0] + [c for c in cortes if 0 < c < total] + [total]
    return [
        {"n": i + 1, "inicio": round(a, 3), "fim": round(b, 3), "dura": round(b - a, 3)}
        for i, (a, b) in enumerate(zip(marcos, marcos[1:]))
        if b - a > 0
    ]


def ritmo(media: float) -> str:
    if media < 1.5:
        return "frenético (estilo Short viral)"
    if media < 3:
        return "rápido"
    if media < 6:
        return "médio"
    return "calmo (cenas longas)"


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")  # terminal do Windows (cp1252) não mostra emoji
    p = argparse.ArgumentParser(description="Detector de cortes do raio-x-video")
    p.add_argument("video", type=Path)
    p.add_argument("--limiar", type=float, default=0.3,
                   help="0.2 pega mais cortes, 0.4 só os bem fortes (padrão 0.3)")
    p.add_argument("--modo", choices=["normal", "jogo"], default="normal",
                   help="jogo: gameplay com câmera/legenda fixas (acha picos isolados)")
    p.add_argument("--saida", type=Path, default=None)
    args = p.parse_args()

    video = args.video.resolve()
    if not video.is_file():
        sys.exit(f"Vídeo não encontrado: {video}")
    if not 0.05 <= args.limiar <= 0.9:
        sys.exit("Use um limiar entre 0.05 e 0.9.")
    pasta = (args.saida or video.parent / f"raio-x_{video.stem}").resolve()
    pasta.mkdir(parents=True, exist_ok=True)

    total = duracao(video)
    cenas = montar_cenas(achar_cortes(video, args.limiar, args.modo), total)
    duras = [c["dura"] for c in cenas]
    media = statistics.mean(duras)
    resumo = {
        "video": video.name,
        "duracao_s": round(total, 2),
        "cenas": len(cenas),
        "cortes": len(cenas) - 1,
        "cortes_por_minuto": round((len(cenas) - 1) / total * 60, 1) if total else 0,
        "cena_media_s": round(media, 2),
        "cena_mais_curta_s": min(duras),
        "cena_mais_longa_s": max(duras),
        "primeiro_corte_s": cenas[1]["inicio"] if len(cenas) > 1 else None,
        "ritmo": ritmo(media),
        "limiar": args.limiar,
        "modo": args.modo,
    }
    (pasta / "cortes.json").write_text(
        json.dumps({"resumo": resumo, "cenas": cenas}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"🎬 {video.name} — {resumo['duracao_s']} s")
    print(f"✂️  {resumo['cortes']} cortes ({resumo['cortes_por_minuto']}/min), "
          f"cena média {resumo['cena_media_s']} s → {resumo['ritmo']}")
    if resumo["primeiro_corte_s"] is None:
        print("⏱️  Nenhum corte: plano único (ou tente --limiar 0.2; gameplay: --modo jogo)")
    else:
        print(f"⏱️  Primeiro corte em {resumo['primeiro_corte_s']} s "
              f"(gancho: o que aparece antes disso?)")
    for c in cenas[:40]:
        print(f"   {c['n']:>3}. {c['inicio']:>7.2f} → {c['fim']:>7.2f}  ({c['dura']:.2f} s)")
    if len(cenas) > 40:
        print(f"   … mais {len(cenas) - 40} cenas no cortes.json")
    print(f"📁 {pasta / 'cortes.json'}")


if __name__ == "__main__":
    main()
