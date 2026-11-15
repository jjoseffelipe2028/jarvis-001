"""raio-x-video — bloco 2: uma foto de cada cena + folha de contato.

Lê o cortes.json do bloco 1, tira uma foto do MEIO de cada cena (com o número e o tempo
escritos nela) e junta tudo em folhas de 6 colunas, para a IA e o José olharem de uma vez.

Uso:
    python fotos.py VIDEO.mp4 [--saida pasta] [--largura 270]

Precisa rodar o cortes.py antes (ele cria raio-x_<nome>/cortes.json).
Saída: raio-x_<nome>/cenas/cena_001.jpg ... e folha_1.jpg, folha_2.jpg ...
Segurança: ffmpeg chamado com lista de argumentos (sem shell); só lê o vídeo.
"""
from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import sys
from pathlib import Path

COLUNAS = 6
LINHAS = 8  # 48 cenas por folha; acima disso vira folha_2, folha_3...
FONTES = [Path("C:/Windows/Fonts/arialbd.ttf"), Path("C:/Windows/Fonts/arial.ttf"),
          Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")]


def precisa(programa: str) -> str:
    caminho = shutil.which(programa)
    if not caminho:
        sys.exit(f"Falta o {programa}. Instale pelo winget (Gyan.FFmpeg).")
    return caminho


def fonte_para_ffmpeg() -> str | None:
    for f in FONTES:
        if f.is_file():
            # o drawtext usa ":" como separador; no Windows o "C:" precisa de escape
            return f.as_posix().replace(":", "\\:")
    return None


def rodar(args: list[str]) -> None:
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        sys.exit("O ffmpeg falhou:\n" + r.stderr[-800:])


def tirar_foto(video: Path, cena: dict, destino: Path, largura: int, fonte: str | None) -> None:
    meio = cena["inicio"] + cena["dura"] / 2
    filtro = f"scale={largura}:-2"
    if fonte:
        rotulo = f"{cena['n']}  {cena['inicio']:.1f}s ({cena['dura']:.1f}s)"
        filtro += (f",drawtext=fontfile='{fonte}':text='{rotulo}':x=6:y=6:fontsize={max(14, largura // 14)}"
                   ":fontcolor=white:box=1:boxcolor=black@0.6:boxborderw=4")
    rodar([precisa("ffmpeg"), "-hide_banner", "-loglevel", "error", "-y",
           "-ss", f"{meio:.3f}", "-i", str(video), "-frames:v", "1",
           "-vf", filtro, "-q:v", "3", str(destino)])


def montar_folhas(pasta_cenas: Path, total: int, pasta: Path) -> list[Path]:
    por_folha = COLUNAS * LINHAS
    for velha in pasta.glob("folha_*.jpg"):
        velha.unlink()
    folhas = []
    for f in range(math.ceil(total / por_folha)):
        inicio = f * por_folha + 1
        qtd = min(por_folha, total - f * por_folha)
        colunas = min(COLUNAS, qtd)  # vídeo com poucas cenas não sobra espaço vazio
        linhas = math.ceil(qtd / colunas)
        destino = pasta / f"folha_{f + 1}.jpg"
        rodar([precisa("ffmpeg"), "-hide_banner", "-loglevel", "error", "-y",
               "-start_number", str(inicio), "-i", str(pasta_cenas / "cena_%03d.jpg"),
               "-vf", f"tile={colunas}x{linhas}:padding=4:margin=4:color=0x202030",
               "-frames:v", "1", "-q:v", "3", str(destino)])  # 1 quadro = só as cenas desta folha
        folhas.append(destino)
    return folhas


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description="Fotos por cena do raio-x-video")
    p.add_argument("video", type=Path)
    p.add_argument("--saida", type=Path, default=None)
    p.add_argument("--largura", type=int, default=270, help="largura de cada foto em pixels (padrão 270)")
    args = p.parse_args()

    video = args.video.resolve()
    if not video.is_file():
        sys.exit(f"Vídeo não encontrado: {video}")
    if not 120 <= args.largura <= 720:
        sys.exit("Use uma largura entre 120 e 720.")
    pasta = (args.saida or video.parent / f"raio-x_{video.stem}").resolve()
    arquivo = pasta / "cortes.json"
    if not arquivo.is_file():
        sys.exit(f"Rode o cortes.py antes (não achei {arquivo}).")
    cenas = json.loads(arquivo.read_text(encoding="utf-8"))["cenas"]

    pasta_cenas = pasta / "cenas"
    pasta_cenas.mkdir(parents=True, exist_ok=True)
    for velha in pasta_cenas.glob("cena_*.jpg"):
        velha.unlink()
    fonte = fonte_para_ffmpeg()
    for c in cenas:
        tirar_foto(video, c, pasta_cenas / f"cena_{c['n']:03d}.jpg", args.largura, fonte)
    folhas = montar_folhas(pasta_cenas, len(cenas), pasta)

    print(f"📸 {len(cenas)} fotos em {pasta_cenas}")
    for f in folhas:
        print(f"🗂️  {f}")
    if not fonte:
        print("⚠️ Sem fonte encontrada: as fotos saíram sem número e tempo.")


if __name__ == "__main__":
    main()
