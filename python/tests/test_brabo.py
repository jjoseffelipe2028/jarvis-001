"""Testes do Estúdio do Brabo (Ticket 050): sem FFmpeg, sem voz e sem Gemma de verdade."""

import json
import re
import unittest

import numpy as np

import avatar
import brabo
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

    def test_voz_kokoro_usa_alex_e_fala_mais_rapido(self):
        pedidos = []

        class ModeloFalso:
            def create(self, texto, voice, speed, lang):
                pedidos.append((texto, voice, speed, lang))
                return np.ones(240, np.float32), 24000

        som, taxa = brabo.sintetizar(brabo.VozKokoro(ModeloFalso()), "Chegou o Brabo!", 0.92)
        self.assertEqual((len(som), taxa), (240, 24000))
        self.assertEqual(pedidos, [("Chegou o Brabo!", "pm_alex", 1.09, "pt-br")])

    def test_trilha_nao_passa_do_tamanho(self):
        trilha = montar_trilha([np.ones(100, np.float32) * 2], [(0.99, 1.1)], 100, 1.0)
        self.assertEqual(len(trilha), 101)
        self.assertLessEqual(trilha.max(), 1.0)


class TestSaida(unittest.TestCase):
    def test_ass_tem_fala_tela_e_nota(self):
        r = validar_roteiro(roteiro(nota=3))
        ass = gerar_ass(r, [(0, 2.5), (3, 5)], 10)
        self.assertIn("CHEGOU O BRABO,", re.sub(r"\{[^}]*\}", "", ass))  # sem as marcas do karaoke
        self.assertIn(r"{\k", ass)
        self.assertIn("O BRABO AVALIA", ass)
        self.assertIn("NOTA 3/10", ass)

    def test_efeito_vira_pop_up_e_lista_e_fechada(self):
        base = roteiro()
        base["falas"][0]["efeito"] = "vacilo"
        ass = gerar_ass(validar_roteiro(base), [(0, 2.5), (3, 5)], 10)
        self.assertIn(",Efeito,,", ass)
        self.assertIn("VACILO!", ass)
        for ruim in (r"{\pos(0,0)}hack", "nota11", 3):
            base["falas"][0]["efeito"] = ruim
            with self.assertRaises(ErroBrabo):
                validar_roteiro(base)
        muitos = roteiro()
        muitos["falas"] = [{"em": i, "texto": "oi", "efeito": "kkk"} for i in range(5)]
        with self.assertRaises(ErroBrabo):
            validar_roteiro(muitos)

    def test_comando_ffmpeg_e_lista_sem_sobrescrever(self):
        r = validar_roteiro(roteiro())
        cmd = comando_final("ffmpeg", r, 20, "saida.mp4", corte="C:/x/corte-01.mp4")
        self.assertIsInstance(cmd, list)
        self.assertIn("-n", cmd)
        self.assertIn("file:C:/x/corte-01.mp4", cmd)
        self.assertIn("pipe:0", cmd)
        self.assertTrue(any("amix" in c for c in cmd))

    def test_kit_do_editor_punch_sons_e_voz_na_taxa_certa(self):
        r = validar_roteiro(roteiro())
        cmd = comando_final("ffmpeg", r, 20, "saida.mp4", corte="c.mp4", taxa=24000, tom=1.0,
                            impactos=[3.0], sons=True)
        filtro = cmd[cmd.index("-filter_complex") + 1]
        self.assertIn("file:sons.wav", cmd)
        self.assertIn("eval=frame", filtro)                                  # zoom/tremida/clarão
        self.assertIn("sidechaincompress", filtro)                           # batida abaixa na fala
        self.assertIn("amix=inputs=3", filtro)
        self.assertNotIn("asetrate", filtro)                                 # Kokoro: sem mexer no tom
        self.assertIn("acompressor", filtro)
        piper = comando_final("ffmpeg", r, 20, "saida.mp4", corte="c.mp4")
        self.assertIn(f"asetrate={int(22050 * brabo.MODOS['gta']['tom'])}", piper[piper.index("-filter_complex") + 1])
        self.assertNotIn("file:sons.wav", piper)

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


def ensino(**extra):
    base = {"modo": "ensino", "fundo": "cartoes",
            "falas": [{"em": 0, "texto": "Chuveiro gelado?", "cartao": "gelado"},
                      {"em": 2, "texto": "Desliga o disjuntor.", "cartao": "disjuntor", "passo": 1,
                       "tela": "DESLIGA O DISJUNTOR"},
                      {"em": 4, "texto": "Confere que desligou.", "passo": 1}]}
    base.update(extra)
    return base


