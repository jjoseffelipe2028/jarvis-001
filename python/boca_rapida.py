"""
Jarvis 001 — Boca por IA rápida (Ticket 055, Parte 3) ⚡👄

Antes (boca_ia.sincronizar): a MuseTalk recebia o vídeo do personagem JÁ balançando e achava/recortava o rosto em
cada quadro → 15 s de Short ≈ 8 min.
Agora:
  1. a MuseTalk recebe UMA imagem parada (boca fechada) e guarda o rosto em cache (`musetalk_rapido.py`);
  2. a IA só desenha a boca nos quadros com voz;
  3. aqui a boca nova volta como "diferença" por cima da imagem 3D original (transparência perfeita) e só DEPOIS
     vem o balanço, o tranco e a moldura (avatar.movimento/_mexer/emoldurar) — o movimento continua igual.
Só uma boca IA por vez (trava em arquivo): duas juntas lotam os 6 GB da placa e as duas quase param.
"""
import json
import os
import subprocess
import time
from pathlib import Path

import numpy as np

import avatar
import boca_ia

LIMIAR_FALA = 0.06       # nível da voz (0..1) acima disso = está falando
FOLGA = 4                # quadros de folga antes/depois da fala (a boca abre e fecha com calma)
TRAVA_VELHA = 40 * 60    # trava mais velha que isso = sobra de um processo que morreu
RUIDO = 4                # diferença menor que isso = ruído do vídeo, não é boca


def script():
    return Path(__file__).with_name("musetalk_rapido.py")


def quadros_com_fala(voz, taxa, fps=avatar.FPS, limiar=LIMIAR_FALA, folga=FOLGA):
    """1 = desenhar boca pela IA, 0 = silêncio (vai a boca fechada original)."""
    nivel = avatar.nivel_da_voz(voz, taxa, fps)
    fala = (nivel > limiar).astype(np.int8)
    if folga and fala.size:
        perto = np.convolve(fala, np.ones(2 * folga + 1, dtype=np.int8), mode="same")
        fala = (perto > 0).astype(np.int8)
    return fala


def processo_vivo(pid):
    """True se o processo existe (Windows: pergunta ao sistema, sem mandar sinal nenhum)."""
    try:
        pid = int(pid)
    except (TypeError, ValueError):
        return False
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False
    import ctypes
    k = ctypes.windll.kernel32
    h = k.OpenProcess(0x1000, False, pid)            # PROCESS_QUERY_LIMITED_INFORMATION
    if not h:
        return False
    codigo = ctypes.c_ulong()
    ok = k.GetExitCodeProcess(h, ctypes.byref(codigo))
    k.CloseHandle(h)
    return bool(ok) and codigo.value == 259          # STILL_ACTIVE


class Trava:
    """Só um processo da MuseTalk por vez na placa de vídeo. Trava de processo morto (parado à força) é solta."""

    def __init__(self, caminho, esperar=boca_ia.TEMPO_LIMITE, passo=5.0, dormir=time.sleep, agora=time.time,
                 vivo=processo_vivo):
        self.caminho, self.esperar, self.passo, self.dormir, self.agora = Path(caminho), esperar, passo, dormir, agora
        self.vivo = vivo

    def __enter__(self):
        inicio = self.agora()
        while True:
            try:
                fd = os.open(self.caminho, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                os.write(fd, str(os.getpid()).encode())
                os.close(fd)
                return self
            except FileExistsError:
                try:
                    dono = self.caminho.read_text(encoding="utf-8").strip()
                    idade = self.agora() - self.caminho.stat().st_mtime
                    morta = (not self.vivo(dono)) if dono else idade > 10   # vazia = acabou de nascer
                    if idade > TRAVA_VELHA or morta:
                        self.caminho.unlink()
                        continue
                except FileNotFoundError:
                    continue
                if self.agora() - inicio > self.esperar:
                    raise boca_ia.ErroBocaIA("a placa de vídeo ficou ocupada tempo demais")
                self.dormir(self.passo)

    def __exit__(self, *erro):
        try:
            self.caminho.unlink()
        except FileNotFoundError:
            pass


def rosto_parado(fotos_entrada):
    """Imagem da boca fechada sobre cinza escuro (RGBA → RGBA opaco), a que a IA recebe."""
    f = fotos_entrada[0]
    cinza = np.full_like(f, 40)
    cinza[..., 3] = 255
    return avatar._sobre(cinza, f)


def gravar_png(rgba, destino, ffmpeg):
    lado = rgba.shape[0]
    subprocess.run([ffmpeg, "-nostdin", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba",
                    "-s", f"{lado}x{lado}", "-i", "pipe:0", str(destino)],
                   input=np.ascontiguousarray(rgba).tobytes(), check=True, timeout=60)


def comando(pasta_mt, rosto, wav, saida, fala_json, fps, ffmpeg):
    py = Path(pasta_mt) / ".venv" / "Scripts" / "python.exe"
    return [str(py), str(script()), "--rosto", str(rosto), "--audio", str(wav), "--saida", str(saida),
            "--fps", str(fps), "--fala", str(fala_json), "--ffmpeg", str(ffmpeg),
            "--cache", str(Path(pasta_mt) / "results" / "jarvis-cache")]


def sincronizar(personagem, voz, taxa, pasta_tmp, ffmpeg, fps=avatar.FPS, pasta_mt=None, rodar=subprocess.run):
    """Roda a MuseTalk rápida → (boca.mp4, rosto.png)."""
    import retratos
    pasta_mt = Path(pasta_mt or boca_ia.pasta_musetalk())
    pasta_tmp = Path(pasta_tmp)
    if " " in str(pasta_tmp) or " " in str(script()):
        raise boca_ia.ErroBocaIA("caminho com espaço")
    fotos = retratos.retratos(personagem, boca_ia.ENTRADA_LADO)
    if fotos is None:
        raise boca_ia.ErroBocaIA("o personagem não tem as 3 imagens 3D")
    rosto, wav = pasta_tmp / "rosto.png", pasta_tmp / "voz-ia.wav"
    fala_json, saida = pasta_tmp / "fala.json", pasta_tmp / "boca.mp4"
    gravar_png(rosto_parado(fotos), rosto, ffmpeg)
    boca_ia.gravar_wav(wav, voz, taxa)
    fala_json.write_text(json.dumps(quadros_com_fala(voz, taxa, fps).tolist()), encoding="utf-8")
    env = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUTF8="1")
    env["PATH"] = str(Path(ffmpeg).parent) + os.pathsep + env.get("PATH", "")
    with Trava(pasta_mt / ".jarvis-gpu.trava"), open(pasta_tmp / "musetalk.log", "wb") as log:
        try:
            rodar(comando(pasta_mt, rosto, wav, saida, fala_json, fps, ffmpeg), cwd=pasta_mt, env=env,
                  stdout=log, stderr=log, timeout=boca_ia.TEMPO_LIMITE, check=False)
        except subprocess.TimeoutExpired as e:
            raise boca_ia.ErroBocaIA("a MuseTalk passou do tempo") from e
    if not saida.is_file() or saida.stat().st_size == 0:
        raise boca_ia.ErroBocaIA("a MuseTalk não gerou o vídeo (ver musetalk.log)")
    return saida, rosto


