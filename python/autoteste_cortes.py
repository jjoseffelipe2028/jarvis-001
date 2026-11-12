"""
Jarvis 001 — Autoteste do robô de cortes 🧪 (Ticket 025)

  python cortes.py autoteste

Monta uma "live" falsa de 90 s (imagem de teste + ruído + a voz do Jarvis falando no segundo 40),
e confere o caminho inteiro numa PASTA TEMPORÁRIA (apagada no fim):
  1. o caçador acha o momento da fala
  2. o porteiro barra um corte de 10 s
  3. o corte sai em pé (1080x1920), com áudio e com a duração certa
  4. o Whisper escreveu legenda
  5. o original continua igual (mesmo hash)
Não precisa do .env e não toca nas suas pastas de vídeo.
"""

import hashlib
import subprocess
import tempfile
import wave
from pathlib import Path

import av
import numpy as np

import cacador
import cortador

FALA = "Olha isso mano! Que fuga incrível, a polícia ficou para trás. Agora vamos para o aeroporto."
VOZ = Path(__file__).parent / "vozes" / "pt_BR-faber-medium.onnx"
SEGUNDO_DA_FALA = 40


def _gerar_fala(destino):
    """Voz do Jarvis (Piper) num .wav. Sem a voz baixada → None (o teste de legenda é pulado)."""
    if not VOZ.is_file():
        return None
    from piper import PiperVoice
    voz = PiperVoice.load(str(VOZ))
    pedacos, taxa = [], 22050
    for c in voz.synthesize(FALA):
        taxa = c.sample_rate
        pedacos.append(np.frombuffer(c.audio_int16_bytes, dtype=np.int16))
    with wave.open(str(destino), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(taxa)
        w.writeframes(np.concatenate(pedacos).tobytes())
    return destino


def _gerar_live(ffmpeg, pasta, fala):
    """Vídeo deitado (16:9) de 90 s: ruído baixo + a fala no segundo 40."""
    live = pasta / "live teste.mp4"
    cmd = [ffmpeg, "-hide_banner", "-nostdin", "-loglevel", "error", "-n",
           "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=90",
           "-f", "lavfi", "-i", "anoisesrc=color=pink:amplitude=0.02:duration=90"]
    if fala:
        atraso = SEGUNDO_DA_FALA * 1000
        cmd += ["-i", f"file:{fala}", "-filter_complex",
                f"[2:a]aresample=48000,adelay={atraso}:all=1,volume=3[f];"
                "[1:a][f]amix=inputs=2:duration=first:normalize=0[a]",
                "-map", "0:v", "-map", "[a]"]
    cmd += ["-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", f"file:{live}"]
    subprocess.run(cmd, cwd=pasta, stdin=subprocess.DEVNULL, capture_output=True, check=True,
                   shell=False, timeout=300)
    return live


def _hash(caminho):
    return hashlib.sha256(Path(caminho).read_bytes()).hexdigest()


def rodar(saida=print):
    """Roda os 5 testes. Devolve True se todos passaram."""
    ffmpeg = cortador.achar_ffmpeg()
    resultados = []

    def checar(nome, ok, detalhe=""):
        resultados.append(ok)
        saida(f"   {'✅' if ok else '❌'} {nome}{' · ' + detalhe if detalhe else ''}")

    with tempfile.TemporaryDirectory(prefix="jarvis-autoteste-") as d:
        pasta = Path(d)
        saida("🧪 Montando a live falsa de 90 s (pasta temporária)...")
        fala = _gerar_fala(pasta / "fala.wav")
        live = _gerar_live(ffmpeg, pasta, fala)
        antes = _hash(live)

        saida("🔎 1. Caçador")
        energias = cacador.energia_por_segundo(live)
        momentos = cacador.achar_picos(energias)
        if fala:
            perto = [m for m in momentos if SEGUNDO_DA_FALA - 2 <= m.pico <= SEGUNDO_DA_FALA + 12]
            checar("achou o momento da fala", bool(perto),
                   f"pico em {cacador.hms(momentos[0].pico)}" if momentos else "nenhum pico")
        else:
            checar("leu o áudio inteiro", len(energias) == 90, f"{len(energias)} s")

        saida("🚪 2. Porteiro")
        ok, motivo = cortador.liberar(20, 30, 90, "@amigo")
        checar("barrou o corte de 10 s", not ok, motivo)

        saida("✂️ 3. Corte (o Whisper demora uns segundos para ligar)")
        falas = []
        whisper = cortador.transcritor_whisper()

        def transcrever(wav):
            falas.extend(whisper(wav))
            return falas

        corte = pasta / "corte.mp4"
        cortador.Cortador(ffmpeg, transcrever).cortar(live, 35, 60, 90, "@amigo_teste", corte)
        with av.open(str(corte)) as c:
            v = c.streams.video[0]
            tem_audio = any(s.type == "audio" for s in c.streams)
            duracao = c.duration / av.time_base
        checar("em pé 1080x1920", (v.width, v.height) == (1080, 1920), f"{v.width}x{v.height}")
        checar("com áudio e 25 s", tem_audio and abs(duracao - 25) < 1, f"{duracao:.1f} s")

        saida("📝 4. Legenda")
        if fala:
            texto = " ".join(t for _, _, t in falas)
            checar("o Whisper escreveu a fala", len(texto) > 10, f"\"{cortador.desarmar_ass(texto)[:60]}\"")
            srt = corte.with_suffix(".srt")
            checar("legenda separada (.srt) salva", srt.is_file() and "-->" in srt.read_text(encoding="utf-8"))
        else:
            saida("   ⏭️ pulado: a voz do Piper não está em python/vozes/")

        saida("🔒 5. Original")
        checar("original intacto (mesmo hash)", _hash(live) == antes)

    passou = all(resultados)
    saida(f"\n{'✅ AUTOTESTE OK' if passou else '❌ AUTOTESTE FALHOU'}: "
          f"{sum(resultados)} de {len(resultados)} conferências passaram. Pasta temporária apagada.")
    return passou
