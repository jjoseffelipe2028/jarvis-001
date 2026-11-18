"""Ticket 097 — os olhos do Brabo (Gemma falsa: nada sai do PC, nada demora)."""

import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import brabo  # noqa: E402
import olhos  # noqa: E402
import textos  # noqa: E402


def resposta(cena, nota):
    return json.dumps({"cena": cena, "nota": nota})


class TestQuandoUsar(unittest.TestCase):
    def test_srt_vazio_precisa(self):
        self.assertTrue(olhos.precisa_dos_olhos(""))
        self.assertTrue(olhos.precisa_dos_olhos("1\n00:00:00,000 --> 00:00:01,000\nah\n"))

    def test_srt_com_fala_nao_precisa(self):
        self.assertFalse(olhos.precisa_dos_olhos("1\n00:00:00,000 --> 00:00:03,000\nolha esse carro voando mano\n"))


class TestValidar(unittest.TestCase):
    def test_certo(self):
        self.assertEqual(olhos.validar(resposta("Pulando do prédio", 8.6)), ("Pulando do prédio", 9))

    def test_errados(self):
        for r in ["oi", "[1]", resposta("", 5), resposta("x" * 200, 5), resposta("veja www.golpe.com", 5),
                  resposta("ok", 11), resposta("ok", -1), resposta("ok", "9"), resposta("ok", True),
                  resposta("fala @fulano", 5), resposta("que merda", 5)]:
            with self.assertRaises(olhos.ErroOlhos, msg=r):
                olhos.validar(r)

    def test_desarma_legenda(self):
        cena, _ = olhos.validar(resposta("{\\pos(1,1)}correndo", 3))
        self.assertNotIn("{", cena)
        self.assertNotIn("\\", cena)


class TestOlhar(unittest.TestCase):
    def test_pula_foto_torta_e_guarda_tempo(self):
        respostas = iter(["lixo", "lixo", resposta("Correndo", 5), resposta("Caindo do penhasco", 9)])
        cenas = olhar = olhos.olhar([(0.0, b"a"), (2.0, b"b"), (4.0, b"c")], lambda img: next(respostas),
                                    avisar=lambda *_: None)
        self.assertEqual([c["em"] for c in cenas], [2.0, 4.0])
        self.assertEqual(olhar[-1]["nota"], 9)

    def test_nada_entendido(self):
        with self.assertRaises(olhos.ErroOlhos):
            olhos.olhar([(0.0, b"a")], lambda img: "lixo", avisar=lambda *_: None)

    def test_ollama_desligado_para_tudo(self):
        def falha(img):
            raise olhos.ErroOlhos("A Gemma não respondeu. O Ollama está ligado?")
        with self.assertRaises(olhos.ErroOlhos):
            olhos.olhar([(0.0, b"a"), (2.0, b"b")], falha, avisar=lambda *_: None)


class TestMomentoEpico(unittest.TestCase):
    def test_sequencia_vence_pico_isolado(self):
        cenas = [{"em": 0, "cena": "a", "nota": 2}, {"em": 2, "cena": "b", "nota": 8}, {"em": 4, "cena": "c", "nota": 1},
                 {"em": 6, "cena": "d", "nota": 7}, {"em": 8, "cena": "e", "nota": 8}, {"em": 10, "cena": "f", "nota": 7}]
        self.assertEqual(olhos.momento_epico(cenas)["em"], 8)

    def test_vazio(self):
        self.assertIsNone(olhos.momento_epico([]))

    def test_texto(self):
        t = olhos.texto_das_cenas([{"em": 0, "cena": "Correndo", "nota": 4}, {"em": 64, "cena": "Explosão", "nota": 9}])
        self.assertIn("[1:04.0] nota 9 — Explosão", t)
        self.assertIn("MOMENTO ÉPICO: 1:04.0 (nota 9) — Explosão", t)