class TestCenarioEnsino(unittest.TestCase):
    """O cenário do Mestre: cartão por passo, troca suave, número do passo e barra de progresso."""

    def test_cartao_e_passo_validos_passam(self):
        r = validar_roteiro(ensino())
        self.assertEqual(r["falas"][1]["cartao"], "disjuntor")
        self.assertEqual(r["falas"][2]["passo"], 1)

    def test_recusa_cartao_fora_da_lista_e_passo_errado(self):
        for errado in ({"cartao": "../../segredo"}, {"cartao": ["tomada"]}, {"cartao": 3},
                       {"passo": 0}, {"passo": 10}, {"passo": 1.5}, {"passo": True}, {"passo": "2"}):
            with self.assertRaises(ErroBrabo):
                validar_roteiro(ensino(falas=[{"em": 0, "texto": "oi", **errado}]))

    def test_fala_sem_cartao_repete_o_anterior_e_o_primeiro_e_a_tomada(self):
        r = validar_roteiro(ensino())
        cenas = brabo.cenas_da_tela(r, [(0, 1), (2, 3), (4, 5)])
        self.assertEqual([c[1] for c in cenas], ["gelado", "disjuntor", "disjuntor"])
        antigo = validar_roteiro({"modo": "ensino", "fundo": "cartoes", "falas": [{"em": 0, "texto": "Oi"}]})
        self.assertEqual(brabo.cenas_da_tela(antigo, [(0.5, 1)]), [(0.0, "tomada", None)])

    def test_barra_cresce_com_o_tempo(self):
        def amarelos(p):
            q = brabo.barra(np.zeros((100, 1080, 3), np.uint8), p)
            y0, _, x0, x1 = brabo.BARRA
            return int((q[y0, x0:x1] == (242, 194, 0)).all(axis=1).sum())
        self.assertEqual(amarelos(0.0), 0)
        self.assertLess(amarelos(0.3), amarelos(0.7))
        self.assertEqual(amarelos(1.0), brabo.BARRA[3] - brabo.BARRA[2])

    def test_colar_corta_o_que_sai_da_tela(self):
        q = np.zeros((10, 10, 3), np.uint8)
        img = np.full((6, 6, 4), 255, np.uint8)
        brabo.colar(q, img, -3, 7)
        self.assertTrue((q[7:, :3] == 255).all())
        self.assertTrue((q[:7] == 0).all())

    def test_fade_mistura_os_dois_cartoes(self):
        a, b = np.zeros((2, 2, 3), np.uint8), np.full((2, 2, 3), 200, np.uint8)
        self.assertTrue((brabo.misturar(a, b, 0.0) == 0).all())
        self.assertTrue((brabo.misturar(a, b, 0.5) == 100).all())
        self.assertEqual(brabo.suave(0.0), 0.0)
        self.assertEqual(brabo.suave(1.0), 1.0)

    def test_quadros_tem_tamanho_certo_e_trocam_de_cartao(self):
        r = validar_roteiro(ensino())
        tempos = [(0, 1.5), (2, 3.5), (4, 5)]
        cores = {"gelado": 50, "disjuntor": 200, "tomada": 0}
        cartoes = {n: np.dstack([np.full((brabo.CARTAO_LADO,) * 2 + (3,), c, np.uint8),
                                 np.full((brabo.CARTAO_LADO,) * 2, 255, np.uint8)]) for n, c in cores.items()}
        mestre = {(b, p): np.zeros((brabo.MESTRE_LADO,) * 2 + (4,), np.uint8) for b in (0, 1, 2) for p in (0, 1)}
        quadros = list(brabo.quadros_ensino(r, tempos, 5.0, np.zeros(5 * 1000, np.float32), 1000,
                                            cartoes=cartoes, mestre=mestre, fps=10))
        self.assertEqual(len(quadros), 50)
        self.assertEqual(quadros[0].shape, (brabo.ALTURA, brabo.LARGURA, 3))
        centro = (brabo.CARTAO_Y + 600, brabo.CARTAO_X + 600)
        self.assertEqual(int(quadros[10][centro][0]), 50)          # 1 s: cartão do gancho
        self.assertEqual(int(quadros[30][centro][0]), 200)         # 3 s: já trocou para o disjuntor
        meio = int(quadros[21][centro][0])                         # 2,1 s: no meio da troca
        self.assertTrue(50 < meio < 200)

    def test_ass_tem_numero_do_passo_so_no_cenario(self):
        r = validar_roteiro(ensino())
        ass = gerar_ass(r, [(0, 1.5), (2, 3.5), (4, 5)], 6)
        self.assertEqual(ass.count(",Passo,,"), 2)
        self.assertIn(r"\fad(", ass)
        self.assertIn("}1", ass)
        corte = validar_roteiro(roteiro(modo="ensino", falas=[{"em": 0, "texto": "oi", "passo": 1}]))
        self.assertNotIn(",Passo,,", gerar_ass(corte, [(0, 1)], 3))

    def test_comando_do_cenario_recebe_quadro_inteiro(self):
        r = validar_roteiro(ensino())
        cmd = comando_final("ffmpeg", r, 20, "saida.mp4")
        self.assertIn(f"{brabo.LARGURA}x{brabo.ALTURA}", cmd)
        self.assertIn("rgb24", cmd)
        self.assertNotIn("file:fundo.png", cmd)
        self.assertTrue(any("[1:a]" in c for c in cmd))

    def test_todos_os_cartoes_desenham(self):
        for nome in avatar.CARTOES:
            img = avatar.desenhar_cartao(nome, lado=80)
            self.assertEqual(img.shape, (80, 80, 4))
        with self.assertRaises(ValueError):
            avatar.desenhar_cartao("rede")

    def test_sombra_aparece_fora_do_desenho(self):
        img = np.zeros((60, 60, 4), np.uint8)
        img[10:30, 10:30] = (255, 0, 0, 255)
        com = avatar.com_sombra(img, dx=6, dy=6, raio=2)
        self.assertEqual(int(com[20, 20, 3]), 255)                 # o desenho continua igual
        self.assertGreater(int(com[33, 33, 3]), 0)                 # e ganhou sombra embaixo/à direita
        self.assertEqual(int(com[5, 5, 3]), 0)


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
