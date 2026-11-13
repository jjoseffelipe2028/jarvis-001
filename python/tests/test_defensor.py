"""Testes do super defensor (Ticket 044): os vigias são FALSOS (nada do PC é lido)."""

import contextlib
import io
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

from defensor import ALERTA, ATENCAO, FALHA, OK, frase_geral, main, nota_geral, nota_markdown, ronda


def vigia(nivel, frase="tudo em ordem"):
    return lambda: (nivel, frase)


def explode():
    raise RuntimeError("quebrou")


class DefensorTest(unittest.TestCase):

    def test_tudo_certo(self):
        r = ronda([("Sentinela", vigia(OK)), ("Rede", vigia(OK))])
        self.assertEqual(nota_geral(r), OK)
        self.assertEqual(frase_geral(r), "Defensor: tudo certo na ronda.")

    def test_a_nota_geral_e_a_do_pior_vigia(self):
        r = ronda([("Sentinela", vigia(OK)), ("Processos", vigia(ALERTA, "1 suspeito")), ("Backup", vigia(ATENCAO))])
        self.assertEqual(nota_geral(r), ALERTA)
        self.assertEqual(frase_geral(r), "Defensor: ALERTA em Processos. Veja isso antes de tudo.")
        r = ronda([("Sentinela", vigia(OK)), ("Backup", vigia(ATENCAO)), ("Rede", vigia(ATENCAO))])
        self.assertEqual(frase_geral(r), "Defensor: sem alerta, mas com atenção em Backup, Rede.")

    def test_vigia_quebrado_nao_derruba_e_nao_some(self):
        r = ronda([("Sentinela", explode), ("Processos", vigia(ALERTA, "minerador")), ("Rede", vigia(OK))])
        self.assertEqual([x.nivel for x in r], [FALHA, ALERTA, OK])
        self.assertIn("Não deu para conferir: Sentinela.", frase_geral(r))
        self.assertIn("ALERTA em Processos", frase_geral(r))

    def test_so_falha_nao_vira_tudo_certo(self):
        r = ronda([("Sentinela", explode), ("Rede", explode)])
        self.assertEqual(nota_geral(r), FALHA)
        self.assertEqual(frase_geral(r), "Defensor: nenhum alerta visto. Não deu para conferir: Sentinela, Rede.")
        self.assertEqual(nota_geral([]), FALHA)

    def test_resposta_estranha_de_vigia_vira_falha(self):
        for torto in (lambda: None, lambda: ("ok",), lambda: ("otimo", "x"), lambda: (OK, ""), lambda: (OK, 5)):
            self.assertEqual(ronda([("V", torto)])[0].nivel, FALHA)

    def test_nota_tem_tabela_e_texto_limpo(self):
        r = ronda([("Rede", vigia(ATENCAO, "aparelho | novo `x` <script>\nlinha 2"))])
        nota = nota_markdown(r, datetime(2026, 11, 13, 2, 0))
        self.assertIn("# 🛡️ Ronda do defensor (13/11/2026 02:00)", nota)
        self.assertIn("| ⚠️ | Rede | aparelho   novo 'x'  script  linha 2 |", nota)
        self.assertNotIn("<script>", nota)

    def test_comando_grava_a_nota_uma_vez_e_devolve_1_so_com_alerta(self):
        with tempfile.TemporaryDirectory() as tmp:
            pasta, agora, saida = Path(tmp), datetime(2026, 11, 13, 2, 0, 5), io.StringIO()
            with contextlib.redirect_stdout(saida):
                self.assertEqual(main([("Rede", vigia(ATENCAO))], pasta, agora), 0)
                self.assertEqual(main([("Processos", vigia(ALERTA))], pasta, agora), 1)
                self.assertEqual(main([("Sentinela", explode)], None, agora), 0)
            notas = list(pasta.iterdir())
            self.assertEqual([n.name for n in notas], ["2026-11-13-020005-ronda.md"])
            self.assertIn("atenção em Rede", notas[0].read_text(encoding="utf-8"), "a 2ª ronda não sobrescreveu a 1ª")
        self.assertIn("não consegui gravar", saida.getvalue())


if __name__ == "__main__":
    unittest.main()
