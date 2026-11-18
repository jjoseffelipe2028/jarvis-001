"""Testes do Quiz de Cyber por voz (Ticket 086). Sem microfone, sem voz e sem API de verdade."""

import contextlib
import io
import random
import unittest

import quiz_cyber as q
from quiz_cyber import BANCO, LETRAS, entender, jogar


def respostas(*textos):
    fila = list(textos)
    return lambda: fila.pop(0) if fila else ""


class QuizCyberTest(unittest.TestCase):

    def test_banco_bem_formado(self):
        self.assertGreaterEqual(len(BANCO), 10)
        for p in BANCO:
            with self.subTest(p.texto):
                self.assertEqual(len(p.opcoes), 3)
                self.assertIn(p.certa, LETRAS)
                self.assertTrue(p.porque.strip())
        self.assertEqual(len({p.texto for p in BANCO}), len(BANCO), "pergunta repetida")
        # Gabarito variado: não pode ser sempre a mesma letra (dava para chutar)
        self.assertGreater(len({p.certa for p in BANCO}), 1)

    def test_entende_jeitos_de_responder(self):
        p = BANCO[0]
        casos = {"b": "B", "B": "B", "letra B.": "B", "A resposta é cê": "C", "opção a": "A", "bê": "B",
                 "  c  ": "C", "para que nenhum outro aparelho": "B"}
        for texto, esperado in casos.items():
            with self.subTest(texto):
                self.assertEqual(entender(texto, p), esperado)

    def test_resposta_estranha_vira_none(self):
        for texto in ("", None, "rm tudo; apaga", "talvez", "x" * 5000, "abc"):
            with self.subTest(texto):
                self.assertIsNone(entender(texto, BANCO[0]))

    def test_jogar_conta_acertos_e_explica_o_porque(self):
        perguntas = BANCO[:3]
        falado = []
        certas = [p.certa for p in perguntas]
        errada = next(l for l in LETRAS if l != certas[2])
        acertos = jogar(perguntas, respostas(certas[0], certas[1], errada), falado.append)
        self.assertEqual(acertos, 2)
        self.assertTrue(falado[0].startswith("Pergunta 1."))
        self.assertIn("Certo! " + perguntas[0].porque, falado)
        self.assertIn(f"Era a {certas[2]}. {perguntas[2].porque}", falado)
        self.assertEqual(falado[-1], "Você acertou 2 de 3. Bom! Revise as que errou.")

    def test_nao_entendeu_pergunta_de_novo_uma_vez(self):
        falado = []
        acertos = jogar([BANCO[0]], respostas("hã?", BANCO[0].certa), falado.append)
        self.assertEqual(acertos, 1)
        self.assertIn("Não entendi. Responda A, B ou C.", falado)

    def test_sorteio_sem_repetir(self):
        rodada = q.sortear(aleatorio=random.Random(7))
        self.assertEqual(len(rodada), 5)
        self.assertEqual(len(set(rodada)), 5)

    def test_main_anota_so_com_anotar(self):
        class BracosFalso:
            def __init__(self):
                self.textos = []

            def registrar_daily(self, texto):
                self.textos.append(texto)

        b = BracosFalso()
        with contextlib.redirect_stdout(io.StringIO()):
            q.main([], responder=respostas(), falar=lambda t: None, bracos=b)
            self.assertEqual(b.textos, [])
            q.main(["--anotar"], responder=respostas(), falar=lambda t: None, bracos=b, aleatorio=random.Random(1))
        self.assertEqual(b.textos, ["Quiz de Cyber: 0/5"])

    def test_api_desligada_nao_estraga_o_quiz(self):
        class Quebrado:
            def registrar_daily(self, texto):
                raise ConnectionError("desligada")
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            self.assertEqual(q.main(["--anotar"], responder=respostas(), falar=lambda t: None, bracos=Quebrado()), 0)
        self.assertIn("não consegui anotar", saida.getvalue())

    def test_argumento_desconhecido(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(q.main(["--apagar"], responder=respostas(), falar=print), 2)


if __name__ == "__main__":
    unittest.main()
