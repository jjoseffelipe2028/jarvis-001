"""
Jarvis 001 — MuseTalk rápida (Ticket 055, Parte 3) ⚡👄

Roda DENTRO do venv da MuseTalk (o boca_ia.py chama com cwd = pasta da MuseTalk). Não importa nada do Jarvis.

Por que é rápida: o modo normal da MuseTalk acha o rosto e recorta o rosto em CADA quadro do vídeo (~70% do tempo).
O personagem do Jarvis é uma imagem parada (a cabeça só mexe depois, no avatar.py). Então:
  1. o rosto é achado e recortado UMA vez só e guardado em cache (pela impressão digital da imagem);
  2. a IA só desenha a boca nos quadros em que tem voz; no silêncio vai a boca fechada original;
  3. os quadros vão direto para o ffmpeg (sem salvar milhares de PNGs).

Uso: python musetalk_rapido.py --rosto rosto.png --audio voz.wav --saida boca.mp4 --fps 30
                               [--fala fala.json] [--cache pasta] [--ffmpeg ffmpeg.exe]
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.getcwd())     # a pasta da MuseTalk (cwd), para achar o pacote `musetalk`

import cv2
import numpy as np
import torch

VERSAO_CACHE = "v15-jaw-m10-1"
LOTE = int(os.environ.get("JARVIS_RAPIDO_LOTE", "8"))
DECODE = 2               # o VAE decodifica de 2 em 2: 8 juntas = pico de 6,8 GB (estoura a RTX 4050 de 6 GB)


def log(msg):
    print(f"[rapido] {msg}", flush=True)


def preparar(rosto, cache, vae):
    """Caixa do rosto, latente e máscara de mistura: do cache ou calculados uma vez (e guardados)."""
    from musetalk.utils.blending import get_image_prepare_material
    if os.path.isfile(os.path.join(cache, "info.json")):
        with open(os.path.join(cache, "info.json"), encoding="utf-8") as f:
            info = json.load(f)
        latente = torch.load(os.path.join(cache, "latente.pt"))
        mascara = cv2.imread(os.path.join(cache, "mascara.png"), cv2.IMREAD_GRAYSCALE)
        log("rosto do cache")
        return info["caixa"], info["recorte"], latente, mascara
    t = time.time()
    os.makedirs(cache, exist_ok=True)
    from musetalk.utils.preprocessing import get_landmark_and_bbox, coord_placeholder   # carrega DWPose + S3FD
    from musetalk.utils.face_parsing import FaceParsing
    caminho = os.path.join(cache, "rosto.png")
    cv2.imwrite(caminho, rosto)
    coords, _ = get_landmark_and_bbox([caminho], 0)
    if not coords or tuple(coords[0]) == tuple(coord_placeholder):
        raise SystemExit("ERRO: a IA não achou o rosto na imagem")
    x1, y1, x2, y2 = [int(v) for v in coords[0]]
    y2 = min(y2 + 10, rosto.shape[0])                       # extra_margin da v1.5
    recorte = cv2.resize(rosto[y1:y2, x1:x2], (256, 256), interpolation=cv2.INTER_LANCZOS4)
    latente = vae.get_latents_for_unet(recorte)
    mascara, caixa_mistura = get_image_prepare_material(rosto, [x1, y1, x2, y2], fp=FaceParsing(left_cheek_width=90, right_cheek_width=90), mode="jaw")
    torch.save(latente, os.path.join(cache, "latente.pt"))
    cv2.imwrite(os.path.join(cache, "mascara.png"), mascara)
    with open(os.path.join(cache, "info.json"), "w", encoding="utf-8") as f:
        json.dump({"caixa": [x1, y1, x2, y2], "recorte": [int(v) for v in caixa_mistura]}, f)
    log(f"rosto preparado e guardado ({time.time() - t:.1f}s)")
    return [x1, y1, x2, y2], [int(v) for v in caixa_mistura], latente, mascara


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rosto", required=True)
    ap.add_argument("--audio", required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--fala", default=None, help="JSON com 1 (fala) / 0 (silêncio) por quadro")
    ap.add_argument("--cache", default=os.path.join("results", "jarvis-cache"))
    ap.add_argument("--ffmpeg", default="ffmpeg")
    a = ap.parse_args()

    t0 = time.time()
    rosto = cv2.imread(a.rosto)                              # BGR
    if rosto is None:
        raise SystemExit("ERRO: não li o rosto")
    digital = hashlib.sha256(open(a.rosto, "rb").read() + VERSAO_CACHE.encode()).hexdigest()[:16]

    from transformers import WhisperModel
    from musetalk.utils.utils import load_all_model
    from musetalk.utils.audio_processor import AudioProcessor
    from musetalk.utils.blending import get_image_blending

    dispositivo = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    vae, unet, pe = load_all_model(unet_model_path="models/musetalkV15/unet.pth", vae_type="sd-vae",
                                   unet_config="models/musetalkV15/musetalk.json", device=dispositivo)
    pe, vae.vae, unet.model = pe.half().to(dispositivo), vae.vae.half().to(dispositivo), unet.model.half().to(dispositivo)
    tipo = unet.model.dtype
    audio = AudioProcessor(feature_extractor_path="models/whisper")
    whisper = WhisperModel.from_pretrained("models/whisper").to(device=dispositivo, dtype=tipo).eval()
    whisper.requires_grad_(False)
    if dispositivo.type == "cuda":
        log(f"modelos carregados ({time.time() - t0:.1f}s) · memória {torch.cuda.memory_allocated() / 2**20:.0f} MB")
    else:
        log(f"modelos carregados ({time.time() - t0:.1f}s)")

    with torch.no_grad():
        caixa, recorte, latente, mascara = preparar(rosto, os.path.join(a.cache, digital), vae)
        x1, y1, x2, y2 = caixa

        t = time.time()
        feats, tamanho = audio.get_audio_feature(a.audio, weight_dtype=tipo)
        pedacos = audio.get_whisper_chunk(feats, dispositivo, tipo, whisper, tamanho, fps=a.fps,
                                          audio_padding_length_left=2, audio_padding_length_right=2)
        total = len(pedacos)
        fala = [1] * total
        if a.fala:
            with open(a.fala, encoding="utf-8") as f:
                lista = json.load(f)
            fala = [int(lista[i]) if i < len(lista) else 1 for i in range(total)]
        com_voz = [i for i in range(total) if fala[i]]
        if dispositivo.type == "cuda":   # solta o que o Whisper reservou na placa antes de desenhar as bocas
            del whisper
            torch.cuda.empty_cache()
            torch.cuda.reset_peak_memory_stats()
            log(f"placa: reservado {torch.cuda.memory_reserved() / 2**20:.0f} MB")
        log(f"áudio: {total} quadros, {len(com_voz)} com voz ({time.time() - t:.1f}s)")

        ff = subprocess.Popen([a.ffmpeg, "-nostdin", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24",
                               "-s", f"{rosto.shape[1]}x{rosto.shape[0]}", "-framerate", str(a.fps), "-i", "pipe:0",
                               "-c:v", "libx264", "-crf", "12", "-pix_fmt", "yuv420p", a.saida], stdin=subprocess.PIPE)
        t = time.time()
        bocas = {}
        passos = torch.tensor([0], device=dispositivo)
        for k in range(0, len(com_voz), LOTE):
            idx = com_voz[k:k + LOTE]
            w = torch.stack([pedacos[i] for i in idx]).to(dispositivo)
            lat = torch.cat([latente] * len(idx), dim=0).to(device=dispositivo, dtype=tipo)
            pred = unet.model(lat, passos, encoder_hidden_states=pe(w)).sample
            pred = pred.to(dtype=vae.vae.dtype)
            imgs = [img for j in range(0, len(idx), DECODE) for img in vae.decode_latents(pred[j:j + DECODE])]
            for i, img in zip(idx, imgs):
                bocas[i] = img
            if k < 3 * LOTE and dispositivo.type == "cuda":
                torch.cuda.synchronize()
                log(f"lote {k // LOTE}: {time.time() - t:.1f}s · memória {torch.cuda.memory_allocated() / 2**20:.0f} MB"
                    f" (pico {torch.cuda.max_memory_allocated() / 2**20:.0f} MB)")
        log(f"bocas desenhadas ({time.time() - t:.1f}s)")

        t = time.time()
        for i in range(total):
            if i in bocas:
                cara = cv2.resize(bocas.pop(i).astype(np.uint8), (x2 - x1, y2 - y1))
                quadro = get_image_blending(rosto, cara, caixa, mascara, recorte)
            else:
                quadro = rosto
            ff.stdin.write(np.ascontiguousarray(quadro).tobytes())
        ff.stdin.close()
        if ff.wait() != 0:
            raise SystemExit("ERRO: ffmpeg")
    log(f"vídeo pronto ({time.time() - t:.1f}s) · total {time.time() - t0:.1f}s")


if __name__ == "__main__":
    main()
