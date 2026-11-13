"""Testes do vigia da rede (Ticket 041): a lista da rede é FALSA e o arquivo fica numa pasta temporária."""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from vigia_rede import COMANDO, Aparelho, frase, guardar_conhecidos, ler_arp, ler_conhecidos, main, separar

ARP = """
Interface: 192.168.11.204 --- 0xa
  Endereço IP           Endereço físico       Tipo
  192.168.11.1          aa-bb-cc-11-22-33     dinâmico
  192.168.11.50         AA-BB-CC-44-55-66     dinâmico
  192.168.11.255        ff-ff-ff-ff-ff-ff     estático
  224.0.0.22            01-00-5e-00-00-16     estático
  239.255.255.250       01-00-5e-7f-ff-fa     estático
  255.255.255.255       ff-ff-ff-ff-ff-ff     estático

Interface: 172.20.0.1 --- 0x15
  Internet Address      Physical Address      Type
  172.20.0.9            aa-bb-cc-11-22-33     dynamic
  999.1.1.1             aa-bb-cc-77-88-99     dynamic
  linha torta sem nada
"""


def arp_falso(texto=ARP, codigo=0):
    chamadas = []

    def executar(comando, **opcoes):
        chamadas.append((comando, opcoes))
        return SimpleNamespace(returncode=codigo, stdout=texto)
    executar.chamadas = chamadas
    return executar


class VigiaRedeTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.arquivo = Path(self._tmp.name) / ".rede-conhecida.json"

    def tearDown(self):
        self._tmp.cleanup()

    def _rodar(self, argumentos, executar=None):
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            codigo = main(argumentos, arquivo=self.arquivo, executar=executar or arp_falso())
        return codigo, saida.getvalue()

    def test_le_so_aparelho_de_verdade_sem_repetir(self):
        self.assertEqual(ler_arp(ARP), [Aparelho("192.168.11.1", "aa-bb-cc-11-22-33"),
                                        Aparelho("192.168.11.50", "aa-bb-cc-44-55-66")])
        self.assertEqual(ler_arp(""), [])
        self.assertEqual(ler_arp("lixo " * 1000), [])

    def test_primeira_vez_lista_tudo_e_pede_para_lembrar(self):
        codigo, texto = self._rodar([])
        self.assertEqual(codigo, 0)
        self.assertEqual(texto.count("🆕"), 2)
        self.assertIn("Ainda não tem lista de conhecidos", texto)
        self.assertFalse(self.arquivo.exists(), "sem --lembrar nada é gravado")

    def test_lembrar_e_depois_avisar_do_aparelho_novo(self):
        self.assertEqual(self._rodar(["--lembrar"])[0], 0)
        codigo, texto = self._rodar([])
        self.assertEqual(codigo, 0)
        self.assertIn("2 aparelho(s), todos conhecidos", texto)
        invasor = ARP + "  192.168.11.77         de-ad-be-ef-00-01     dinâmico\n"
        codigo, texto = self._rodar([], arp_falso(invasor))
        self.assertEqual(codigo, 1)
        self.assertIn("🆕 192.168.11.77", texto)
        self.assertIn("1 aparelho(s) NOVO(S) de 3", texto)

    def test_arquivo_torto_ou_mexido_nao_derruba(self):
        for ruim in ("isso não é json", "{}", '{"conhecidos": 5}', '{"conhecidos": ["../x", 7, "aa-bb"]}'):
            self.arquivo.write_text(ruim, encoding="utf-8")
            self.assertEqual(ler_conhecidos(self.arquivo), set())
        self.assertEqual(ler_conhecidos(self.arquivo.with_name("nao-existe.json")), set())

    def test_guardar_soma_com_os_antigos(self):
        guardar_conhecidos([Aparelho("1.1.1.1", "aa-bb-cc-00-00-01")], self.arquivo)
        self.assertEqual(guardar_conhecidos([Aparelho("1.1.1.2", "aa-bb-cc-00-00-02")], self.arquivo), 2)
        self.assertEqual(separar([Aparelho("1.1.1.9", "aa-bb-cc-00-00-09")], ler_conhecidos(self.arquivo))[0],
                         [Aparelho("1.1.1.9", "aa-bb-cc-00-00-09")])

    def test_comando_fixo_sem_shell_e_passivo(self):
        executar = arp_falso()
        self._rodar([], executar)
        self.assertEqual([c for c, _ in executar.chamadas], [["arp", "-a"]])
        self.assertFalse(executar.chamadas[0][1]["shell"])
        self.assertEqual(COMANDO, ["arp", "-a"], "só lê a lista do Windows: não varre a rede")

    def test_erro_do_comando_e_argumento_estranho(self):
        self.assertEqual(self._rodar([], arp_falso(codigo=1))[0], 2)
        self.assertEqual(self._rodar(["--apagar"])[0], 2)
        self.assertEqual(self._rodar(["--lembrar", "x"])[0], 2)

    def test_frases(self):
        self.assertIn("sem rede", frase([], 0, True))
        self.assertIn("todos conhecidos", frase([], 3, False))


if __name__ == "__main__":
    unittest.main()
