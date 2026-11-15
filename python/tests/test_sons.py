import unittest

import numpy as np

import sons


class TestSons(unittest.TestCase):
    def test_trilha_tem_o_tamanho_do_video_e_nao_estoura(self):
        faixa = sons.trilha_de_sons(10.0, impactos=[3.0], nota_em=7.0)
        self.assertEqual(len(faixa), int(10.0 * sons.TAXA) + 1)
        self.assertLessEqual(float(np.abs(faixa).max()), 1.0)
        self.assertEqual(faixa.dtype, np.float32)

    def test_batida_para_no_impacto_e_soco_toca(self):
        sem = sons.trilha_de_sons(10.0)
        com = sons.trilha_de_sons(10.0, impactos=[5.0])
        a, b = int(5.0 * sons.TAXA), int(5.3 * sons.TAXA)
        self.assertGreater(float(np.abs(com[a:b]).max()), 0.3)               # o soco
        depois = slice(int(5.4 * sons.TAXA), int(5.55 * sons.TAXA))
        self.assertLess(float(np.abs(com[depois]).max()), float(np.abs(sem[depois]).max()) + 1e-6)  # batida parada

    def test_mesma_entrada_mesmo_som(self):
        self.assertTrue(np.array_equal(sons.trilha_de_sons(4.0, [2.0], 1.0), sons.trilha_de_sons(4.0, [2.0], 1.0)))

    def test_expressoes_do_punch(self):
        self.assertIsNone(sons.expressoes_punch([]))
        zoom, tx, ty, luz = sons.expressoes_punch([1.5, 4.0])
        self.assertIn("t-1.500", zoom)
        self.assertIn("t-4.000", luz)
        self.assertNotIn(";", zoom + tx + ty + luz)                        # nada que quebre o filtro do FFmpeg


if __name__ == "__main__":
    unittest.main()