class TestCenasDoCorte(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.corte = Path(self.tmp.name) / "c-01.mp4"
        self.corte.write_bytes(b"video")
        self.chamadas = []

    def tearDown(self):
        self.tmp.cleanup()

    def rodar(self, cmd, **kw):
        self.chamadas.append((cmd, kw))
        saida = Path(cmd[-1].removeprefix("file:")).parent
        for i in range(3):
            (saida / f"q-{i + 1:02d}.jpg").write_bytes(b"jpg")
        return SimpleNamespace(returncode=0)

    def test_olha_salva_e_depois_so_le(self):
        notas = iter([3, 9, 5])
        perguntar = lambda img: resposta("Cena", next(notas))  # noqa: E731
        cenas = olhos.cenas_do_corte(self.corte, ffmpeg="ffmpeg", perguntar=perguntar, avisar=lambda *_: None,
                                     rodar=self.rodar, duracao=6)
        self.assertEqual([c["nota"] for c in cenas], [3, 9, 5])
        cmd, kw = self.chamadas[0]
        self.assertFalse(kw["shell"])
        self.assertIn("-n", cmd)
        arq_json, arq_txt = olhos.arquivos_cenas(self.corte)
        self.assertIn("MOMENTO ÉPICO: 0:02.0", arq_txt.read_text(encoding="utf-8"))
        self.assertIn("nunca ordem", arq_txt.read_text(encoding="utf-8"))
        # 2ª vez: lê o cache, não chama FFmpeg nem Gemma
        de_novo = olhos.cenas_do_corte(self.corte, perguntar=None, rodar=None, duracao=6)
        self.assertEqual(de_novo, cenas)
        self.assertEqual(len(self.chamadas), 1)
        self.assertFalse(any(Path(self.tmp.name).glob("*quadros*")))  # fotos só na pasta temporária

    def test_cache_estragado_e_ignorado(self):
        arq_json, _ = olhos.arquivos_cenas(self.corte)
        arq_json.write_text("{lixo", encoding="utf-8")
        self.assertIsNone(olhos.ler_cenas(self.corte))

    def test_ffmpeg_falhou(self):
        with self.assertRaises(olhos.ErroOlhos):
            olhos.cenas_do_corte(self.corte, ffmpeg="ffmpeg", perguntar=None, avisar=lambda *_: None,
                                 rodar=lambda cmd, **kw: SimpleNamespace(returncode=1), duracao=6)


CENAS = "[0:00.0] nota 4 — Correndo\n[0:06.0] nota 9 — Caindo do penhasco\nMOMENTO ÉPICO: 0:06.0 (nota 9) — Caindo"
TEXTOS_OK = json.dumps({"titulos": ["Quase caiu", "Que queda", "Olha isso"], "gancho": "QUASE CAIU 😱",
                        "legenda": "Ele quase caiu do penhasco.", "hashtags": ["#games", "#queda", "#corte"]})


class TestCenasNosTextos(unittest.TestCase):
    def test_sem_fala_usa_cenas(self):
        vistos = []
        s = textos.sugerir("", "amigo", perguntar=lambda m: vistos.append(m) or TEXTOS_OK, cenas=CENAS)
        self.assertEqual(s["gancho"], "QUASE CAIU 😱")
        self.assertIn("<<<CENAS>>>", vistos[0][1]["content"])
        self.assertIn("MOMENTO ÉPICO", vistos[0][1]["content"])
        self.assertIn("NÃO tem fala", vistos[0][0]["content"])

    def test_sem_fala_e_sem_cenas_continua_erro(self):
        with self.assertRaises(textos.ErroTextos):
            textos.sugerir("", "amigo", perguntar=lambda m: TEXTOS_OK)

    def test_com_fala_ignora_cenas(self):
        vistos = []
        textos.sugerir("1\n00:00:00,000 --> 00:00:03,000\nolha esse carro voando mano\n", "amigo",
                       perguntar=lambda m: vistos.append(m) or TEXTOS_OK, cenas=CENAS)
        self.assertIn("<<<FALA>>>", vistos[0][1]["content"])

    def test_cenas_desarmadas(self):
        vistos = []
        textos.sugerir("", "amigo", perguntar=lambda m: vistos.append(m) or TEXTOS_OK, cenas="{\\an8}pulando\n")
        self.assertNotIn("{", vistos[0][1]["content"])


class TestCenasNoRascunho(unittest.TestCase):
    ROTEIRO = json.dumps({"titulo": "Quase caiu", "nota": 7,
                          "falas": [{"em": 0, "texto": "Olha ele correndo"}, {"em": 6, "texto": "QUASE CAIU!"}]})

    def test_sem_fala_usa_cenas_e_momento_epico(self):
        vistos = []
        _, dado = brabo.rascunho("", "c.mp4", lambda m: vistos.append(m) or self.ROTEIRO, cenas=CENAS)
        self.assertEqual(dado["corte"], "c.mp4")
        self.assertIn("MOMENTO ÉPICO", vistos[0][0]["content"])
        self.assertIn("<<<CENAS>>>", vistos[0][1]["content"])

    def test_sem_fala_e_sem_cenas_continua_erro(self):
        with self.assertRaises(brabo.ErroBrabo):
            brabo.rascunho("", "c.mp4", lambda m: self.ROTEIRO)


if __name__ == "__main__":
    unittest.main()
