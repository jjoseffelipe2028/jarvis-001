"""raio-x-video — bloco 3: o som do vídeo.

Mede o volume ao longo do tempo, acha os silêncios, os "golpes" de som (batida, efeito, impacto),
estima o BPM da música e confere se os cortes caem no tempo.
Usa só ffmpeg e a biblioteca padrão do Python (sem baixar nada).

Uso:
    python som.py VIDEO.mp4 [--saida pasta] [--silencio -40]

Lê o cortes.json do bloco 1 (se existir) para o teste "corte na batida".
Saída: raio-x_<nome>/som.json e onda.png (desenho do som).
Segurança: ffmpeg chamado com lista de argumentos (sem shell); só lê o vídeo.
"""
from __future__ import annotations

import argparse
import array
import json
import math
import shutil
import statistics
import subprocess
import sys
from pathlib import Path

TAXA = 8000          # amostras por segundo (basta para volume e batida)
PASSO = 160          # 20 ms por janela
JANELA_S = PASSO / TAXA
SILENCIO_MIN_S = 0.3
GOLPE_GAP_S = 0.12   # dois golpes mais perto que isso viram um
CORTE_NO_TEMPO_S = 0.08


def precisa(programa: str) -> str:
    caminho = shutil.which(programa)
    if not caminho:
        sys.exit(f"Falta o {programa}. Instale pelo winget (Gyan.FFmpeg).")
    return caminho


def tem_audio(video: Path) -> bool:
    r = subprocess.run(
        [precisa("ffprobe"), "-v", "error", "-select_streams", "a", "-show_entries",
         "stream=index", "-of", "csv=p=0", str(video)],
        capture_output=True, text=True,
    )
    return bool(r.stdout.strip())


