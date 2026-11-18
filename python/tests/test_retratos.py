"""Ticket 055: retratos 3D (fundo verde) no lugar do desenho. Usa imagens falsas feitas aqui mesmo."""

import os
import tempfile
import unittest
from unittest import mock
from pathlib import Path

import numpy as np

import avatar
import cortador
import retratos


def falso(lado, topo, largura, boca, cx=None):
    """Imagem RGB: fundo verde e uma 'cabeça' vermelha (retângulo) com uma 'boca' escura."""
    img = np.zeros((lado, lado, 3), dtype=np.uint8)
    img[...] = (0, 255, 0)
    cx = lado // 2 if cx is None else cx
    img[topo:, cx - largura // 2:cx + largura // 2] = (200, 60, 40)
    img[topo + largura // 2:topo + largura // 2 + boca, cx - 10:cx + 10] = (20, 10, 10)
    return img


class TestRetratos(unittest.TestCase):
    def test_tirar_verde(self):
        rgba = retratos.tirar_verde(falso(100, 20, 40, 5))
        self.assertEqual(rgba.shape, (100, 100, 4))
        self.assertEqual(rgba[0, 0, 3], 0)            # fundo transparente
        self.assertEqual(rgba[60, 50, 3], 255)        # personagem inteiro

    def test_medir_e_encaixar_igualam_a_cabeca(self):
        a = retratos.tirar_verde(falso(200, 40, 80, 6))
        b = retratos.tirar_verde(falso(200, 20, 120, 6, cx=110))   # mais zoom e para o lado
        alvo = retratos.medir(a)
        alinhada = retratos.encaixar(b, retratos.medir(b), alvo)
        topo, cx, larg = retratos.medir(alinhada)
        self.assertAlmostEqual(topo, alvo[0], delta=2)
        self.assertAlmostEqual(cx, alvo[1], delta=2)
        self.assertAlmostEqual(larg, alvo[2], delta=3)

    def test_sem_as_3_imagens_usa_o_desenho(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "brabo").mkdir()
            self.assertIsNone(retratos.arquivos("brabo", tmp))
            self.assertIsNone(retratos.retratos("brabo", 100, tmp))
            with mock.patch.dict(os.environ, {"JARVIS_RETRATOS": tmp}):   # pasta sem imagem → desenho por código
                s = avatar.sprites("brabo")
            self.assertTrue(np.array_equal(s[(0, False)], avatar.desenhar_brabo(0, False)))

    def test_le_imagens_de_verdade_com_ffmpeg(self):
        try:
            ffmpeg = cortador.achar_ffmpeg()
        except Exception:
            self.skipTest("sem FFmpeg")
        import brabo
        with tempfile.TemporaryDirectory() as tmp:
            pasta = Path(tmp, "capi")
            pasta.mkdir()
            for nome, boca in (("fechada", 2), ("meia", 8), ("aberta", 14)):
                rgb = falso(300, 60, 120, boca)
                rgba = np.concatenate([rgb, np.full((300, 300, 1), 255, np.uint8)], axis=-1)
                brabo.gravar_png(ffmpeg, rgba, pasta / f"{nome}.png", tmp)
            fotos = retratos.retratos("capi", 120, tmp)
            self.assertEqual(len(fotos), 3)
            for f in fotos:
                self.assertEqual(f.shape, (120, 120, 4))
                self.assertEqual(f[0, 0, 3], 0)
            moldura = avatar.emoldurar("capi", fotos[0])
            self.assertEqual(moldura.shape, (120, 120, 4))
            self.assertEqual(moldura[60, 2, 3], 255)  # dentro do quadradinho fica opaco

    def test_movimento_boca_suave_e_cabeca_viva(self):
        bocas = np.array([0, 2, 2, 2, 0, 0, 0, 0], dtype=np.int8)
        nivel = np.array([0, 1, 1, 1, 0, 0, 0, 0], dtype=np.float32)
        mov = avatar.movimento(bocas, nivel, fps=30)
        self.assertEqual(len(mov), 8)
        self.assertTrue(0 < mov[1][0] < 2)                 # não pula direto para a boca aberta
        self.assertGreater(mov[1][2], mov[0][2])           # tranco: zoom no começo da fala
        self.assertEqual(mov, avatar.movimento(bocas, nivel, fps=30))   # mesmo vídeo sempre

    def test_quadros_do_retrato(self):
        fotos = []
        for boca in (2, 8, 14):
            rgba = retratos.tirar_verde(falso(80, 16, 40, boca))
            fotos.append(rgba)
        voz = np.sin(np.linspace(0, 400, 22050)).astype(np.float32)
        qs = list(avatar.quadros_retrato("brabo", fotos, voz, 22050, fundo=True, fps=10))
        self.assertEqual(len(qs), 10)
        for q in qs:
            self.assertEqual(q.shape, (80, 80, 4))
            self.assertEqual(q.dtype, np.uint8)
        self.assertEqual(qs[0][0, 0, 3], 0)                # canto arredondado transparente
        self.assertEqual(qs[0][79, 40, 3], 255)            # embaixo sem buraco


if __name__ == "__main__":
    unittest.main()
