"""Testes do caderno do Brabo (Ticket 096): repertório sorteado para a Gemma."""

import json
import random
import tempfile
import unittest
from pathlib import Path

import brabo
import caderno
from caderno import CATEGORIAS, ErroCaderno, carregar, sortear, tempero, validar


def minimo(**troca):
    dado = {cat: [f"{cat} um", f"{cat} dois", f"{cat} tres", f"{cat} quatro"] for cat in CATEGORIAS}
    dado.update(troca)
    return dado


class TestCaderno(unittest.TestCase):
    def test_o_caderno_de_verdade_e_valido(self):
        c = carregar()
        for cat in CATEGORIAS:
            self.assertGreaterEqual(len(c[cat]), 3, cat)

    def test_item_ruim_e_recusado(self):
        for ruim in ("veja www.golpe.com", "sigam @alguem", "que porra", "a" * 81, "", "{\\fs300}x", 42):
            with self.assertRaises(ErroCaderno, msg=ruim):
                validar(minimo(girias=["ok", ruim]))
        with self.assertRaises(ErroCaderno):
            validar(minimo(quadros=[]))
        with self.assertRaises(ErroCaderno):
            validar([1, 2])

    def test_repetido_some_e_espaco_e_limpo(self):
        self.assertEqual(validar(minimo(girias=["deu  ruim", "deu ruim"]))["girias"], ["deu ruim"])

    def test_sorteio_varia_e_respeita_quantos(self):
        c = validar(minimo())
        s = sortear(c, random.Random(1))
        self.assertEqual(len(s["girias"]), 3)
        self.assertEqual(len(s["quadros"]), 1)
        sorteios = {tempero(sortear(c, random.Random(n))) for n in range(10)}
        self.assertGreater(len(sorteios), 1)  # não sai sempre o mesmo tempero

    def test_arquivo_estragado(self):
        with tempfile.TemporaryDirectory() as d:
            ruim = Path(d) / "c.json"
            ruim.write_text("não é json", encoding="utf-8")
            with self.assertRaises(ErroCaderno):
                carregar(ruim)
            with self.assertRaises(ErroCaderno):
                carregar(Path(d) / "nao-existe.json")


class TestRascunhoComTempero(unittest.TestCase):
    SRT = "1\n00:00:01,000 --> 00:00:03,000\nele pulou de moto no helicóptero\n"
    RESP = json.dumps({"titulo": "Pulo", "nota": 7, "falas": [{"em": 0, "texto": "Que pulo foi esse"}]})

    def test_tempero_vai_nas_instrucoes_e_a_fala_continua_separada(self):
        vistas = []
        t = tempero(sortear(validar(minimo()), random.Random(3)))
        brabo.rascunho(self.SRT, "c.mp4", lambda m: vistas.append(m) or self.RESP, tempero=t)
        sistema, usuario = vistas[0][0]["content"], vistas[0][1]["content"]
        self.assertTrue(sistema.startswith(brabo.INSTRUCOES_GTA))
        self.assertIn("TEMPERO DESTE ROTEIRO", sistema)
        self.assertNotIn("TEMPERO", usuario)

    def test_sem_tempero_fica_como_antes(self):
        vistas = []
        brabo.rascunho(self.SRT, "c.mp4", lambda m: vistas.append(m) or self.RESP)
        self.assertEqual(vistas[0][0]["content"], brabo.INSTRUCOES_GTA)


if __name__ == "__main__":
    unittest.main()
