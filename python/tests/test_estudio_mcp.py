import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import estudio_mcp as e  # noqa: E402
from brabo import ErroBrabo  # noqa: E402

ROTEIRO = {"modo": "ensino", "fundo": "cartoes", "titulo": "Chuveiro",
           "falas": [{"em": 0, "texto": "Desliga o disjuntor primeiro.", "tela": "1. DESLIGA O DISJUNTOR"}]}


class TestEstudioMcp(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_lista_separa_cortes_roteiros_e_prontos(self):
        for n in ("a.mp4", "a-brabo.mp4", "a.brabo.json", "x.txt"):
            (self.pasta / n).write_text("x")
        lista = e.listar_cortes(self.pasta)
        self.assertEqual(lista["cortes"], ["a.mp4"])
        self.assertEqual(lista["roteiros"], ["a.brabo.json"])
        self.assertEqual(lista["shorts_prontos"], ["a-brabo.mp4"])

    def test_salva_e_le_roteiro(self):
        r = e.salvar_roteiro(self.pasta, "chuveiro.mestre.json", ROTEIRO)
        self.assertEqual(r["modo"], "ensino")
        self.assertEqual(e.ler_roteiro(self.pasta, "chuveiro.mestre.json")["titulo"], "Chuveiro")

    def test_nao_sobrescreve(self):
        e.salvar_roteiro(self.pasta, "c.mestre.json", ROTEIRO)
        with self.assertRaises(ErroBrabo):
            e.salvar_roteiro(self.pasta, "c.mestre.json", ROTEIRO)

    def test_sufixo_tem_que_bater_com_modo(self):
        with self.assertRaises(ErroBrabo):
            e.salvar_roteiro(self.pasta, "c.brabo.json", ROTEIRO)

    def test_recusa_caminho(self):
        for nome in ("../c.mestre.json", "C:\\x\\c.mestre.json", ".c.mestre.json", "c.json"):
            with self.assertRaises(ErroBrabo):
                e.salvar_roteiro(self.pasta, nome, ROTEIRO)
        self.assertEqual(list(self.pasta.iterdir()), [])

    def test_roteiro_invalido_nao_grava(self):
        ruim = dict(ROTEIRO, falas=[{"em": 0, "texto": "veja em www.golpe.com"}])
        with self.assertRaises(ErroBrabo):
            e.salvar_roteiro(self.pasta, "r.mestre.json", ruim)
        self.assertFalse((self.pasta / "r.mestre.json").exists())

    def test_fazer_usa_nome_certo_e_nao_posta(self):
        e.salvar_roteiro(self.pasta, "chuveiro.mestre.json", ROTEIRO)
        chamado = {}

        def falso(roteiro, pasta, nome):
            chamado["nome"] = nome
            return Path(pasta) / nome

        r = e.fazer_short(self.pasta, "chuveiro.mestre.json", fazer=falso)
        self.assertEqual(chamado["nome"], "chuveiro-mestre.mp4")
        self.assertIn("Nada foi postado", r["lembrete"])

    # O pacote "mcp" só existe no PC do José (requirements.txt); o robô do CI não instala.
    @unittest.skipUnless(importlib.util.find_spec("mcp"), "pacote mcp não instalado (CI)")
    def test_servidor_nao_tem_ferramenta_de_postar(self):
        import asyncio
        srv = e.criar_servidor()
        nomes = {t.name for t in asyncio.run(srv.list_tools())}
        self.assertEqual(nomes, {"listar", "ler", "salvar", "rascunho", "fazer", "ver_personagem"})
        self.assertFalse(any("post" in n or "upload" in n for n in nomes))


if __name__ == "__main__":
    unittest.main()
