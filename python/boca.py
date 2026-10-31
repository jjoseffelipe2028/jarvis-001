"""
Jarvis 001 — Boca 🔊

Transforma texto em fala com o Piper (voz pt_BR-faber-medium).
Tudo local: nada sai do PC. 🔒
"""

from pathlib import Path

import numpy as np
import sounddevice as sd
from piper import PiperVoice

# A voz fica na pasta vozes/ (baixada 1 vez, fora do Git)
VOZ = Path(__file__).parent / "vozes" / "pt_BR-faber-medium.onnx"


def carregar_boca():
    """Liga a boca (carrega a voz). Fazer só 1 vez."""
    print("🔊 Carregando a boca...")
    return PiperVoice.load(str(VOZ))


def falar(boca, texto):
    """Fala o texto em voz alta."""
    pedacos = []
    taxa = 22050
    for chunk in boca.synthesize(texto):          # o Piper gera o som em pedaços
        taxa = chunk.sample_rate
        pedacos.append(np.frombuffer(chunk.audio_int16_bytes, dtype=np.int16))
    if pedacos:
        sd.play(np.concatenate(pedacos), taxa)    # toca no alto-falante
        sd.wait()                                 # espera terminar de falar


# Teste sozinho: python boca.py
if __name__ == "__main__":
    boca = carregar_boca()
    falar(boca, "Olá, José. Eu sou o Jarvis. Agora eu também sei falar.")
