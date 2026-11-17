"""Testes dos cortes ao vivo (Ticket 094). Sem FFmpeg, sem Whisper, sem internet: tudo falso."""

import tempfile
import unittest
from pathlib import Path

import ao_vivo
from ao_vivo import Vigia, blocos_fechados, comando_gravador, ja_cortado, limpar, validar_url
from cacador import Momento
from cortador import ErroCorte


def _criar(pasta, *numeros):
    for n in numeros:
        Path(pasta, f"bloco_{n:04d}.ts").write_bytes(b"x")


class TestUrl(unittest.TestCase):
    def test_aceita_youtube_twitch_kick(self):
        for url in ("https://www.youtube.com/watch?v=abc", "https://youtu.be/abc",
                    "https://www.twitch.tv/amigo", "https://kick.com/amigo", "https://m.youtube.com/live/x"):
            self.assertTrue(validar_url(url))

    def test_recusa_http_outro_site_e_disfarce(self):
        for url in ("http://twitch.tv/amigo", "https://twitch.tv.golpe.com/x", "https://eviltwitch.tv/x",
                    "file:///C:/Windows", "", None, "twitch.tv/amigo"):
            with self.assertRaises(ErroCorte):
                validar_url(url)

    def test_url_vai_como_argumento_separado(self):
        cmd = comando_gravador("https://twitch.tv/amigo")
        self.assertIn("https://twitch.tv/amigo", cmd)
        self.assertEqual(cmd[-1], "best")


