"""Testes do achar_golpes (som.py). Rodar na raiz do repositório:
    python -m unittest discover -s .claude/skills/raio-x-video/tests -v
Usa unittest (vem com o Python), igual aos testes de python/tests.
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import som  # noqa: E402

SILENCIO = -80.0
BATIDA = -6.0


def curva(golpes_em: list[int], tamanho: int = 100) -> list[float]:
    """Volume em dB por janela: silêncio com uma batida curta em cada índice pedido."""
    db = [SILENCIO] * tamanho
    for i in golpes_em:
        db[i] = BATIDA
        if i + 1 < tamanho:
            db[i + 1] = -20.0  # o som cai aos poucos depois da batida
    return db


class AcharGolpes(unittest.TestCase):
    def test_batida_no_segundo_zero_conta(self):
        golpes, _ = som.achar_golpes(curva([0, 25, 50, 75]))
        self.assertEqual(golpes[0], 0.0)
        self.assertEqual(len(golpes), 4)

    def test_comeco_em_silencio_nao_cria_golpe_falso(self):
        golpes, _ = som.achar_golpes(curva([25, 50, 75]))
        self.assertNotIn(0.0, golpes)
        self.assertEqual(len(golpes), 3)

    def test_audio_curto_nao_quebra(self):
        for db in ([], [BATIDA], [SILENCIO, BATIDA]):
            self.assertEqual(som.achar_golpes(db), ([], []))

    def test_batida_forte_no_fim_nao_apaga_a_do_comeco(self):
        # pega o bug do subida[-1]: sem o "i == 0 or", a janela 0 seria comparada com a ÚLTIMA (mais forte)
        db = curva([0, 50])
        db[0] = -12.0
        db[99] = -6.0
        golpes, _ = som.achar_golpes(db)
        self.assertIn(0.0, golpes)

    def test_ultima_janela_nao_compara_com_a_primeira(self):
        # batida só na última janela: antes, subida[i + 1] não existia; agora a ponta é tratada
        golpes, _ = som.achar_golpes(curva([99]))
        self.assertEqual(golpes, [round(99 * som.JANELA_S, 3)])


if __name__ == "__main__":
    unittest.main()