def com_boca(base_rgba, base_rgb, ia_rgb):
    """Imagem 3D original + só o que a IA mudou (a boca). Fora da boca fica idêntica, com a transparência certa."""
    dif = ia_rgb.astype(np.int16) - base_rgb.astype(np.int16)
    dif[np.abs(dif).max(axis=-1) < RUIDO] = 0
    q = base_rgba.copy()
    q[..., :3] = np.clip(base_rgba[..., :3].astype(np.int16) + dif, 0, 255).astype(np.uint8)
    return q


def mexer(personagem, imgs, voz, taxa, fundo=True, fps=avatar.FPS):
    """Balanço + tranco + moldura em quadros já com a boca (mesmo movimento do avatar.quadros_retrato)."""
    nivel = avatar.nivel_da_voz(voz, taxa, fps)
    params = avatar.movimento(np.zeros(len(nivel)), nivel, fps)
    base = None
    for img, (_, angulo, escala, dy) in zip(imgs, params):
        img = avatar._mexer(img.astype(np.float32), angulo, escala, dy)
        if fundo:
            base = avatar.moldura(personagem, img.shape[0]) if base is None else base
            img = avatar.emoldurar(personagem, img, base)
        yield img


def quadros(personagem, voz, taxa, fundo, ffmpeg, pasta_tmp, fps=avatar.FPS, avisar=print, lado=avatar.LADO):
    """Mesma interface do boca_ia.quadros. Se der erro, a boca antiga."""
    import retratos
    if not boca_ia.ligado() or retratos.arquivos(personagem) is None:
        return avatar.quadros(personagem, voz, taxa, fundo=fundo, fps=fps)
    try:
        avisar("👄 Boca por IA (rápida)...")
        boca, rosto = sincronizar(personagem, voz, taxa, pasta_tmp, ffmpeg, fps)
        base_rgb = next(boca_ia.ler_quadros(rosto, lado, ffmpeg))
    except Exception as e:     # qualquer erro aqui: nunca trava o Short, vai a boca antiga
        avisar(f"⚠️ Boca por IA falhou ({e}). Vai com a boca normal.")
        return avatar.quadros(personagem, voz, taxa, fundo=fundo, fps=fps)
    original = retratos.retratos(personagem, lado)[0]
    total = len(avatar.nivel_da_voz(voz, taxa, fps))

    def com_bocas():
        n = 0
        for ia in boca_ia.ler_quadros(boca, lado, ffmpeg):
            if n >= total:
                break
            yield com_boca(original, base_rgb, ia)
            n += 1
        while n < total:                      # a IA devolveu menos quadros: completa com a boca fechada
            yield original
            n += 1

    return mexer(personagem, com_bocas(), voz, taxa, fundo, fps)
