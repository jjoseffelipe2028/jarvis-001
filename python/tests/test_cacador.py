"""Testes do caçador de momentos (Ticket 025). Áudio de teste gerado aqui, com um "grito" falso."""

import tempfile
import unittest
from pathlib import Path

import av
import numpy as np

from cacador import (ErroCacador, Momento, achar_picos, energia_por_segundo, estouros, hms, janela_tiktok, ler_hms,
                     nota_fala, nota_markdown, reordenar)


class TestJanelaTiktok(unittest.TestCase):
    """Ticket 089: a versão TikTok tem que passar de 1 min, senão o Creator Rewards não paga."""

    def test_meio_do_video_70_s_com_pico_dentro(self):
        self.assertEqual(janela_tiktok(1000, 3600), (955, 1025))

    def test_perto_do_comeco_escorrega_para_frente(self):
        self.assertEqual(janela_tiktok(5, 3600), (0, 70))

    def test_perto_do_fim_escorrega_para_tras(self):
        self.assertEqual(janela_tiktok(3595, 3600), (3530, 3600))

    def test_video_curto_nao_tem_versao(self):
        self.assertIsNone(janela_tiktok(20, 50))
        self.assertEqual(janela_tiktok(30, 62), (0, 62))  # exatamente o mínimo ainda vale

    def test_nunca_passa_do_fim_nem_fica_negativa(self):
        for pico in (0, 1, 44, 45, 3555, 3599, 3600):
            inicio, fim = janela_tiktok(pico, 3600)
            self.assertGreaterEqual(inicio, 0)
            self.assertLessEqual(fim, 3600)
            self.assertGreater(fim - inicio, 60)


def gravar_wav(caminho, segundos, gritos=(), taxa=8000):
    """Ruído baixo o tempo todo + rajadas altas nos segundos de `gritos`."""
    rng = np.random.default_rng(42)
    som = rng.normal(0, 0.01, segundos * taxa)
    for g in gritos:
        som[g * taxa:(g + 2) * taxa] = rng.normal(0, 0.5, 2 * taxa)
    pcm = (np.clip(som, -1, 1) * 32767).astype(np.int16).reshape(1, -1)
    with av.open(str(caminho), "w", format="wav") as c:
        s = c.add_stream("pcm_s16le", rate=taxa, layout="mono")
        quadro = av.AudioFrame.from_ndarray(pcm, format="s16", layout="mono")
        quadro.sample_rate = taxa
        for pacote in s.encode(quadro):
            c.mux(pacote)
        for pacote in s.encode(None):
            c.mux(pacote)


class TestEnergia(unittest.TestCase):
    def test_acha_o_grito_no_audio_de_verdade(self):
        with tempfile.TemporaryDirectory() as d:
            wav = Path(d) / "live.wav"
            gravar_wav(wav, 120, gritos=(70,))
            energias = energia_por_segundo(wav)
            self.assertEqual(len(energias), 120)
            momentos = achar_picos(energias)
            self.assertEqual(len(momentos), 1)
            self.assertIn(momentos[0].pico, (69, 70, 71))
            self.assertEqual(momentos[0].numero, 1)

    def test_nao_altera_o_original(self):
        with tempfile.TemporaryDirectory() as d:
            wav = Path(d) / "live.wav"
            gravar_wav(wav, 30, gritos=(10,))
            antes = wav.read_bytes()
            energia_por_segundo(wav)
            self.assertEqual(wav.read_bytes(), antes)

    def test_arquivo_que_nao_e_video(self):
        with tempfile.TemporaryDirectory() as d:
            falso = Path(d) / "falso.mp4"
            falso.write_text("isso não é vídeo")
            with self.assertRaises(ErroCacador):
                energia_por_segundo(falso)


class TestPicos(unittest.TestCase):
    def test_silencio_nao_tem_momento(self):
        self.assertEqual(achar_picos([-40.0] * 600), [])
        self.assertEqual(achar_picos([]), [])

    def test_mais_forte_vem_primeiro_e_respeita_distancia(self):
        e = [-40.0] * 600
        e[100] = e[101] = -20.0   # +20 dB
        e[110] = e[111] = -10.0   # +30 dB, mas a 10 s do outro → vira um só
        e[400] = e[401] = -25.0   # +15 dB
        m = achar_picos(e)
        self.assertEqual([x.numero for x in m], [1, 2])
        self.assertIn(m[0].pico, (110, 111))
        self.assertIn(m[1].pico, (400, 401))

    def test_maximo_de_momentos(self):
        e = [-40.0] * 3000
        for i in range(100, 3000, 100):
            e[i] = e[i + 1] = -10.0
        self.assertEqual(len(achar_picos(e, maximo=5)), 5)

    def test_corte_sugerido_fica_dentro_do_video(self):
        e = [-40.0] * 300
        e[2] = e[3] = -5.0
        e[298] = e[299] = -5.0
        for m in achar_picos(e):
            self.assertGreaterEqual(m.inicio, 0)
            self.assertLessEqual(m.fim, 300)


