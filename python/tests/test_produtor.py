"""Testes do super produtor (Ticket 045): cortes e Gemma FALSOS, pasta temporária."""

import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from produtor import traduzir_cortes

SRT = "1\n00:00:01,000 --> 00:00:03,000\nOlha isso, pulou de moto!\n\n"


def gemma(*respostas):
    fila = list(respostas)
    return lambda mensagens: fila.pop(0)


def boa(texto="Look at that, he jumped!"):
    return json.dumps({"linhas": [texto]})


class ProdutorTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def _corte(self, nome, srt=SRT):
        (self.pasta / nome).write_bytes(b"video")
        if srt is not None:
            (self.pasta / nome).with_suffix(".srt").write_text(srt, encoding="utf-8")
        return SimpleNamespace(corte=nome)

    def test_traduz_cada_corte_pronto_e_pula_o_que_nao_saiu(self):
        itens = [self._corte("c-01.mp4"), SimpleNamespace(corte=""), self._corte("c-02.mp4")]
        feitos = traduzir_cortes(itens, self.pasta, "en", gemma(boa("um"), boa("dois")))
        self.assertEqual(feitos, [("c-01.mp4", "c-01.en.srt", ""), ("c-02.mp4", "c-02.en.srt", "")])
        self.assertEqual((self.pasta / "c-02.en.srt").read_text(encoding="utf-8"),
                         "1\n00:00:01,000 --> 00:00:03,000\ndois\n\n")
        self.assertEqual((self.pasta / "c-01.srt").read_text(encoding="utf-8"), SRT, "o original fica igual")

    def test_um_corte_com_problema_nao_derruba_os_outros(self):
        itens = [self._corte("sem-legenda.mp4", srt=None), self._corte("torto.mp4"), self._corte("bom.mp4")]
        feitos = traduzir_cortes(itens, self.pasta, "es", gemma("torta", "torta", boa("hola")))
        self.assertEqual(feitos[0], ("sem-legenda.mp4", "", "sem legenda"))
        self.assertEqual(feitos[1][1], "")
        self.assertIn("falhou 2 vezes", feitos[1][2])
        self.assertEqual(feitos[2], ("bom.mp4", "bom.es.srt", ""))
        self.assertFalse((self.pasta / "torto.es.srt").exists(), "nada pela metade")

    def test_nunca_sobrescreve_traducao_que_ja_existe(self):
        item = self._corte("c-01.mp4")
        (self.pasta / "c-01.en.srt").write_text("minha revisão", encoding="utf-8")
        feitos = traduzir_cortes([item], self.pasta, "en", gemma(boa()))
        self.assertEqual(feitos, [("c-01.mp4", "", "a tradução já existia")])
        self.assertEqual((self.pasta / "c-01.en.srt").read_text(encoding="utf-8"), "minha revisão")

    def test_sem_corte_pronto_nao_chama_a_ia(self):
        def nao_pode(mensagens):
            raise AssertionError("não devia chamar a IA")
        self.assertEqual(traduzir_cortes([SimpleNamespace(corte=""), SimpleNamespace()], self.pasta, "en", nao_pode), [])


if __name__ == "__main__":
    unittest.main()
