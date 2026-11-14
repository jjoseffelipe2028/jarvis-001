"""Testes do Estúdio do Brabo (Ticket 050): sem FFmpeg, sem voz e sem Gemma de verdade."""

import json
import unittest

import numpy as np

import avatar
from brabo import (ErroBrabo, agendar, comando_final, gerar_ass, montar_trilha, pedacos, rascunho,
                   texto_do_post, validar_roteiro)


def roteiro(**extra):
    base = {"modo": "gta", "corte": "corte-01.mp4",
            "falas": [{"em": 0, "texto": "Chegou o Brabo, e hoje ninguém escapa!", "tela": "O BRABO AVALIA"},
                      {"em": 3, "texto": "Esse cara achou que moto voava."}]}
    base.update(extra)
    return base


class TestRoteiro(unittest.TestCase):
    def test_roteiro_bom_passa(self):
        r = validar_roteiro(roteiro(nota=7, credito="amigo"))
        self.assertEqual(r["nota"], 7)
        self.assertEqual(r["credito"], "@amigo")
        self.assertEqual(r["som_jogo"], 0.2)

    def test_recusa_link_palavrao_e_tamanho(self):
        for texto in ["entra em www.golpe.com", "que porra é essa", "a" * 200, "", 123]:
            with self.assertRaises(ErroBrabo):
                validar_roteiro(roteiro(falas=[{"em": 0, "texto": texto}]))

    def test_recusa_tempo_fora_de_ordem_e_nota_errada(self):
        with self.assertRaises(ErroBrabo):
            validar_roteiro(roteiro(falas=[{"em": 5, "texto": "b"}, {"em": 1, "texto": "a"}]))
        for nota in (11, -1, 7.5, True, "7"):
            with self.assertRaises(ErroBrabo):
                validar_roteiro(roteiro(nota=nota))

    def test_recusa_modo_fundo_e_arroba_estranhos(self):
        for extra in ({"modo": "hack"}, {"fundo": "rede"}, {"credito": "@a b"}, {"som_jogo": 2},
                      {"corte": None}, {"falas": []}, {"falas": [{"em": 0, "texto": "x"}] * 15}):
            with self.assertRaises(ErroBrabo):
                validar_roteiro(roteiro(**extra))

    def test_cartoes_nao_precisa_de_corte(self):
        r = validar_roteiro({"modo": "ensino", "fundo": "cartoes", "falas": [{"em": 0, "texto": "Oi"}]})
        self.assertIsNone(r["corte"])

    def test_comando_de_legenda_vira_texto(self):
        r = validar_roteiro(roteiro(falas=[{"em": 0, "texto": "oi {\\pos(1,1)} tchau"}]))
        self.assertNotIn("{", r["falas"][0]["texto"])
        self.assertNotIn("\\", r["falas"][0]["texto"])


class TestTempo(unittest.TestCase):
    def test_fala_que_atropela_e_empurrada(self):
        falas = [{"em": 0}, {"em": 1}, {"em": 10}]
        tempos = agendar(falas, [3.0, 2.0, 1.0])
        self.assertEqual(tempos[0], (0, 3.0))
        self.assertAlmostEqual(tempos[1][0], 3.15)
        self.assertEqual(tempos[2], (10, 11.0))

    def test_pedacos_cobrem_a_fala_toda(self):
        blocos = pedacos("um dois tres quatro cinco seis sete", 1.0, 4.0)
        self.assertEqual([b[2] for b in blocos], ["um dois tres", "quatro cinco seis", "sete"])
        self.assertAlmostEqual(blocos[0][0], 1.0)
        self.assertAlmostEqual(blocos[-1][1], 4.0)

    def test_trilha_nao_passa_do_tamanho(self):
        trilha = montar_trilha([np.ones(100, np.float32) * 2], [(0.99, 1.1)], 100, 1.0)
        self.assertEqual(len(trilha), 101)
        self.assertLessEqual(trilha.max(), 1.0)


class TestSaida(unittest.TestCase):
    def test_ass_tem_fala_tela_e_nota(self):
        r = validar_roteiro(roteiro(nota=3))
        ass = gerar_ass(r, [(0, 2.5), (3, 5)], 10)
        self.assertIn("CHEGOU O BRABO,", ass)
        self.assertIn("O BRABO AVALIA", ass)
        self.assertIn("NOTA 3/10", ass)

    def test_comando_ffmpeg_e_lista_sem_sobrescrever(self):
        r = validar_roteiro(roteiro())
        cmd = comando_final("ffmpeg", r, 20, "saida.mp4", corte="C:/x/corte-01.mp4")
        self.assertIsInstance(cmd, list)
        self.assertIn("-n", cmd)
        self.assertIn("file:C:/x/corte-01.mp4", cmd)
        self.assertIn("pipe:0", cmd)
        self.assertTrue(any("amix" in c for c in cmd))

    def test_som_do_jogo_zero_tira_o_jogo(self):
        r = validar_roteiro(roteiro(som_jogo=0))
        cmd = comando_final("ffmpeg", r, 20, "saida.mp4", corte="c.mp4")
        self.assertFalse(any("amix" in c for c in cmd))

    def test_post_lembra_do_rotulo_de_ia(self):
        txt = texto_do_post(validar_roteiro(roteiro(credito="amigo")))
        self.assertIn("sintético", txt)
        self.assertIn("@amigo", txt)


class TestAvatar(unittest.TestCase):
    def test_silencio_fecha_a_boca_e_fala_abre(self):
        taxa = 3000
        voz = np.concatenate([np.zeros(taxa), 0.5 * np.sin(np.arange(taxa) / 3.0)]).astype(np.float32)
        bocas = avatar.bocas_pela_voz(voz, taxa, fps=10)
        self.assertTrue((bocas[:10] == 0).all())
        self.assertTrue((bocas[10:] > 0).all())

    def test_desenho_tem_tamanho_certo(self):
        img = avatar.desenhar_brabo(2, True)
        self.assertEqual(img.shape, (avatar.LADO, avatar.LADO, 4))
        self.assertEqual(img.dtype, np.uint8)


class TestRascunho(unittest.TestCase):
    SRT = "1\n00:00:01,000 --> 00:00:04,000\nNão acredito, ele pulou de moto no helicóptero\n"

    def test_gemma_torta_tenta_de_novo_e_desiste(self):
        respostas = iter(["não é json", json.dumps({"falas": "x"})])
        with self.assertRaises(ErroBrabo):
            rascunho(self.SRT, "c.mp4", lambda m: next(respostas))

    def test_gemma_certa_vira_roteiro_gta(self):
        resp = json.dumps({"titulo": "Moto voadora", "nota": 2, "falas": [{"em": 0, "texto": "Olha isso!"}]})
        r, _ = rascunho(self.SRT, "c.mp4", lambda m: resp)
        self.assertEqual((r["modo"], r["corte"], r["nota"]), ("gta", "c.mp4", 2))

    def test_fala_do_jogador_vai_como_dado(self):
        vistas = []
        resp = json.dumps({"falas": [{"em": 0, "texto": "Oi"}]})
        rascunho(self.SRT, "c.mp4", lambda m: vistas.append(m) or resp)
        self.assertIn("<<<FALA>>>", vistas[0][1]["content"])
        self.assertIn("DADO", vistas[0][0]["content"])


if __name__ == "__main__":
    unittest.main()
