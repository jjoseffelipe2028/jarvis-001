"""
Jarvis 001 — Ouvidos (teste)

Grava o microfone até você apertar Enter e transforma a fala em texto.
Só OUVE: quem responde é o cérebro (chat.py).
Tudo local: o áudio nunca sai do PC. 🔒
(Na 1ª vez, baixa o modelo do Whisper do Hugging Face/Systran. Depois, offline.)
"""

import queue

import numpy as np
import sounddevice as sd
from faster_whisper import WhisperModel

TAXA = 16000      # o Whisper trabalha com 16 mil amostras por segundo
MODELO = "small"  # tamanho do modelo (baixa 1 vez, ~500 MB)


def gravar():
    """Liga o microfone no 1º Enter e desliga no 2º Enter."""
    pedacos = queue.Queue()  # fila onde o áudio vai chegando

    def ao_receber(dados, frames, tempo, status):
        pedacos.put(dados.copy())  # guarda cada pedaço de som

    input("🎙️ Aperte Enter para COMEÇAR a falar...")
    with sd.InputStream(samplerate=TAXA, channels=1, dtype="float32",
                        callback=ao_receber):
        input("🔴 Gravando... aperte Enter para PARAR.")

    audio = []
    while not pedacos.empty():
        audio.append(pedacos.get())
    if not audio:
        return np.array([], dtype="float32")
    return np.concatenate(audio).flatten()  # junta tudo num áudio só


def main():
    print("Carregando o ouvido (na 1ª vez baixa da internet)...")
    modelo = WhisperModel(MODELO, device="cpu", compute_type="int8")

    audio = gravar()
    if audio.size == 0:
        print("Nenhum áudio gravado.")
        return

    segmentos, _ = modelo.transcribe(audio, language="pt")
    texto = " ".join(s.text.strip() for s in segmentos)
    print(f"📝 Você disse: {texto}")


if __name__ == "__main__":
    main()
