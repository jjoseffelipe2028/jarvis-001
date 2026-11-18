"""Testes do anti-molde (Ticket 095): histórico de roteiros e trava de roteiro repetido."""

import tempfile
import unittest
from datetime import datetime
from pathlib import Path

import molde
from molde import ErroMolde, abertura, conferir, ler_historico, normalizar, parecido, registrar

ROTEIRO_A = ("Chegou o Brabo, e hoje ninguém escapa! Esse aqui tentou fugir da polícia de bicicleta "
             "no meio da avenida. Nota três, e olhe lá.")
ROTEIRO_B = ("Chegou o Brabo, e hoje ninguém escapa! O piloto pousou o helicóptero em cima da piscina "
             "do vizinho. Tribunal decide: culpado.")


def reg(texto, arquivo="velho.brabo.json", modo="gta"):
    return {"modo": modo, "arquivo": arquivo, "texto": texto}


class TestComparar(unittest.TestCase):
    def test_normaliza_e_tira_o_bordao(self):
        self.assertEqual(normalizar("Chegou o Brabo, e hoje NINGUÉM escapa! Olhá isso"), "olha isso")
        self.assertEqual(abertura(ROTEIRO_A), "esse aqui tentou fugir da policia")

    def test_parecido(self):
        self.assertEqual(parecido(ROTEIRO_A, ROTEIRO_A), 1.0)
        self.assertLess(parecido(ROTEIRO_A, ROTEIRO_B), 0.1)
        self.assertEqual(parecido("", ROTEIRO_A), 0.0)


class TestConferir(unittest.TestCase):
    def test_roteiro_novo_passa_mesmo_com_o_bordao(self):
        conferir(ROTEIRO_B, "gta", [reg(ROTEIRO_A)])  # não levanta erro

    def test_abertura_repetida_barra(self):
        copia = "Chegou o Brabo! Esse aqui tentou fugir da polícia de jet ski, e caiu."
        with self.assertRaisesRegex(ErroMolde, "abertura"):
            conferir(copia, "gta", [reg(ROTEIRO_A)])

    def test_parecido_demais_barra(self):
        quase = ROTEIRO_A.replace("Esse aqui", "Olha só, o mano").replace("três", "dois")
        with self.assertRaisesRegex(ErroMolde, "parecido"):
            conferir(quase, "gta", [reg(ROTEIRO_A)])

    def test_outro_canal_e_o_mesmo_arquivo_nao_contam(self):
        conferir(ROTEIRO_A, "ensino", [reg(ROTEIRO_A)])                     # Mestre ≠ Brabo
        conferir(ROTEIRO_A, "gta", [reg(ROTEIRO_A, arquivo="x.brabo.json")], arquivo="x.brabo.json")

    def test_so_olha_os_recentes(self):
        velhos = [reg(ROTEIRO_A, "a.json")] + [reg(f"roteiro numero {n} bem diferente dos outros {n}", f"{n}.json")
                                               for n in range(molde.RECENTES)]
        conferir(ROTEIRO_A, "gta", velhos)  # o igual ficou além dos últimos 30


class TestHistorico(unittest.TestCase):
    def test_grava_le_e_pula_linha_estragada(self):
        with tempfile.TemporaryDirectory() as d:
            arq = Path(d) / molde.NOME_HISTORICO
            self.assertEqual(ler_historico(arq), [])
            registrar(arq, ROTEIRO_A, "gta", "a.brabo.json", agora=datetime(2026, 11, 17, 20, 0))
            with open(arq, "a", encoding="utf-8") as f:
                f.write("isso não é json\n[1, 2]\n")
            registrar(arq, ROTEIRO_B, "gta", "b.brabo.json")
            hist = ler_historico(arq)
            self.assertEqual([r["arquivo"] for r in hist], ["a.brabo.json", "b.brabo.json"])
            self.assertEqual(hist[0]["data"], "2026-11-17T20:00:00")
            with self.assertRaises(ErroMolde):
                conferir(ROTEIRO_A, "gta", hist, arquivo="c.brabo.json")


if __name__ == "__main__":
    unittest.main()
