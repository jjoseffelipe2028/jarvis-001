"""Testes do comando do verificador de propostas (Ticket 030): a API é FALSA (não precisa do Java)."""

import contextlib
import io
import unittest

from bracos import ErroBraco
from proposta import formatar, ler_texto, main

PARECER = {
    "nivel": "alto", "pontos": 7, "links": 1,
    "motivos": ["Anexo executável ou compactado com senha: é assim que roubam o canal."],
    "conselho": "Não responda, não baixe nada e não clique.",
    "passos": ["Ache o site oficial da marca.", "Não abra anexo .exe."],
}


class BracosFalsos:
    def __init__(self, resposta):
        self.resposta, self.pedido = resposta, None

    def proposta(self, texto, remetente=None, marca=None):
        self.pedido = (texto, remetente, marca)
        if isinstance(self.resposta, Exception):
            raise self.resposta
        return self.resposta


def rodar(argumentos, linhas, bracos):
    saida = io.StringIO()
    with contextlib.redirect_stdout(saida):
        codigo = main(argumentos, entrada=linhas, bracos=bracos)
    return codigo, saida.getvalue()


class PropostaTest(unittest.TestCase):

    def test_le_ate_a_linha_fim(self):
        self.assertEqual(ler_texto(["Olá!\n", "Parceria paga.\n", "fim\n", "isso não entra\n"]), "Olá!\nParceria paga.")
        self.assertEqual(ler_texto(["sem a palavra final\n"]), "sem a palavra final")
        self.assertEqual(ler_texto([]), "")

    def test_colar_um_livro_nao_enche_a_memoria(self):
        self.assertLess(len(ler_texto(["x" * 1000 + "\n"] * 1000)), 10_000)

    def test_formata_nivel_motivos_conselho_e_passos(self):
        texto = formatar(PARECER)
        self.assertTrue(texto.startswith("🚨 Muita cara de golpe!"))
        self.assertIn("• Anexo executável", texto)
        self.assertIn("Não responda", texto)
        self.assertIn("  2. Não abra anexo .exe.", texto)

    def test_resposta_torta_nao_vira_veredito(self):
        for ruim in (None, [], {"nivel": "ok"}, {"motivos": []}, "alto"):
            with self.assertRaises(ErroBraco):
                formatar(ruim)

    def test_texto_com_escape_sai_limpo(self):
        sujo = dict(PARECER, motivos=["x\x1b[2Jy"], conselho="a\x07b", passos=[1, None, "ok\x1b[0m"])
        texto = formatar(sujo)
        self.assertNotIn("\x1b", texto)
        self.assertNotIn("\x07", texto)

    def test_comando_manda_texto_remetente_e_marca(self):
        b = BracosFalsos(PARECER)
        codigo, saida = rodar(["--remetente", "a@marca.com", "--marca", "Marca"], ["Oi\n", "FIM\n"], b)
        self.assertEqual(codigo, 0)
        self.assertEqual(b.pedido, ("Oi", "a@marca.com", "Marca"))
        self.assertIn("🚨", saida)

    def test_erro_da_api_vira_frase_e_codigo_1(self):
        codigo, saida = rodar([], ["Oi\n"], BracosFalsos(ErroBraco("A API do Jarvis está desligada.")))
        self.assertEqual(codigo, 1)
        self.assertIn("❌ A API do Jarvis está desligada.", saida)


if __name__ == "__main__":
    unittest.main()
