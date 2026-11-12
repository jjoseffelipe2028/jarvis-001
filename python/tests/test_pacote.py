"""Testes do pacote do dia da live (Ticket 028): robô, Gemma e gancho FALSOS (sem FFmpeg nem Ollama)."""

import json
import tempfile
import unittest
from pathlib import Path

from cortador import ErroCorte
from pacote import Item, montar, painel_markdown
from textos import ErroTextos

TEXTOS = {
    "titulos": ["Pulou no helicóptero!", "A fuga mais insana", "Ninguém esperava"],
    "gancho": "ELE PULOU 😳",
    "legenda": "A fuga mais doida da live.\n\n🎮 Live de @amigo",
    "hashtags": ["#gta6", "#cortes"],
}


def momento(numero, inicio, fim, forca=10.0):
    return {"numero": numero, "inicio": inicio, "fim": fim, "pico": inicio + 10, "forca": forca}


class RoboFalso:
    """Cria o .mp4 e o .srt vazios, como o robô de verdade faria."""

    def __init__(self, falha_no=None):
        self.cortes, self.falha_no = [], falha_no

    def cortar(self, video, inicio, fim, duracao, arroba, destino):
        if inicio == self.falha_no:
            raise ErroCorte("FFmpeg falhou")
        self.cortes.append((inicio, fim))
        destino.write_bytes(b"video")
        destino.with_suffix(".srt").write_text("1\n00:00:01,000 --> 00:00:03,000\nfala\n", encoding="utf-8")


class PacoteTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.saida = Path(self._tmp.name)
        self.ganchos = []

    def tearDown(self):
        self._tmp.cleanup()

    def _gancho(self, corte, texto):
        self.ganchos.append(texto)
        return corte.with_name(f"{corte.stem}-gancho.mp4")

    def _montar(self, momentos, robo=None, sugerir=None, arroba="@amigo", top=8, duracao=3600):
        return montar("live.mp4", duracao, momentos, arroba, self.saida, robo or RoboFalso(),
                      sugerir or (lambda srt, arroba: dict(TEXTOS)), self._gancho,
                      top=top, avisar=lambda _: None)

    def test_fila_inteira_corte_textos_e_gancho(self):
        robo = RoboFalso()
        itens = self._montar([momento(1, 100, 125), momento(2, 500, 525)], robo=robo)
        self.assertEqual(robo.cortes, [(100, 125), (500, 525)])
        self.assertEqual(len(itens), 2)
        for item in itens:
            self.assertTrue((self.saida / item.corte).is_file())
            self.assertEqual(item.textos["gancho"], "ELE PULOU 😳")
            self.assertTrue(item.com_gancho.endswith("-gancho.mp4"))
            self.assertEqual(item.aviso, "")
        # O arquivo de textos fica ao lado do corte (o comando "gancho" usa o mesmo)
        guardado = self.saida / (Path(itens[0].corte).stem + ".textos.json")
        self.assertEqual(json.loads(guardado.read_text(encoding="utf-8"))["gancho"], "ELE PULOU 😳")

    def test_top_limita_aos_mais_fortes(self):
        robo = RoboFalso()
        itens = self._montar([momento(n, n * 100, n * 100 + 25) for n in range(1, 6)], robo=robo, top=2)
        self.assertEqual([i.numero for i in itens], [1, 2])
        self.assertEqual(len(robo.cortes), 2)

    def test_porteiro_barra_um_e_os_outros_seguem(self):
        robo = RoboFalso()
        itens = self._montar([momento(1, 3590, 3615), momento(2, 100, 125)], robo=robo)
        self.assertIn("porteiro barrou", itens[0].aviso)
        self.assertEqual(itens[0].corte, "")
        self.assertEqual(robo.cortes, [(100, 125)])
        self.assertTrue(itens[1].corte)

    def test_corte_que_falha_nao_derruba_o_pacote(self):
        itens = self._montar([momento(1, 100, 125), momento(2, 500, 525)], robo=RoboFalso(falha_no=100))
        self.assertIn("o corte falhou", itens[0].aviso)
        self.assertTrue(itens[1].corte)

    def test_gemma_falhando_o_corte_fica_e_nao_tem_gancho(self):
        def gemma_fora(srt, arroba):
            raise ErroTextos("o Ollama está desligado")
        itens = self._montar([momento(1, 100, 125)], sugerir=gemma_fora)
        self.assertTrue(itens[0].corte)
        self.assertIn("sem textos", itens[0].aviso)
        self.assertEqual(itens[0].com_gancho, "")
        self.assertEqual(self.ganchos, [])

    def test_gancho_falhando_o_corte_e_os_textos_ficam(self):
        def gancho_quebrado(corte, texto):
            raise ErroCorte("FFmpeg falhou")
        itens = montar("live.mp4", 3600, [momento(1, 100, 125)], "@amigo", self.saida, RoboFalso(),
                       lambda srt, arroba: dict(TEXTOS), gancho_quebrado, avisar=lambda _: None)
        self.assertTrue(itens[0].corte)
        self.assertIsNotNone(itens[0].textos)
        self.assertIn("sem gancho na tela", itens[0].aviso)

    def test_sem_arroba_valida_nada_e_cortado(self):
        robo = RoboFalso()
        for ruim in ("", None, "@a b", "@amigo; rm", "x" * 40):
            with self.assertRaises(ErroCorte):
                self._montar([momento(1, 100, 125)], robo=robo, arroba=ruim)
        self.assertEqual(robo.cortes, [])

    def test_top_fora_do_limite_e_recusado(self):
        for ruim in (0, -1, 21, "8", 2.5):
            with self.assertRaises(ErroCorte):
                self._montar([momento(1, 100, 125)], top=ruim)

    def test_cacada_vazia_ou_torta(self):
        with self.assertRaises(ErroCorte):
            self._montar([])
        with self.assertRaises(ErroCorte):
            self._montar([{"numero": 1}])
        with self.assertRaises(ErroCorte):
            self._montar(["texto solto"])