class TestTempoENota(unittest.TestCase):
    def test_hms(self):
        self.assertEqual(hms(65), "1:05")
        self.assertEqual(hms(3725), "1:02:05")
        self.assertEqual(ler_hms("1:02:05"), 3725)
        self.assertEqual(ler_hms("45"), 45)
        for ruim in ("", "1:-2", "a:b", "1:2:3:4", "1.5"):
            with self.assertRaises(ValueError):
                ler_hms(ruim)

    def test_nome_do_video_e_desarmado_na_nota(self):
        nota = nota_markdown("live](javascript:x)|<script>.mp4", [], 60)
        self.assertNotIn("](", nota)
        self.assertNotIn("<script>", nota)
        self.assertIn("rádio do GTA", nota)


class TestFalas(unittest.TestCase):
    """Ticket 092: a fala do candidato reordena a caçada (Whisper FALSO)."""

    def test_nota_da_fala(self):
        self.assertEqual(nota_fala("NÃO ACREDITO mano, kkkkk"), (6.0, ("não acredito", "kkkkk")))
        self.assertEqual(nota_fala("clipa clipa clipa")[0], 3.0)  # repetida conta 1 vez
        self.assertEqual(nota_fala("que isso, caraca, meu deus, socorro, hahaha")[0], 9.0)  # teto de 3
        self.assertEqual(nota_fala("vamos pro banco")[0], 0.0)
        self.assertEqual(nota_fala(None), (0.0, ()))
        self.assertEqual(nota_fala("kk")[0], 0.0)  # "kk" é pouco para ser risada

    def test_fala_forte_passa_na_frente_e_renumera(self):
        a = Momento(1, 90, 115, 100, 12.0)
        b = Momento(2, 490, 515, 500, 8.0)
        falas = {100: "andando de carro", 500: "NÃO ACREDITO, clipa isso kkkk"}
        r = reordenar([a, b], lambda m: falas[m.pico])
        self.assertEqual([(m.numero, m.pico) for m in r], [(1, 500), (2, 100)])
        self.assertEqual(r[0].bonus, 9.0)
        self.assertEqual(r[0].forca, 8.0)  # o volume original fica guardado

    def test_falha_ao_ouvir_nao_derruba_e_corta_no_maximo(self):
        def ouvir(m):
            if m.pico == 100:
                raise RuntimeError("FFmpeg falhou")
            return ""
        avisos = []
        ms = [Momento(n, n * 100 - 10, n * 100 + 15, n * 100, 20.0 - n) for n in range(1, 6)]
        r = reordenar(ms, ouvir, maximo=3, avisar=avisos.append)
        self.assertEqual([m.pico for m in r], [100, 200, 300])
        self.assertEqual(len(avisos), 1)

    def test_nota_mostra_as_palavras(self):
        m = Momento(1, 90, 115, 100, 8.0, bonus=3.0, palavras=("clipa",))
        self.assertIn("🗣️ clipa", nota_markdown("live.mp4", [m], 3600))


class TestEstouros(unittest.TestCase):
    """Ticket 093: estouro seco (tiro) nos 7 s iniciais, com áudio de verdade gerado aqui."""

    def _wav(self, pasta, estalos):
        taxa = 8000
        t = np.arange(10 * taxa) / taxa
        som = 0.01 * np.sin(2 * np.pi * 220 * t)  # fundo baixinho
        for s in estalos:  # 30 ms de estouro
            ini = int(s * taxa)
            som[ini:ini + 240] = 0.9 * np.sign(np.sin(2 * np.pi * 900 * t[ini:ini + 240]))
        caminho = Path(pasta) / "corte.wav"
        with av.open(str(caminho), "w") as c:
            s = c.add_stream("pcm_s16le", rate=taxa, layout="mono")
            q = av.AudioFrame.from_ndarray((som * 32767).astype(np.int16).reshape(1, -1),
                                           format="s16", layout="mono")
            q.sample_rate = taxa
            for pacote in s.encode(q):
                c.mux(pacote)
            for pacote in s.encode(None):
                c.mux(pacote)
        return caminho

    def test_acha_o_estalo_e_ignora_depois_dos_7_s(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(estouros(self._wav(d, [2.0, 8.5])), [2])

    def test_som_constante_nao_e_tiro(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(estouros(self._wav(d, [])), [])


if __name__ == "__main__":
    unittest.main()
