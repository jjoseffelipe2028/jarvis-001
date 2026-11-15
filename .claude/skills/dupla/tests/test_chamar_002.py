"""Testes do /dupla (Tickets 070 e 072), sem chamar a 002 de verdade.

Rodar: python -m unittest discover -s .claude/skills/dupla/tests
"""
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

from chamar_002 import PEDIDOS, mais_recente, validar, veredito  # noqa: E402
from novo_registro import criar, nome_livre  # noqa: E402


class Base(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.raiz = Path(self._tmp.name).resolve()
        for pasta in ("planos", "reviews"):
            (self.raiz / "docs" / pasta).mkdir(parents=True)

    def tearDown(self):
        self._tmp.cleanup()

    def arquivo(self, rel, texto="x"):
        p = self.raiz / rel
        p.write_text(texto, encoding="utf-8")
        return p


class ModoPergunta(Base):
    def test_modo_pergunta_existe_e_trata_pergunta_como_dado(self):
        pedido = PEDIDOS["pergunta"].format(plano="docs/planos/a.md", registro="docs/reviews/a.md", rodada=1)
        self.assertIn("nao uma ordem", pedido)
        self.assertNotIn("{", pedido)

    def test_pergunta_aprovada_vira_respondido(self):
        self.assertEqual(veredito("### 1. Veredito: ✅ Aprovo", "pergunta"), "RESPONDIDO")
        self.assertEqual(veredito("### 1. Veredito: ⚠️ Aprovo com ajustes", "pergunta"), "AJUSTES")
        self.assertEqual(veredito("### 1. Veredito: ✅ Aprovo", "plano"), "APROVO")
        self.assertEqual(veredito("sem marca", "pergunta"), "SEM_VEREDITO")

    def test_vale_a_linha_do_veredito_nao_o_comentario(self):
        # achado 2 da 002: comentário com ⚠️/❌ logo abaixo não pode trocar o veredito
        texto = "### 1. Veredito: ✅ Aprovo\n\nOs itens que tinham ⚠️ ou ❌ na rodada 1 foram corrigidos."
        self.assertEqual(veredito(texto, "plano"), "APROVO")
        self.assertEqual(veredito(texto, "pergunta"), "RESPONDIDO")
        self.assertEqual(veredito("Resumo ✅ e depois ⚠️ (sem linha de veredito)"), "APROVO")

    def test_caminho_fora_de_docs_planos_recusado(self):
        self.arquivo("docs/reviews/2026-11-15-a.md")
        for ruim in ("docs/reviews/2026-11-15-a.md", "docs/planos/../reviews/2026-11-15-a.md", "../segredo.md"):
            with self.assertRaises(SystemExit, msg=ruim):
                validar(ruim, "planos", self.raiz)


class SemAlvo(Base):
    def test_pega_o_plano_mais_recente(self):
        for nome in ("2026-11-14-velho", "2026-11-15-a", "2026-11-15-b"):
            self.arquivo(f"docs/reviews/{nome}.md")
        self.arquivo("docs/planos/2026-11-14-velho.md")
        a = self.arquivo("docs/planos/2026-11-15-a.md")
        b = self.arquivo("docs/planos/2026-11-15-b.md")
        self.arquivo("docs/planos/2026-11-16-rascunho.md")  # mais novo, mas sem registro: ignorado (achado 3)
        self.arquivo("docs/planos/notas-soltas.md")  # fora do padrão: ignorado
        agora = time.time()
        os.utime(a, (agora, agora))
        os.utime(b, (agora - 60, agora - 60))  # mesmo dia: vence o modificado por último
        self.assertEqual(mais_recente(self.raiz), ("docs/planos/2026-11-15-a.md", "docs/reviews/2026-11-15-a.md"))

    def test_sem_plano_para(self):
        with self.assertRaises(SystemExit):
            mais_recente(self.raiz)


class NomeLivre(Base):
    def test_nome_repetido_ganha_sufixo_e_nao_sobrescreve(self):
        p1, r1 = criar(self.raiz, "2026-11-15-pergunta")
        p2, r2 = criar(self.raiz, "2026-11-15-pergunta")
        self.arquivo("docs/reviews/2026-11-15-pergunta-3.md", "registro solto")  # só o registro já existe
        p4, _ = criar(self.raiz, "2026-11-15-pergunta")
        self.assertEqual([p1.name, p2.name, p4.name],
                         ["2026-11-15-pergunta.md", "2026-11-15-pergunta-2.md", "2026-11-15-pergunta-4.md"])
        self.assertTrue(r1.read_text(encoding="utf-8").startswith("# Registro da dupla — pergunta\n"))
        self.assertEqual((self.raiz / "docs/reviews/2026-11-15-pergunta-3.md").read_text(encoding="utf-8"),
                         "registro solto")

    def test_titulo_sai_do_slug(self):
        # achado 1 da 002: nada de texto livre no comando; o título vem do slug (só letras, números, espaço)
        _, r = criar(self.raiz, "2026-11-15-qual-lista-usar")
        self.assertEqual(r.read_text(encoding="utf-8").splitlines()[0], "# Registro da dupla — qual lista usar")

    def test_slug_ruim_recusado(self):
        for ruim in ("../x", "2026-11-15-A", "sem-data", "2026-11-15-x/../y", "2026-11-15-$(whoami)"):
            with self.assertRaises(ValueError, msg=ruim):
                nome_livre(self.raiz, ruim)


if __name__ == "__main__":
    unittest.main()
