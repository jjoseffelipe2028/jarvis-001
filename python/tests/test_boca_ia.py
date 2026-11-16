"""Ticket 055: boca por IA (MuseTalk). Sem rodar a IA de verdade: o subprocess é falso."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import numpy as np

import avatar
import boca_ia


class TestBocaIA(unittest.TestCase):
    def test_desligada_ou_sem_instalar(self):
        with mock.patch.dict(os.environ, {"JARVIS_BOCA_IA": "0"}):
            self.assertFalse(boca_ia.ligado())
        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"JARVIS_BOCA_IA": "1"}):
            self.assertFalse(boca_ia.ligado(tmp))

    def test_comando_usa_v15_float16_e_ffmpeg(self):
        cmd = boca_ia.comando_musetalk(Path("C:/mt"), Path("C:/tmp/x"), "C:/ff/bin/ffmpeg.exe")
        self.assertIn("scripts.inference", cmd)
        self.assertIn("--use_float16", cmd)
        self.assertEqual(cmd[cmd.index("--version") + 1], "v15")
        self.assertEqual(Path(cmd[cmd.index("--ffmpeg_path") + 1]), Path("C:/ff/bin"))

    def test_sincronizar_confere_o_arquivo(self):
        with tempfile.TemporaryDirectory(prefix="bocaia") as tmp:
            tmp = Path(tmp)
            chamadas = []

            def falso_ok(cmd, **kw):
                chamadas.append(kw)
                (tmp / "saida" / "v15").mkdir(parents=True)
                (tmp / "saida" / "v15" / "boca.mp4").write_bytes(b"x")

            saida = boca_ia.sincronizar(tmp / "e.mp4", tmp / "v.wav", tmp, "ffmpeg", tmp, rodar=falso_ok)
            self.assertEqual(saida.name, "boca.mp4")
            self.assertEqual(chamadas[0]["env"]["PYTHONIOENCODING"], "utf-8")
            self.assertIn("video_path", (tmp / "boca.yaml").read_text(encoding="utf-8"))

        with tempfile.TemporaryDirectory(prefix="bocaia") as tmp:   # a MuseTalk "sai 0" sem gerar nada
            with self.assertRaises(boca_ia.ErroBocaIA):
                boca_ia.sincronizar(Path(tmp) / "e.mp4", Path(tmp) / "v.wav", tmp, "ffmpeg", tmp,
                                    rodar=lambda cmd, **kw: None)

        def demorou(cmd, **kw):
            raise subprocess.TimeoutExpired(cmd, 1)
        with tempfile.TemporaryDirectory(prefix="bocaia") as tmp, self.assertRaises(boca_ia.ErroBocaIA):
            boca_ia.sincronizar(Path(tmp) / "e.mp4", Path(tmp) / "v.wav", tmp, "ffmpeg", tmp, rodar=demorou)

    def test_juntar_poe_a_transparencia_e_completa_quadros(self):
        rgb = [np.full((4, 4, 3), 10, np.uint8), np.full((4, 4, 3), 20, np.uint8)]
        alfas = [np.zeros((4, 4, 4), np.uint8) for _ in range(3)]
        for a in alfas:
            a[..., 3] = 200
        saida = list(boca_ia.juntar(rgb, alfas))
        self.assertEqual(len(saida), 3)                 # a IA devolveu 2: o último se repete
        self.assertEqual(saida[2][0, 0, 0], 20)
        self.assertEqual(saida[0][0, 0, 3], 200)
        with self.assertRaises(boca_ia.ErroBocaIA):
            list(boca_ia.juntar([], alfas))

    def test_erro_na_ia_volta_para_a_boca_normal(self):
        avisos = []
        with mock.patch.object(boca_ia, "ligado", return_value=True), \
                mock.patch("retratos.arquivos", return_value=["a", "b", "c"]), \
                mock.patch.object(boca_ia, "gravar_entrada", side_effect=boca_ia.ErroBocaIA("x")), \
                mock.patch.object(avatar, "quadros", return_value="boca-normal") as normal:
            r = boca_ia.quadros("brabo", np.zeros(100, np.float32), 22050, True, "ffmpeg", "tmp", avisar=avisos.append)
        self.assertEqual(r, "boca-normal")
        normal.assert_called_once()
        self.assertTrue(any("falhou" in a for a in avisos))

    def test_wav(self):
        with tempfile.TemporaryDirectory() as tmp:
            caminho = Path(tmp) / "v.wav"
            boca_ia.gravar_wav(caminho, np.array([0, 0.5, -2.0], np.float32), 24000)
            import wave
            with wave.open(str(caminho)) as w:
                self.assertEqual((w.getframerate(), w.getnframes(), w.getsampwidth()), (24000, 3, 2))


if __name__ == "__main__":
    unittest.main()