def ler_amostras(video: Path) -> array.array:
    r = subprocess.run(
        [precisa("ffmpeg"), "-hide_banner", "-loglevel", "error", "-i", str(video),
         "-vn", "-ac", "1", "-ar", str(TAXA), "-f", "s16le", "-"],
        capture_output=True,
    )
    if r.returncode != 0:
        sys.exit("O ffmpeg não conseguiu ler o áudio:\n" + r.stderr.decode(errors="replace")[-800:])
    a = array.array("h")
    a.frombytes(r.stdout[: len(r.stdout) // 2 * 2])
    if sys.byteorder == "big":
        a.byteswap()
    return a


def volumes_db(a: array.array) -> list[float]:
    """Volume (dBFS) de cada janela de 20 ms. 0 dB = máximo; -60 = quase nada."""
    saida = []
    for i in range(0, len(a) - PASSO + 1, PASSO):
        bloco = a[i : i + PASSO]
        rms = math.sqrt(sum(x * x for x in bloco) / PASSO) / 32768
        saida.append(20 * math.log10(rms) if rms > 1e-5 else -100.0)
    return saida


def achar_silencios(db: list[float], limite: float) -> list[dict]:
    trechos, inicio = [], None
    for i, v in enumerate(db + [0.0]):  # o 0.0 no fim fecha um silêncio aberto
        if v < limite and inicio is None:
            inicio = i
        elif v >= limite and inicio is not None:
            dura = (i - inicio) * JANELA_S
            if dura >= SILENCIO_MIN_S:
                trechos.append({"inicio": round(inicio * JANELA_S, 2), "dura": round(dura, 2)})
            inicio = None
    return trechos


def achar_golpes(db: list[float]) -> tuple[list[float], list[float]]:
    """Golpe = subida forte de volume de uma janela para a outra (batida, impacto, efeito).
    O "antes do vídeo" conta como silêncio (-60 dB): assim uma batida no segundo 0 também conta."""
    if len(db) < 3:
        return [], []
    subida = []
    for i in range(len(db)):
        antes = -60.0 if i == 0 else max(db[i - 1], -60.0)
        subida.append(max(0.0, min(db[i], 0.0) - antes))
    corte = statistics.mean(subida) + 2 * statistics.pstdev(subida)
    corte = max(corte, 6.0)  # pelo menos +6 dB de uma vez
    n = len(subida)
    golpes: list[float] = []
    for i in range(n):
        # compara só com os vizinhos que existem (subida[-1] seria o ÚLTIMO item, sem erro)
        pico = (i == 0 or subida[i] >= subida[i - 1]) and (i == n - 1 or subida[i] >= subida[i + 1])
        if subida[i] >= corte and pico:
            t = i * JANELA_S
            if not golpes or t - golpes[-1] >= GOLPE_GAP_S:
                golpes.append(round(t, 3))
    return golpes, subida


def estimar_bpm(subida: list[float]) -> tuple[float | None, float]:
    """Autocorrelação da curva de subidas entre 60 e 180 BPM. Devolve (bpm, confiança 0-1)."""
    n = len(subida)
    if n * JANELA_S < 4:
        return None, 0.0
    media = statistics.mean(subida)
    x = [v - media for v in subida]
    energia = sum(v * v for v in x) or 1.0
    melhor, melhor_lag, notas = 0.0, None, []
    for lag in range(int(60 / 180 / JANELA_S), int(60 / 60 / JANELA_S) + 1):
        c = sum(x[i] * x[i + lag] for i in range(n - lag)) / energia
        notas.append(c)
        if c > melhor:
            melhor, melhor_lag = c, lag
    if melhor_lag is None or melhor < 0.1:
        return None, round(max(melhor, 0.0), 2)
    return round(60 / (melhor_lag * JANELA_S), 1), round(min(melhor, 1.0), 2)


def cortes_no_tempo(pasta: Path, golpes: list[float]) -> dict | None:
    arquivo = pasta / "cortes.json"
    if not arquivo.is_file() or not golpes:
        return None
    cenas = json.loads(arquivo.read_text(encoding="utf-8"))["cenas"]
    cortes = [c["inicio"] for c in cenas[1:]]
    if not cortes:
        return None
    batem = [c for c in cortes if min(abs(c - g) for g in golpes) <= CORTE_NO_TEMPO_S]
    return {"cortes": len(cortes), "no_tempo": len(batem),
            "pct": round(len(batem) / len(cortes) * 100), "tempos": batem}


def desenhar_onda(video: Path, destino: Path) -> None:
    subprocess.run(
        [precisa("ffmpeg"), "-hide_banner", "-loglevel", "error", "-y", "-i", str(video),
         "-filter_complex", "aformat=channel_layouts=mono,showwavespic=s=1600x240:colors=0x55ccff",
         "-frames:v", "1", str(destino)],
        capture_output=True,
    )


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description="Som do raio-x-video")
    p.add_argument("video", type=Path)
    p.add_argument("--saida", type=Path, default=None)
    p.add_argument("--silencio", type=float, default=-40.0,
                   help="abaixo disso (dBFS) conta como silêncio (padrão -40)")
    args = p.parse_args()

    video = args.video.resolve()
    if not video.is_file():
        sys.exit(f"Vídeo não encontrado: {video}")
    if not -80 <= args.silencio <= -10:
        sys.exit("Use --silencio entre -80 e -10.")
    pasta = (args.saida or video.parent / f"raio-x_{video.stem}").resolve()
    pasta.mkdir(parents=True, exist_ok=True)

    if not tem_audio(video):
        (pasta / "som.json").write_text(json.dumps({"tem_audio": False}), encoding="utf-8")
        print("🔇 O vídeo não tem áudio.")
        return

    db = volumes_db(ler_amostras(video))
    audiveis = [v for v in db if v > args.silencio]
    silencios = achar_silencios(db, args.silencio)
    golpes, subida = achar_golpes(db)
    bpm, confianca = estimar_bpm(subida)
    no_tempo = cortes_no_tempo(pasta, golpes)
    desenhar_onda(video, pasta / "onda.png")

    resumo = {
        "tem_audio": True,
        "duracao_s": round(len(db) * JANELA_S, 2),
        "volume_medio_db": round(statistics.mean(audiveis), 1) if audiveis else None,
        "volume_pico_db": round(max(db), 1),
        "silencios": silencios,
        "tempo_em_silencio_pct": round(sum(s["dura"] for s in silencios) / (len(db) * JANELA_S) * 100) if db else 0,
        "golpes": golpes,
        "golpes_por_minuto": round(len(golpes) / (len(db) * JANELA_S) * 60, 1) if db else 0,
        "bpm": bpm,
        "bpm_confianca": confianca,
        "cortes_na_batida": no_tempo,
    }
    (pasta / "som.json").write_text(json.dumps(resumo, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"🔊 Volume médio {resumo['volume_medio_db']} dB · pico {resumo['volume_pico_db']} dB")
    print(f"🤫 {len(silencios)} silêncios ({resumo['tempo_em_silencio_pct']}% do vídeo)"
          + "".join(f"\n   {s['inicio']:.2f} s por {s['dura']:.2f} s" for s in silencios[:10]))
    print(f"🥁 {len(golpes)} golpes de som ({resumo['golpes_por_minuto']}/min): "
          + ", ".join(f"{g:.2f}" for g in golpes[:15]) + (" …" if len(golpes) > 15 else ""))
    if bpm:
        print(f"🎵 Batida estimada: {bpm} BPM (confiança {confianca})")
    else:
        print("🎵 Sem batida regular (fala, ambiente ou música solta)")
    if no_tempo:
        print(f"✂️  {no_tempo['no_tempo']} de {no_tempo['cortes']} cortes caem num golpe de som ({no_tempo['pct']}%)")
    print(f"📁 {pasta / 'som.json'} · {pasta / 'onda.png'}")


if __name__ == "__main__":
    main()
