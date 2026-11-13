"""Testes do Red Team dos cortes (Ticket 036): os ataques batem no porteiro de verdade, numa pasta temporária."""

import json
import tempfile
import unittest
from pathlib import Path

from redteam_cortes import atacar, gerar, porteiro_python


class RedTeamCortesTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self._tmp.name)
        (self.pasta / "live.mp4").write_bytes(b"0")

    def tearDown(self):
        self._tmp.cleanup()

    def test_porteiro_python_barra_todos_os_ataques_e_libera_os_normais(self):
        falhas = atacar(lambda pedido: porteiro_python(pedido, self.pasta))
        self.assertEqual(falhas, [], json.dumps(falhas, ensure_ascii=False, indent=1))

    def test_lista_tem_todas_as_categorias_e_ids_unicos(self):
        casos = gerar()
        self.assertGreaterEqual(len(casos), 60)
        self.assertEqual(len({c["id"] for c in casos}), len(casos))
        self.assertEqual({c["categoria"] for c in casos},
                         {"normal", "fuga-de-pasta", "injecao-no-ffmpeg", "injecao-no-filtro", "numeros", "tipo-errado"})
        self.assertEqual(sum(1 for c in casos if c["esperado"] == "liberar"), 3)

    def test_cada_pedido_segue_o_contrato(self):
        for c in gerar():
            self.assertEqual(set(c["pedido"]), {"video", "inicioSeg", "fimSeg", "duracaoVideoSeg", "arroba"})

    def test_porteiro_furado_e_percebido(self):
        # Um porteiro que libera tudo: todo ataque tem que aparecer como falha
        ataques = sum(1 for c in gerar() if c["esperado"] == "barrar")
        self.assertEqual(len(atacar(lambda pedido: (True, "ok"))), ataques)
        # Um porteiro que barra tudo: os 3 pedidos normais aparecem como falha
        self.assertEqual(len(atacar(lambda pedido: (False, "nao"))), 3)

    def test_o_ataque_nao_cria_nem_muda_arquivo(self):
        atacar(lambda pedido: porteiro_python(pedido, self.pasta))
        self.assertEqual([p.name for p in self.pasta.iterdir()], ["live.mp4"])
        self.assertEqual((self.pasta / "live.mp4").read_bytes(), b"0")


if __name__ == "__main__":
    unittest.main()
