"""Ticket 055 Parte 3: boca por IA rápida. Sem rodar a IA de verdade: o subprocess é falso."""

import os
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

import numpy as np

import avatar
import boca_ia
import boca_rapida


def voz_com_pausa(taxa=16000):
    """1 s de fala, 1 s de silêncio, 1 s de fala."""
    t = np.arange(taxa) / taxa
    fala = 0.5 * np.sin(2 * np.pi * 180 * t).astype(np.float32)
    return np.concatenate([fala, np.zeros(taxa, np.float32), fala]), taxa


class TestBocaRapida(unittest.TestCase):
    def test_so_desenha_boca_onde_tem_voz(self):
        voz, taxa = voz_com_pausa()
        fala = boca_rapida.quadros_com_fala(voz, taxa, fps=30, folga=0)
        self.assertEqual(len(fala), 90)
        self.assertTrue(fala[:30].all() and fala[60:].all())
        self.assertFalse(fala[30:60].any())
        com_folga = boca_rapida.quadros_com_fala(voz, taxa, fps=30, folga=4)
        self.assertEqual(int(com_folga[30:60].sum()), 8)          # 4 quadros de cada lado da pausa

    def test_com_boca_muda_so_onde_a_ia_mudou(self):
        base = np.zeros((8, 8, 4), np.uint8)
        base[..., :3], base[..., 3] = 100, 200
        base_rgb = np.full((8, 8, 3), 40, np.uint8)
        ia = base_rgb.copy()
        ia[0, 0] = 42                    # ruído do vídeo: ignora
        ia[5, 5] = (90, 10, 40)          # boca: aplica a diferença
        q = boca_rapida.com_boca(base, base_rgb, ia)
        self.assertEqual(q[0, 0, :3].tolist(), [100, 100, 100])
        self.assertEqual(q[5, 5, :3].tolist(), [150, 70, 100])
        self.assertTrue((q[..., 3] == 200).all())                  # transparência intacta
        self.assertEqual(base[5, 5, 0], 100)                       # não estraga a imagem do cache

    def test_trava_espera_a_outra_e_limpa_a_velha(self):
        with tempfile.TemporaryDirectory() as tmp:
            caminho = Path(tmp) / "gpu.trava"
            caminho.write_text("123")
            relogio = [time.time()]
            dormidas = []

            def dormir(s):
                dormidas.append(s)
                relogio[0] += s
                if len(dormidas) == 2:
                    caminho.unlink()                                # a outra terminou
            with boca_rapida.Trava(caminho, esperar=60, passo=5, dormir=dormir, agora=lambda: relogio[0],
                                   vivo=lambda p: True):
                self.assertTrue(caminho.exists())
            self.assertEqual(len(dormidas), 2)
            self.assertFalse(caminho.exists())

            caminho.write_text("morto")
            velho = time.time() - boca_rapida.TRAVA_VELHA - 10
            os.utime(caminho, (velho, velho))
            with boca_rapida.Trava(caminho, esperar=0, dormir=lambda s: self.fail("não devia esperar"),
                                   vivo=lambda p: True):
                pass

            caminho.write_text("99999")                             # dono parado à força: solta na hora
            with boca_rapida.Trava(caminho, esperar=0, dormir=lambda s: self.fail("não devia esperar"),
                                   vivo=lambda p: p != "99999"):
                self.assertEqual(caminho.read_text(), str(os.getpid()))

            caminho.write_text("ocupada")
            with self.assertRaises(boca_ia.ErroBocaIA):
                with boca_rapida.Trava(caminho, esperar=10, passo=5, dormir=dormir, agora=lambda: relogio[0],
                                       vivo=lambda p: True):
                    pass

    def test_processo_vivo(self):
        self.assertTrue(boca_rapida.processo_vivo(os.getpid()))
        self.assertFalse(boca_rapida.processo_vivo("lixo"))
        self.assertFalse(boca_rapida.processo_vivo(4_000_000))

    def test_comando_chama_o_script_com_cache(self):
        cmd = boca_rapida.comando(Path("C:/mt"), "r.png", "v.wav", "b.mp4", "f.json", 30, "C:/ff/ffmpeg.exe")
        self.assertTrue(cmd[1].endswith("musetalk_rapido.py"))
        self.assertEqual(cmd[cmd.index("--fps") + 1], "30")
        self.assertEqual(Path(cmd[cmd.index("--cache") + 1]), Path("C:/mt/results/jarvis-cache"))

    def test_sincronizar_confere_o_arquivo(self):
        voz, taxa = voz_com_pausa()
        fotos = tuple(np.full((boca_ia.ENTRADA_LADO,) * 2 + (4,), 255, np.uint8) for _ in range(3))
        with tempfile.TemporaryDirectory(prefix="rapida") as tmp, \
                mock.patch("retratos.retratos", return_value=fotos), \
                mock.patch.object(boca_rapida, "gravar_png"):
            tmp = Path(tmp)
            vistos = []

            def falso(cmd, **kw):
                vistos.append((cmd, kw))
                (tmp / "boca.mp4").write_bytes(b"x")
            saida, rosto = boca_rapida.sincronizar("brabo", voz, taxa, tmp, "C:/ff/ffmpeg.exe", 30, tmp, rodar=falso)
            self.assertEqual(saida, tmp / "boca.mp4")
            self.assertEqual(vistos[0][1]["cwd"], tmp)
            self.assertFalse((tmp / ".jarvis-gpu.trava").exists())  # soltou a trava
            self.assertEqual(len((tmp / "fala.json").read_text()) > 0, True)
            with self.assertRaises(boca_ia.ErroBocaIA):
                (tmp / "boca.mp4").unlink()
                boca_rapida.sincronizar("brabo", voz, taxa, tmp, "ff", 30, tmp, rodar=lambda c, **k: None)

    def test_mexer_da_quadros_com_moldura(self):
        voz, taxa = voz_com_pausa()
        img = np.zeros((avatar.LADO, avatar.LADO, 4), np.uint8)
        img[100:300, 100:300] = 255
        quadros = list(boca_rapida.mexer("brabo", [img] * 90, voz, taxa, fundo=True, fps=30))
        self.assertEqual(len(quadros), 90)
        self.assertEqual(quadros[0].dtype, np.uint8)
        self.assertEqual(quadros[0].shape, (avatar.LADO, avatar.LADO, 4))
        self.assertEqual(int(quadros[0][0, 0, 3]), 0)              # canto arredondado da moldura

    def test_sem_ia_vai_a_boca_antiga(self):
        voz, taxa = voz_com_pausa()
        with mock.patch.object(boca_ia, "ligado", return_value=False), \
                mock.patch.object(avatar, "quadros", return_value=iter(["antiga"])) as antiga:
            self.assertEqual(list(boca_rapida.quadros("brabo", voz, taxa, True, "ff", "tmp")), ["antiga"])
            antiga.assert_called_once()


if __name__ == "__main__":
    unittest.main()
