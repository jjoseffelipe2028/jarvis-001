"""
Jarvis 001 — Ouvidos 👂

Grava o microfone e transforma a fala em texto.
Só OUVE: quem responde é o cérebro (chat.py).
Tudo local: o áudio nunca sai do PC. 🔒
"""

import queue

import numpy as np
import sounddevice as sd
from faster_whisper import WhisperModel

TAXA = 16000      # o Whisper trabalha com 16 mil amostras por segundo
MODELO = "small"  # tamanho do modelo (já baixado)

# Dica: palavras que eu uso muito. O ouvido lê antes de escutar e passa a acertar.
DICA = ("Jarvis, José, EBAC, Java, Git, GitHub, cibersegurança, Obsidian, Claude, "
        "beleza, peraí, bora, valeu, mano.")


def carregar_ouvido():
    """Liga o ouvido (demora uns segundos). Fazer só 1 vez."""
    print("👂 Carregando o ouvido...")
    return WhisperModel(MODELO, device="cpu", compute_type="int8")


def gravar():
    """Grava até apertar Enter. Devolve o áudio."""
    pedacos = queue.Queue()

    def ao_receber(dados, frames, tempo, status):
        pedacos.put(dados.copy())

    with sd.InputStream(samplerate=TAXA, channels=1, dtype="float32",
                        callback=ao_receber):
        input("🔴 Gravando... aperte Enter para PARAR.")

    audio = []
    while not pedacos.empty():
        audio.append(pedacos.get())
    if not audio:
        return np.array([], dtype="float32")
    return np.concatenate(audio).flatten()


def ouvir(modelo):
    """Grava a minha voz e devolve o TEXTO do que eu falei."""
    audio = gravar()
    if audio.size == 0:
        return ""
    segmentos, _ = modelo.transcribe(audio, language="pt", initial_prompt=DICA)
    return " ".join(s.text.strip() for s in segmentos).strip()


# Teste sozinho: python ouvido.py
if __name__ == "__main__":
    ouvido = carregar_ouvido()
    input("🎙️ Aperte Enter para COMEÇAR a falar...")
    print(f"📝 Você disse: {ouvir(ouvido)}")
