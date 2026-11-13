"""Testes do catálogo de afiliados (Ticket 042): pasta temporária; nenhum link é aberto."""

import contextlib
import io
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

from afiliados import ErroAfiliado, adicionar, conferir_link, limpar_nome, listar, main, texto_do_post

HOJE = datetime(2026, 11, 13)


class AfiliadosTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def test_link_da_propria_loja_passa(self):
        for link, loja in (("https://amzn.to/abc123", "amazon"), ("https://www.amazon.com.br/dp/B0X?tag=jose-20", "amazon"),
                           ("https://produto.mercadolivre.com.br/MLB-1", "mercadolivre"), ("https://shope.ee/x1", "shopee"),
                           ("https://www.kabum.com.br/produto/1", "kabum")):
            self.assertEqual(conferir_link(link, loja), link)

    def test_link_falso_ou_perigoso_e_recusado(self):
        ruins = [
            ("http://amzn.to/abc", "amazon"),                         # sem https
            ("https://amazon.com.br.promo-top.xyz/dp/1", "amazon"),   # imita a loja
            ("https://amazon-brasil.com/dp/1", "amazon"),
            ("https://amazon.com.br@site-ruim.example/x", "amazon"),  # truque do @
            ("https://192.168.0.5/x", "amazon"),                      # número no lugar do nome
            ("https://xn--amazn-mta.com.br/x", "amazon"),             # letra disfarçada
            ("https://amazоn.com.br/x", "amazon"),                    # "о" russo
            ("https://amzn.to:8443/x", "amazon"),                     # porta estranha
            ("https://amzn.to/a b", "amazon"),                        # espaço
            ("https://amzn.to/a|b", "amazon"),                        # barra de tabela
            ("https://amzn.to/" + "a" * 500, "amazon"),               # grande demais
            ("javascript:alert(1)", "amazon"), ("file:///C:/x", "amazon"), ("", "amazon"), (None, "amazon"),
            ("https://shopee.com.br/x", "amazon"),                    # loja trocada
            ("https://amzn.to/x", "loja-inventada"),
        ]
        for link, loja in ruins:
            with self.subTest(link=link):
                with self.assertRaises(ErroAfiliado):
                    conferir_link(link, loja)

    def test_adiciona_cria_a_nota_e_so_cresce(self):
        adicionar(self.pasta, "Mouse gamer X", "https://amzn.to/abc123", "amazon", HOJE)
        adicionar(self.pasta, "Teclado Y", "https://shope.ee/zz9", "shopee", HOJE)
        nota = (self.pasta / "afiliados.md").read_text(encoding="utf-8")
        self.assertTrue(nota.startswith("# 🔗 Afiliados"))
        self.assertEqual(nota.count("# 🔗 Afiliados"), 1)
        self.assertIn("| 2026-11-13 | amazon | Mouse gamer X | https://amzn.to/abc123 |", nota)
        self.assertEqual([i["produto"] for i in listar(self.pasta)], ["Mouse gamer X", "Teclado Y"])

    def test_link_repetido_e_recusado(self):
        adicionar(self.pasta, "Mouse", "https://amzn.to/abc123", "amazon", HOJE)
        with self.assertRaisesRegex(ErroAfiliado, "já está"):
            adicionar(self.pasta, "Outro nome", "https://amzn.to/abc123", "amazon", HOJE)

    def test_nome_do_produto_e_limpo(self):
        self.assertEqual(limpar_nome("Mouse | <b>top</b> [x](y) `z`"), "Mouse b top /b x (y) z")
        self.assertLessEqual(len(limpar_nome("a" * 300)), 80)
        for vazio in ("", "   ", "|||", None):
            with self.assertRaises(ErroAfiliado):
                limpar_nome(vazio)
        adicionar(self.pasta, "Mouse\n| 2026-01-01 | amazon | falso | https://amzn.to/zz |", "https://amzn.to/a1", "amazon", HOJE)
        self.assertEqual(len(listar(self.pasta)), 1, "quebra de linha no nome não cria linha falsa")

    def test_nota_mexida_a_mao_so_devolve_linha_que_passa_na_conferencia(self):
        (self.pasta / "afiliados.md").write_text(
            "# 🔗 Afiliados\n\n| Data | Loja | Produto | Link |\n|---|---|---|---|\n"
            "| 2026-11-13 | amazon | Bom | https://amzn.to/ok1 |\n"
            "| 2026-11-13 | amazon | Imitação | https://amazon.com.br.golpe.xyz/x |\n"
            "| 2026-11-13 | banco | Loja estranha | https://amzn.to/ok2 |\n"
            "linha solta qualquer\n", encoding="utf-8")
        self.assertEqual([i["produto"] for i in listar(self.pasta)], ["Bom"])
        self.assertEqual(listar(self.pasta / "nao-existe"), [])

    def test_texto_do_post_sempre_tem_o_aviso(self):
        adicionar(self.pasta, "Mouse gamer X", "https://amzn.to/abc123", "amazon", HOJE)
        adicionar(self.pasta, "Mousepad grande", "https://shope.ee/zz9", "shopee", HOJE)
        texto = texto_do_post(listar(self.pasta), "MOUSE")
        self.assertIn("• Mouse gamer X: https://amzn.to/abc123", texto)
        self.assertIn("• Mousepad grande: https://shope.ee/zz9", texto)
        self.assertIn("Link de afiliado", texto)
        for ruim in ("teclado", "", None):
            with self.assertRaises(ErroAfiliado):
                texto_do_post(listar(self.pasta), ruim)

    def test_comando_completo(self):
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            self.assertEqual(main(["adicionar", "Mouse", "https://amzn.to/abc123", "--loja", "amazon"], self.pasta), 0)
            self.assertEqual(main(["adicionar", "Golpe", "https://amazon-brasil.com/x", "--loja", "amazon"], self.pasta), 1)
            self.assertEqual(main(["listar"], self.pasta), 0)
            self.assertEqual(main(["texto", "mouse"], self.pasta), 0)
        texto = saida.getvalue()
        self.assertIn("O link não foi aberto", texto)
        self.assertIn("1 link(s) no catálogo", texto)
        self.assertIn("❌", texto)


if __name__ == "__main__":
    unittest.main()
