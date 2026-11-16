"""Testes do lado Python. Rodar na pasta python/: python -m unittest discover -s tests -t ."""

import os
import tempfile

# Ticket 055: os testes nunca usam as imagens 3D de verdade do José (Videos/personagens), só o desenho por código.
os.environ.setdefault("JARVIS_RETRATOS", os.path.join(tempfile.gettempdir(), "jarvis-sem-retratos"))
os.environ.setdefault("JARVIS_BOCA_IA", "0")      # e nunca rodam a MuseTalk de verdade (minutos de GPU)
