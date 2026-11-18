"""
Jarvis 001 — Boca por IA (Ticket 055, Parte 2) 👄

Deixa a fala do retrato 3D natural com a MuseTalk (IA de lip-sync, código MIT, modelo liberado para uso comercial),
instalada À PARTE em C:\\Users\\jjose\\ferramentas\\musetalk (venv próprio, Python 3.10). Fluxo:
  1. o avatar.py faz o personagem balançando pela voz, de BOCA FECHADA (entrada.mp4) + voz.wav;
  2. a MuseTalk redesenha só a boca, som a som (boca.mp4);
  3. aqui os quadros voltam para o brabo.py com a transparência certa (moldura arredondada ou recorte).

Padrão: modo rápido (boca_rapida.py, ~3x). JARVIS_BOCA_IA_MODO=normal volta ao modo abaixo. Uma IA por vez na placa (trava).
Se a MuseTalk não estiver instalada, estiver desligada (JARVIS_BOCA_IA=0) ou der erro, o vídeo sai com a boca
antiga (3 imagens pelo volume): nunca trava o Brabo por causa disso.
Tudo local: nada é baixado nem enviado.
"""
import os
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

import avatar

ENTRADA_LADO = 512          # tamanho do vídeo que a MuseTalk recebe (ela recorta o rosto em 256)
TEMPO_LIMITE = 25 * 60      # segundos


class ErroBocaIA(Exception):
    pass


def pasta_musetalk():
    return Path(os.environ.get("JARVIS_MUSETALK", Path.home() / "ferramentas" / "musetalk"))


def ligado(pasta=None):
    """True se a MuseTalk está instalada (venv + modelos) e não foi desligada."""
    if os.environ.get("JARVIS_BOCA_IA", "1") == "0":
        return False
    p = Path(pasta or pasta_musetalk())
    return all((p / f).is_file() for f in (".venv/Scripts/python.exe", "models/musetalkV15/unet.pth",
                                           "models/whisper/pytorch_model.bin", "models/sd-vae/config.json"))


def gravar_wav(caminho, audio, taxa):
    a = np.clip(np.asarray(audio, dtype=np.float32), -1, 1)
    with wave.open(str(caminho), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(int(taxa))
        w.writeframes((a * 32767).astype("<i2").tobytes())


def gravar_entrada(personagem, voz, taxa, destino, ffmpeg, fps=avatar.FPS, fundo=True):
    """O personagem balançando pela voz, boca fechada → mp4 (sem transparência: a IA não usa)."""
    import retratos
    fotos = retratos.retratos(personagem, ENTRADA_LADO)
    if fotos is None:
        raise ErroBocaIA("o personagem não tem as 3 imagens 3D")
    cmd = [ffmpeg, "-nostdin", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba",
           "-s", f"{ENTRADA_LADO}x{ENTRADA_LADO}", "-framerate", str(fps), "-i", "pipe:0",
           "-vf", "format=yuv420p", "-c:v", "libx264", "-crf", "14", str(destino)]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for q in avatar.quadros_retrato(personagem, fotos, voz, taxa, fundo, fps, boca_fechada=True):
            if not fundo:   # sem moldura: fundo cinza escuro liso no lugar do transparente
                cinza = np.full_like(q, 40)
                cinza[..., 3] = 255
                q = avatar._sobre(cinza, q)
            proc.stdin.write(np.ascontiguousarray(q).tobytes())
    finally:
        proc.stdin.close()
    if proc.wait(timeout=600) != 0:
        raise ErroBocaIA("não consegui gravar a entrada da IA")


def comando_musetalk(pasta_mt, pasta_tmp, ffmpeg):
    """Comando da MuseTalk (v1.5, float16) com a configuração gravada em `pasta_tmp`."""
    py = Path(pasta_mt) / ".venv" / "Scripts" / "python.exe"
    return [str(py), "-m", "scripts.inference", "--inference_config", str(Path(pasta_tmp) / "boca.yaml"),
            "--result_dir", str(Path(pasta_tmp) / "saida"), "--unet_model_path", "models/musetalkV15/unet.pth",
            "--unet_config", "models/musetalkV15/musetalk.json", "--version", "v15",
            "--ffmpeg_path", str(Path(ffmpeg).parent), "--use_float16", "--output_vid_name", "boca.mp4"]


def sincronizar(entrada, wav, pasta_tmp, ffmpeg, pasta_mt=None, rodar=subprocess.run):
    """Roda a MuseTalk → caminho do boca.mp4. Ela imprime o erro e sai com código 0, então confiro o arquivo."""
    pasta_mt = Path(pasta_mt or pasta_musetalk())
    pasta_tmp = Path(pasta_tmp)
    for p in (entrada, wav, pasta_tmp):
        if " " in str(p):   # a MuseTalk monta comandos de texto: espaço no caminho quebra
            raise ErroBocaIA("caminho com espaço")
    (pasta_tmp / "boca.yaml").write_text(
        f'task_0:\n video_path: "{Path(entrada).as_posix()}"\n audio_path: "{Path(wav).as_posix()}"\n',
        encoding="utf-8")
    env = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUTF8="1")
    env["PATH"] = str(Path(ffmpeg).parent) + os.pathsep + env.get("PATH", "")
    import boca_rapida      # a trava: uma IA por vez na placa de vídeo (duas juntas = as duas quase param)
    with boca_rapida.Trava(pasta_mt / ".jarvis-gpu.trava"), open(pasta_tmp / "musetalk.log", "wb") as log:
        try:
            rodar(comando_musetalk(pasta_mt, pasta_tmp, ffmpeg), cwd=pasta_mt, env=env, stdout=log, stderr=log,
                  timeout=TEMPO_LIMITE, check=False)
        except subprocess.TimeoutExpired as e:
            raise ErroBocaIA("a MuseTalk passou de 25 min") from e
    saida = pasta_tmp / "saida" / "v15" / "boca.mp4"
    if not saida.is_file() or saida.stat().st_size == 0:
        raise ErroBocaIA("a MuseTalk não gerou o vídeo (ver musetalk.log)")
    return saida


