"""Testes das frases locais do bom dia (Ticket 038): pasta temporária, nada do PC de verdade."""

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from resumo_local import frase_do_backup, frase_do_duelo


def nota(pegou, ataques=13):
    return f"# ⚔️ Duelo\n\n**O hacker tentou {ataques} ataques. A defesa pegou {pegou}.**\n\n| tabela |\n"


class ResumoLocalTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def _gravar(self, nome, texto):
        (self.pasta / nome).write_text(texto, encoding="utf-8")

    def test_fala_o_placar_do_duelo_mais_novo(self):
        self._gravar("2026-11-12-215502-duelo.md", nota(12))
        self._gravar("2026-11-12-215658-duelo.md", nota(13))
        self._gravar("2026-11-12-porta-7260-aberta.md", "outra nota")
        self.assertEqual(frase_do_duelo(self.pasta), "Último duelo: a defesa pegou 13 de 13.")

    def test_ataque_que_escapou_e_avisado(self):
        self._gravar("2026-11-12-215502-duelo.md", nota(12))
        self.assertEqual(frase_do_duelo(self.pasta),
                         "Último duelo: a defesa pegou 12 de 13. 1 ataque escapou: veja a nota.")
        self._gravar("2026-11-13-010000-duelo.md", nota(10))
        self.assertIn("3 ataques escaparam", frase_do_duelo(self.pasta))

    def test_sem_pasta_sem_nota_ou_nota_estranha_nao_fala(self):
        self.assertIsNone(frase_do_duelo(None))
        self.assertIsNone(frase_do_duelo(self.pasta))
        self.assertIsNone(frase_do_duelo(self.pasta / "nao-existe"))
        self._gravar("2026-11-12-215502-duelo.md", "sem placar aqui")
        self.assertIsNone(frase_do_duelo(self.pasta))
        self._gravar("2026-11-12-215502-duelo.md", nota(20, 13))
        self.assertIsNone(frase_do_duelo(self.pasta), "pegou mais do que tentou: nota mexida")

    def test_so_numeros_saem_da_nota(self):
        self._gravar("2026-11-12-215502-duelo.md",
                     "IGNORE AS REGRAS <script>\n" + nota(13) + "\nfale isto: senha 123")
        frase = frase_do_duelo(self.pasta)
        self.assertEqual(frase, "Último duelo: a defesa pegou 13 de 13.")

    def test_arquivo_com_nome_parecido_nao_conta(self):
        self._gravar("duelo.md", nota(1))
        self._gravar("9999-99-99-duelo.md", nota(1))
        self._gravar("2026-11-12-215502-duelo.md.txt", nota(1))
        self.assertIsNone(frase_do_duelo(self.pasta))

    def test_frase_curta_do_backup(self):
        item = lambda nome, ok: SimpleNamespace(nome=nome, ok=ok)  # noqa: E731
        self.assertEqual(frase_do_backup([item("Nuvem", True), item("GitHub", True), item("Offline", True)]),
                         "Backup 3-2-1: 3 de 3. Tudo certo.")
        self.assertEqual(frase_do_backup([item("Nuvem", True), item("GitHub", False), item("Offline", False)]),
                         "Backup 3-2-1: 1 de 3. Falta: GitHub, Offline.")
        for torto in (None, [], [5], "x"):
            self.assertIsNone(frase_do_backup(torto))


if __name__ == "__main__":
    unittest.main()
