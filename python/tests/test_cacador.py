"""Testes do caçador de momentos (Ticket 025). Áudio de teste gerado aqui, com um "grito" falso."""

import tempfile
import unittest
from pathlib import Path

import av
import numpy as np

from cacador import ErroCacador, achar_picos, energia_por_segundo, hms, ler_hms, nota_markdown


def gravar_wav(caminho, segundos, gritos=(), taxa=8000):
    """Ruído baixo o tempo todo + rajadas altas nos segundos de `gritos`."""
    rng = np.random.default_rng(42)
    som = rng.normal(0, 0.01, segundos * taxa)
    for g in gritos:
        som[g * taxa:(g + 2) * taxa] = rng.normal(0, 0.5, 2 * taxa)
    pcm = (np.clip(som, -1, 1) * 32767).astype(np.int16).reshape(1, -1)
    with av.open(str(caminho), "w", format="wav") as c:
        s = c.add_stream("pcm_s16le", rate=taxa, layout="mono")
        quadro = av.AudioFrame.from_ndarray(pcm, format="s16", layout="mono")
        quadro.sample_rate = taxa
        for pacote in s.encode(quadro):
            c.mux(pacote)
        for pacote in s.encode(None):
            c.mux(pacote)


class TestEnergia(unittest.TestCase):
    def test_acha_o_grito_no_audio_de_verdade(self):
        with tempfile.TemporaryDirectory() as d:
            wav = Path(d) / "live.wav"
            gravar_wav(wav, 120, gritos=(70,))
            energias = energia_por_segundo(wav)
            self.assertEqual(len(energias), 120)
            momentos = achar_picos(energias)
            self.assertEqual(len(momentos), 1)
            self.assertIn(momentos[0].pico, (69, 70, 71))
            self.assertEqual(momentos[0].numero, 1)

    def test_nao_altera_o_original(self):
        with tempfile.TemporaryDirectory() as d:
            wav = Path(d) / "live.wav"
            gravar_wav(wav, 30, gritos=(10,))
            antes = wav.read_bytes()
            energia_por_segundo(wav)
            self.assertEqual(wav.read_bytes(), antes)

    def test_arquivo_que_nao_e_video(self):
        with tempfile.TemporaryDirectory() as d:
            falso = Path(d) / "falso.mp4"
            falso.write_text("isso não é vídeo")
            with self.assertRaises(ErroCacador):
                energia_por_segundo(falso)


class TestPicos(unittest.TestCase):
    def test_silencio_nao_tem_momento(self):
        self.assertEqual(achar_picos([-40.0] * 600), [])
        self.assertEqual(achar_picos([]), [])

    def test_mais_forte_vem_primeiro_e_respeita_distancia(self):
        e = [-40.0] * 600
        e[100] = e[101] = -20.0   # +20 dB
        e[110] = e[111] = -10.0   # +30 dB, mas a 10 s do outro → vira um só
        e[400] = e[401] = -25.0   # +15 dB
        m = achar_picos(e)
        self.assertEqual([x.numero for x in m], [1, 2])
        self.assertIn(m[0].pico, (110, 111))
        self.assertIn(m[1].pico, (400, 401))

    def test_maximo_de_momentos(self):
        e = [-40.0] * 3000
        for i in range(100, 3000, 100):
            e[i] = e[i + 1] = -10.0
        self.assertEqual(len(achar_picos(e, maximo=5)), 5)

    def test_corte_sugerido_fica_dentro_do_video(self):
        e = [-40.0] * 300
        e[2] = e[3] = -5.0
        e[298] = e[299] = -5.0
        for m in achar_picos(e):
            self.assertGreaterEqual(m.inicio, 0)
            self.assertLessEqual(m.fim, 300)


class TestTempoENota(unittest.TestCase):
    def test_hms(self):
        self.assertEqual(hms(65), "1:05")
        self.assertEqual(hms(3725), "1:02:05")
        self.assertEqual(ler_hms("1:02:05"), 3725)
        self.assertEqual(ler_hms("45"), 45)
        for ruim in ("", "1:-2", "a:b", "1:2:3:4", "1.5"):
            with self.assertRaises(ValueError):
                ler_hms(ruim)

    def test_nome_do_video_e_desarmado_na_nota(self):
        nota = nota_markdown("live](javascript:x)|<script>.mp4", [], 60)
        self.assertNotIn("](", nota)
        self.assertNotIn("<script>", nota)
        self.assertIn("rádio do GTA", nota)


if __name__ == "__main__":
    unittest.main()