def ler_quadros(video, lado, ffmpeg):
    """Quadros RGB lado x lado do vídeo, um por um."""
    proc = subprocess.Popen([ffmpeg, "-nostdin", "-v", "error", "-i", str(video), "-an",
                             "-vf", f"scale={lado}:{lado}:flags=lanczos", "-f", "rawvideo", "-pix_fmt", "rgb24",
                             "pipe:1"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    tam = lado * lado * 3
    try:
        while True:
            dado = proc.stdout.read(tam)
            if len(dado) < tam:
                break
            yield np.frombuffer(dado, dtype=np.uint8).reshape(lado, lado, 3)
    finally:
        proc.stdout.close()
        proc.wait()


def juntar(rgb_quadros, alfa_quadros):
    """Cor da IA + transparência do nosso quadro. Se a IA devolver menos quadros, repete o último."""
    ultimo = None
    rgb_iter = iter(rgb_quadros)
    for alfa in alfa_quadros:
        ultimo = next(rgb_iter, ultimo)
        if ultimo is None:
            raise ErroBocaIA("a IA não devolveu nenhum quadro")
        q = np.empty(alfa.shape[:2] + (4,), dtype=np.uint8)
        q[..., :3] = ultimo
        q[..., 3] = alfa[..., 3]
        yield q


def quadros(personagem, voz, taxa, fundo, ffmpeg, pasta_tmp, fps=avatar.FPS, avisar=print, lado=avatar.LADO):
    """Quadros RGBA (lado x lado) com a boca da IA; se não der, a boca antiga (avatar.LADO). Mesma interface
    de avatar.quadros. `lado` maior serve para o Mestre do ensino (640)."""
    import retratos
    if os.environ.get("JARVIS_BOCA_IA_MODO", "rapida") == "rapida":   # Parte 3: rosto em cache, ~3x mais rápida
        import boca_rapida
        return boca_rapida.quadros(personagem, voz, taxa, fundo, ffmpeg, pasta_tmp, fps, avisar, lado)
    if not ligado() or retratos.arquivos(personagem) is None:
        return avatar.quadros(personagem, voz, taxa, fundo=fundo, fps=fps)
    try:
        pasta_tmp = Path(pasta_tmp)
        entrada, wav = pasta_tmp / "entrada.mp4", pasta_tmp / "voz-ia.wav"
        avisar("👄 Boca por IA (MuseTalk)... pode levar alguns minutos.")
        gravar_entrada(personagem, voz, taxa, entrada, ffmpeg, fps, fundo)
        gravar_wav(wav, voz, taxa)
        boca = sincronizar(entrada, wav, pasta_tmp, ffmpeg)
    except (ErroBocaIA, OSError, subprocess.SubprocessError) as e:
        avisar(f"⚠️ Boca por IA falhou ({e}). Vai com a boca normal.")
        return avatar.quadros(personagem, voz, taxa, fundo=fundo, fps=fps)
    fotos = retratos.retratos(personagem, lado)
    alfas = avatar.quadros_retrato(personagem, fotos, voz, taxa, fundo, fps, boca_fechada=True)
    return juntar(ler_quadros(boca, lado, ffmpeg), alfas)


if __name__ == "__main__":
    print("MuseTalk:", pasta_musetalk(), "ligada" if ligado() else "NÃO encontrada/desligada", file=sys.stderr)
