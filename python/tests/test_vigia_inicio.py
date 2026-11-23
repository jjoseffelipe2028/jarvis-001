"""Testes do vigia da inicialização (Ticket 083): registro e pastas FALSOS; o arquivo fica numa pasta temporária."""

import contextlib
import inspect
import io
import json
import tempfile
import unittest
from pathlib import Path

import vigia_inicio
from vigia_inicio import CHAVES, Item, comparar, frase, guardar_conhecidos, ler_conhecidos, main, olhar, resumo

REGISTRO = {
    ("HKCU", CHAVES[0][1]): [("OneDrive", r'"C:\Users\fulano\AppData\Local\Microsoft\OneDrive\OneDrive.exe" /background')],
    ("HKLM", CHAVES[2][1]): [("SecurityHealth", r"%windir%\system32\SecurityHealthSystray.exe")],
}


def registro_falso(dados=None):
    dados = REGISTRO if dados is None else dados
    lidas = []

    def ler(raiz, chave):
        lidas.append((raiz, chave))
        return list(dados.get((raiz, chave), []))
    ler.lidas = lidas
    return ler


def opcoes(dados=None, arquivos=("Spotify.lnk",)):
    return {"ler_registro": registro_falso(dados), "pastas": [("Pasta Inicializar (usuário)", "falsa")],
            "listar": lambda pasta: list(arquivos)}


class VigiaInicioTest(unittest.TestCase):

    def setUp(self):
        self.pasta = tempfile.TemporaryDirectory()
        self.arquivo = Path(self.pasta.name) / "conhecidos.json"

    def tearDown(self):
        self.pasta.cleanup()

    def _main(self, argv, **kw):
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            codigo = main(argv, arquivo=self.arquivo, **{**opcoes(), **kw})
        return codigo, saida.getvalue()

    def test_olhar_junta_registro_e_pasta(self):
        itens = olhar(**opcoes())
        self.assertEqual([i.nome for i in itens], ["OneDrive", "SecurityHealth", "Spotify.lnk"])
        self.assertEqual(itens[0].onde, "HKCU Run")
        self.assertEqual(itens[2].onde, "Pasta Inicializar (usuário)")

    def test_le_so_a_lista_fixa_de_chaves(self):
        ler = registro_falso()
        olhar(ler_registro=ler, pastas=[], listar=lambda p: [])
        self.assertEqual(ler.lidas, CHAVES)

    def test_fonte_que_falha_nao_derruba_o_resto(self):
        def quebrado(raiz, chave):
            raise OSError("sem acesso")
        itens = olhar(ler_registro=quebrado, pastas=[("P", "x")], listar=lambda p: ["a.lnk"])
        self.assertEqual([i.nome for i in itens], ["a.lnk"])

    def test_primeira_vez_pede_para_lembrar(self):
        codigo, saida = self._main([])
        self.assertEqual(codigo, 0)
        self.assertIn("rode com --lembrar", saida)

    def test_lembrar_e_depois_tudo_conhecido(self):
        self.assertEqual(self._main(["--lembrar"])[0], 0)
        codigo, saida = self._main([])
        self.assertEqual(codigo, 0)
        self.assertIn("3 item(ns), todos conhecidos", saida)

    def test_item_novo_avisa_e_sai_com_1(self):
        self._main(["--lembrar"])
        codigo, saida = self._main([], **opcoes(arquivos=("Spotify.lnk", "atualizador.vbs")))
        self.assertEqual(codigo, 1)
        self.assertIn("🆕 [Pasta Inicializar (usuário)] atualizador.vbs", saida)
        self.assertIn("1 NOVO(S)", saida)
        self.assertIn("O Jarvis não mexe em nada", saida)

    def test_mesmo_nome_com_programa_trocado_e_alarme(self):
        # Truque de malware: manter o nome "OneDrive" e trocar o programa que roda
        self._main(["--lembrar"])
        trocado = dict(REGISTRO)
        trocado[("HKCU", CHAVES[0][1])] = [("OneDrive", r"C:\Users\Public\od.exe")]
        codigo, saida = self._main([], **opcoes(dados=trocado))
        self.assertEqual(codigo, 1)
        self.assertIn("⚠️ mudou [HKCU Run] OneDrive", saida)
        self.assertIn("1 que MUDOU(ARAM) de programa", saida)

    def test_item_que_saiu_so_informa(self):
        self._main(["--lembrar"])
        codigo, saida = self._main([], **opcoes(arquivos=()))
        self.assertEqual(codigo, 0)
        self.assertIn("➖ saiu: Pasta Inicializar (usuário)|Spotify.lnk", saida)

    def test_arquivo_guarda_so_nome_e_hash_nunca_o_caminho(self):
        guardar_conhecidos(olhar(**opcoes()), self.arquivo)
        bruto = self.arquivo.read_text(encoding="utf-8")
        self.assertNotIn("fulano", bruto)
        self.assertNotIn("OneDrive.exe", bruto)
        dados = json.loads(bruto)["conhecidos"]
        self.assertIn("HKCU Run|OneDrive", dados)
        self.assertRegex(dados["HKCU Run|OneDrive"], r"^[0-9a-f]{16}$")

    def test_arquivo_torto_vira_vazio(self):
        for conteudo in ("lixo", "[]", '{"conhecidos": {"a": "nao-e-hash"}}', '{"conhecidos": 5}'):
            self.arquivo.write_text(conteudo, encoding="utf-8")
            self.assertEqual(ler_conhecidos(self.arquivo), {}, conteudo)

    def test_nome_com_truque_de_direcao_e_limpo(self):
        # U+202E (RLO) faz "exe.txt" aparecer como "txt.exe" na tela
        itens = olhar(**opcoes(dados={}, arquivos=("nota\u202etxt.exe", "linha\nfalsa")))
        self.assertEqual([i.nome for i in itens], ["nota_txt.exe", "linha_falsa"])

    def test_comparar_e_frase(self):
        a, b = Item("X", "a", "1"), Item("X", "b", "2")
        novos, mudaram, iguais, sumiram = comparar([a, b], {a.chave: a.resumo, "X|c": "0" * 16})
        self.assertEqual((novos, mudaram, iguais, sumiram), ([b], [], [a], ["X|c"]))
        self.assertIn("todos conhecidos", frase([], [], 2, False))

    def test_resumo_so_numeros(self):
        self._main(["--lembrar"])
        r = resumo(self.arquivo, **opcoes(arquivos=("Spotify.lnk", "novo.lnk")))
        self.assertEqual(r, {"total": 4, "novos": 1, "mudaram": 0, "sumiram": 0, "temLista": True})

    def test_uso_errado(self):
        self.assertEqual(self._main(["--apagar"])[0], 2)

    def test_so_leitura_no_codigo(self):
        # Guarda: o vigia nunca escreve no registro, nunca roda programa e nunca apaga arquivo.
        fonte = inspect.getsource(vigia_inicio)
        for proibido in ("SetValue", "DeleteValue", "DeleteKey", "CreateKey", "KEY_WRITE", "KEY_ALL_ACCESS",
                         "subprocess", "os.system", "startfile", "unlink", "rmtree", ".remove("):
            self.assertNotIn(proibido, fonte)


if __name__ == "__main__":
    unittest.main()