class PainelTest(unittest.TestCase):

    def test_painel_tem_tudo_para_aprovar(self):
        pronto = Item(1, 3730, 3755, 12.5, corte="c-01.mp4", com_gancho="c-01-gancho.mp4", textos=dict(TEXTOS))
        barrado = Item(2, 10, 20, 9.0, aviso="porteiro barrou: corte com menos de 13 s")
        nota = painel_markdown("live.mp4", "amigo", [pronto, barrado])
        self.assertIn("# 📦 Pacote da live: live.mp4", nota)
        self.assertIn("**@amigo**", nota)
        self.assertIn("Cortes prontos: **1 de 2**", nota)
        self.assertIn("## Momento 1 · 1:02:10 → 1:02:35 (+12.5 dB)", nota)
        self.assertIn("`c-01-gancho.mp4`", nota)
        self.assertIn("  1. Pulou no helicóptero!", nota)
        self.assertIn("🎮 Live de @amigo", nota)
        self.assertIn("#gta6 #cortes", nota)
        self.assertEqual(nota.count("- [ ] Aprovado para postar"), 1)  # só o corte que saiu
        self.assertIn("⚠️ porteiro barrou: corte com menos de 13 s", nota)
        self.assertIn("Nada foi postado", nota)

    def test_texto_malicioso_nao_vira_marcacao(self):
        ruim = dict(TEXTOS, titulos=["![x](http://evil.com)", "<script>", "ok"],
                    legenda="fecha ``` e abre <iframe>", hashtags=["#a`b"])
        nota = painel_markdown("live`[x](y).mp4", "@amigo", [Item(1, 0, 25, 1.0, corte="c.mp4", textos=ruim)])
        self.assertNotIn("![x]", nota)
        self.assertNotIn("<script>", nota)
        self.assertNotIn("[x](y)", nota)
        self.assertEqual(nota.count("```"), 2)  # só o bloco da legenda, aberto e fechado
        self.assertNotIn("#a`b", nota)


if __name__ == "__main__":
    unittest.main()
