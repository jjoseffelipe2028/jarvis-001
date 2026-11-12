"""Testes do V8 (Ticket 027): roteirista com Gemma FALSA e montador sem rodar o FFmpeg."""

import json
import tempfile
import unittest
from pathlib import Path

import historinhas as h

CENA = {"personagens": ["Limão Azedo"], "lugar": "feira de noite", "acao": "esconde um envelope",
        "fala": "Ninguém pode saber que fui eu."}
BOM = {"titulo": "Todo mundo odiava o Limão", "legenda": "Você conhece alguém assim?",
       "cenas": [dict(CENA) for _ in range(9)]}


def gemma(*respostas):
    fila = list(respostas)
    return lambda msgs: json.dumps(fila.pop(0)) if not isinstance(fila[0], str) else fila.pop(0)


class TestRoteiro(unittest.TestCase):
    def test_roteiro_bom(self):
        r = h.validar_roteiro(json.dumps(BOM))
        self.assertEqual(len(r["cenas"]), 9)

    def test_personagem_fora_da_lista_e_recusado(self):
        ruim = json.loads(json.dumps(BOM))
        ruim["cenas"][4]["personagens"] = ["Mickey"]
        with self.assertRaisesRegex(h.ErroHistorinha, "fora da lista"):
            h.validar_roteiro(json.dumps(ruim))

    def test_cenas_faltando_link_e_tamanho(self):
        with self.assertRaises(h.ErroHistorinha):
            h.validar_roteiro(json.dumps(dict(BOM, cenas=BOM["cenas"][:5])))
        with self.assertRaises(h.ErroHistorinha):
            h.validar_roteiro(json.dumps(dict(BOM, legenda="siga www.x.com")))
        longa = json.loads(json.dumps(BOM))
        longa["cenas"][0]["fala"] = "a" * 111
        with self.assertRaises(h.ErroHistorinha):
            h.validar_roteiro(json.dumps(longa))

    def test_descricao_do_personagem_vem_do_codigo(self):
        um = h.prompt_da_cena(CENA)
        self.assertIn("Personagem: Limão Azedo, um limão verde-amarelo", um)
        self.assertIn("vídeo vertical 9:16, 8 segundos", um)
        dois = h.prompt_da_cena(dict(CENA, personagens=["Seu Abacaxi", "Manguinha"]))
        self.assertIn("- Seu Abacaxi: abacaxi idoso", dois)
        self.assertIn("- Manguinha: manga laranja", dois)

    def test_tenta_de_novo_e_tema_e_separado(self):
        r = h.roteirizar("o Limão aprende a dividir", perguntar=gemma("lixo", BOM))
        self.assertEqual(r["titulo"], "Todo mundo odiava o Limão")
        with self.assertRaises(h.ErroHistorinha):
            h.roteirizar("tema qualquer", perguntar=gemma("lixo", "lixo"))
        with self.assertRaises(h.ErroHistorinha):
            h.roteirizar("oi", perguntar=gemma(BOM))

    def test_nota(self):
        nota = h.nota_roteiro("tema", h.validar_roteiro(json.dumps(BOM)))
        self.assertEqual(nota.count("## Cena "), 9)
        self.assertIn("cena01.mp4", nota)


class TestMontador(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.p = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_acha_as_cenas_em_ordem(self):
        for n in ("cena02.mp4", "cena01.mp4", "Cena-03.MP4", "notas.txt"):
            (self.p / n).write_bytes(b"x")
        self.assertEqual([c.name for c in h.achar_cenas(self.p)], ["cena01.mp4", "cena02.mp4", "Cena-03.MP4"])

    def test_cena_faltando_ou_repetida(self):
        for n in ("cena01.mp4", "cena03.mp4"):
            (self.p / n).write_bytes(b"x")
        with self.assertRaisesRegex(h.ErroHistorinha, "Falta a cena 02"):
            h.achar_cenas(self.p)
        (self.p / "cena02.mp4").write_bytes(b"x")
        (self.p / "cena 02.mov").write_bytes(b"x")
        with self.assertRaisesRegex(h.ErroHistorinha, "duas cenas 02"):
            h.achar_cenas(self.p)

    def test_cena_sem_som_ganha_silencio(self):
        cmd = h.comando_juntar("ffmpeg", [(Path("a.mp4"), 8.0, True), (Path("b.mp4"), 8.0, False)], Path("o.mp4"))
        self.assertIn("anullsrc=r=48000:cl=stereo", cmd)
        filtro = cmd[cmd.index("-filter_complex") + 1]
        self.assertIn("concat=n=2:v=1:a=1", filtro)
        self.assertIn("[2:a]aresample", filtro)  # o silêncio é a entrada 2
        self.assertIn("-n", cmd)

    def test_musica_baixinha_e_em_loop(self):
        cmd = h.comando_final("ffmpeg", Path("m.mp4"), Path("o.mp4"), Path("trilha.mp3"))
        self.assertIn("-stream_loop", cmd)
        self.assertIn("volume=0.15", cmd[cmd.index("-filter_complex") + 1])
        self.assertIn("duration=first", cmd[cmd.index("-filter_complex") + 1])

    def test_rotulo_e_legenda(self):
        ass = h.gerar_ass_episodio([(1, 3, "{\\b1}Vô!")], 72, [(8.0, "24 horas antes")])
        self.assertIn("0:00:08.00,0:00:10.50,Credito,,0,0,0,,24 horas antes", ass)
        self.assertNotIn("{\\b1}", ass)
        self.assertEqual(ass.count(",Credito,"), 1)  # sem @: só o rótulo no topo

    def test_pasta_do_episodio_e_musica(self):
        (self.p / "ep01").mkdir()
        (self.p / "trilha.mp3").write_bytes(b"x")
        self.assertEqual(h._pasta_episodio(self.p.resolve(), "ep01"), (self.p / "ep01").resolve())
        for ruim in ("../ep01", "ep 01", "", "C:x"):
            with self.assertRaises(h.ErroHistorinha):
                h._pasta_episodio(self.p.resolve(), ruim)
        self.assertIsNone(h._musica(self.p, None))
        for ruim in ("../trilha.mp3", "virus.exe", "nao-tem.mp3"):
            with self.assertRaises(h.ErroHistorinha):
                h._musica(self.p, ruim)


if __name__ == "__main__":
    unittest.main()