class TestBlocos(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_ultimo_bloco_fica_de_fora_ate_chegar_o_proximo(self):
        _criar(self.pasta, 0, 1, 2)
        Path(self.pasta, "outro.mp4").write_bytes(b"x")
        self.assertEqual([b.name for b in blocos_fechados(self.pasta, set())],
                         ["bloco_0000.ts", "bloco_0001.ts"])

    def test_final_pega_todos_e_pula_os_vistos(self):
        _criar(self.pasta, 0, 1, 2)
        self.assertEqual([b.name for b in blocos_fechados(self.pasta, {"bloco_0000.ts"}, final=True)],
                         ["bloco_0001.ts", "bloco_0002.ts"])

    def test_ordem_por_numero_mesmo_passando_de_9999(self):
        _criar(self.pasta, 9999, 10000)
        self.assertEqual([b.name for b in blocos_fechados(self.pasta, set(), final=True)],
                         ["bloco_9999.ts", "bloco_10000.ts"])

    def test_limpar_deixa_so_os_mais_novos(self):
        _criar(self.pasta, *range(5))
        self.assertEqual(limpar(self.pasta, manter=2), 3)
        self.assertEqual(sorted(p.name for p in self.pasta.iterdir()), ["bloco_0003.ts", "bloco_0004.ts"])

    def test_ja_cortado(self):
        self.assertTrue(ja_cortado(100, [70]))
        self.assertFalse(ja_cortado(100, [55]))


class TestVigia(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self.tmp.name)
        self.juntados, self.cortes, self.avisos = [], [], []
        self.picos = {}  # nome do bloco novo da janela → lista de Momento

    def tearDown(self):
        self.tmp.cleanup()

    def _vigia(self, maximo=2):
        def juntar(blocos, saida):
            self.juntados.append([b.name for b in blocos])
            Path(saida).write_bytes(b"janela")
            return 60 * len(blocos)

        def cacar(janela):
            return self.picos.get(janela.stem.replace("janela-", ""), [])

        def cortar(janela, m, duracao):
            if m.forca < 0:
                raise ErroCorte("porteiro barrou")
            self.cortes.append((janela.name, m.pico))
            return Path(f"corte-{len(self.cortes)}.mp4")

        return Vigia(self.pasta, lambda b: 60, juntar, cacar, cortar, avisar=self.avisos.append, maximo=maximo)

    def test_janela_junta_o_anterior_com_o_novo(self):
        _criar(self.pasta, 0, 1, 2)
        v = self._vigia()
        v.passo()
        self.assertEqual(self.juntados, [["bloco_0000.ts"], ["bloco_0000.ts", "bloco_0001.ts"]])
        v.passo(final=True)
        self.assertEqual(self.juntados[-1], ["bloco_0001.ts", "bloco_0002.ts"])

    def test_pico_repetido_entre_janelas_nao_corta_de_novo(self):
        _criar(self.pasta, 0, 1, 2)
        # pico no segundo 30 do bloco 0 aparece nas duas janelas (30 na 1ª, 30 na 2ª também)
        self.picos["bloco_0000"] = [Momento(1, 20, 45, 30, 9.0)]
        self.picos["bloco_0001"] = [Momento(1, 20, 45, 30, 9.0), Momento(2, 80, 105, 90, 7.0)]
        v = self._vigia()
        feitos = v.passo()
        self.assertEqual([a for a, _, _ in feitos], [30, 90])
        self.assertEqual(len(self.cortes), 2)

    def test_pico_colado_no_fim_espera_a_proxima_janela(self):
        _criar(self.pasta, 0, 1)
        self.picos["bloco_0000"] = [Momento(1, 40, 60, 50, 9.0)]  # 50 + 15 > 60: espera
        v = self._vigia()
        self.assertEqual(v.passo(), [])
        self.assertEqual(self.cortes, [])

    def test_no_final_o_pico_colado_no_fim_e_cortado(self):
        _criar(self.pasta, 0)
        self.picos["bloco_0000"] = [Momento(1, 40, 60, 50, 9.0)]
        self.assertEqual(len(self._vigia().passo(final=True)), 1)

    def test_maximo_por_janela_e_erro_nao_derruba(self):
        _criar(self.pasta, 0, 1)  # a janela do bloco 1 tem 120 s
        self.picos["bloco_0001"] = [Momento(1, 0, 13, 1, -1.0), Momento(2, 0, 25, 10, 8.0),
                                    Momento(3, 50, 75, 60, 7.0), Momento(4, 95, 120, 110, 6.0)]
        feitos = self._vigia(maximo=2).passo(final=True)
        self.assertEqual([m.pico for _, m, _ in feitos], [10, 60])  # erro pulado; parou em 2
        self.assertTrue(any("não cortei" in a for a in self.avisos))

    def test_bloco_ruim_vira_aviso_e_segue(self):
        _criar(self.pasta, 0, 1)
        v = self._vigia()
        v._juntar = lambda b, s: (_ for _ in ()).throw(ErroCorte("bloco quebrado"))
        v.passo(final=True)
        self.assertEqual(v.vistos, {"bloco_0000.ts", "bloco_0001.ts"})
        self.assertEqual(len(self.avisos), 2)

    def test_janela_rascunho_e_apagada(self):
        _criar(self.pasta, 0)
        self.picos["bloco_0000"] = [Momento(1, 0, 25, 10, 8.0)]
        self._vigia().passo(final=True)
        self.assertEqual(list((self.pasta / "janelas").iterdir()), [])

    def test_horario_absoluto_soma_os_blocos(self):
        _criar(self.pasta, 0, 1, 2, 3)
        self.picos["bloco_0003"] = [Momento(1, 70, 95, 80, 8.0)]  # janela = blocos 2+3 → 120 + 80
        feitos = self._vigia().passo(final=True)
        self.assertEqual(feitos[0][0], 200)


class TestNota(unittest.TestCase):
    def test_nota_lista_os_cortes_com_credito(self):
        texto = ao_vivo.nota_markdown("ao-vivo-x", "amigo", [(200, Momento(1, 0, 25, 10, 8.0), Path("c.mp4"))])
        self.assertIn("@amigo", texto)
        self.assertIn("3:20", texto)
        self.assertIn("Nada foi postado", texto)


if __name__ == "__main__":
    unittest.main()
