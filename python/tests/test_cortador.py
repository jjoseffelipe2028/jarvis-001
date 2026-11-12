"""Testes do robô de cortes (Ticket 025). FFmpeg e Whisper FALSOS: não precisa de nada instalado."""

import subprocess
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

import cortador
from cortador import (Cortador, ErroCorte, comando_corte, desarmar_ass, gerar_ass, ler_pasta, liberar,
                      nome_saida, resolver_entrada)


class TestPorteiro(unittest.TestCase):
    """As mesmas 6 regras que o José vai escrever no PorteiroDeCortesTest.java."""

    def test_corte_normal_passa(self):
        self.assertEqual(liberar(60, 85, 3600, "@amigo"), (True, "ok"))

    def test_fim_antes_do_comeco(self):
        self.assertFalse(liberar(85, 60, 3600, "@amigo")[0])

    def test_alem_do_fim_do_video(self):
        self.assertFalse(liberar(3590, 3620, 3600, "@amigo")[0])

    def test_menos_de_13_segundos(self):
        self.assertFalse(liberar(20, 30, 3600, "@amigo")[0])   # 10 s: barrado
        self.assertTrue(liberar(20, 33, 3600, "@amigo")[0])    # 13 s: passa

    def test_mais_de_3_minutos(self):
        self.assertFalse(liberar(0, 181, 3600, "@amigo")[0])
        self.assertTrue(liberar(0, 180, 3600, "@amigo")[0])

    def test_arroba_estranho(self):
        for ruim in ("", "@", "@a b", "@amigo;del", "@{\\b1}", "@" + "a" * 31, None):
            self.assertFalse(liberar(0, 20, 3600, ruim)[0], ruim)
        self.assertTrue(liberar(0, 20, 3600, "amigo_gta.ps5")[0])

    def test_comeco_negativo(self):
        self.assertFalse(liberar(-5, 20, 3600, "@amigo")[0])


class TestEntrada(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self.tmp.name) / "lives"
        self.pasta.mkdir()
        (self.pasta / "live.mp4").write_bytes(b"x")
        (Path(self.tmp.name) / "segredo.mp4").write_bytes(b"x")

    def tearDown(self):
        self.tmp.cleanup()

    def test_nome_simples_passa(self):
        self.assertEqual(resolver_entrada(self.pasta, "live.mp4"), (self.pasta / "live.mp4").resolve())

    def test_path_traversal_e_caminho_sao_recusados(self):
        for ruim in ("../segredo.mp4", "..\\segredo.mp4", str(Path(self.tmp.name) / "segredo.mp4"),
                     "C:segredo.mp4", ".oculto.mp4", "", " live.mp4", "live\x1b.mp4"):
            with self.assertRaises(ErroCorte, msg=ruim):
                resolver_entrada(self.pasta, ruim)

    def test_extensao_fora_da_lista(self):
        (self.pasta / "virus.exe").write_bytes(b"x")
        with self.assertRaises(ErroCorte):
            resolver_entrada(self.pasta, "virus.exe")

    def test_video_que_nao_existe(self):
        with self.assertRaises(ErroCorte):
            resolver_entrada(self.pasta, "nao-existe.mp4")

    def test_pasta_do_env(self):
        self.assertEqual(ler_pasta("X", env={"X": str(self.pasta)}), self.pasta.resolve())
        self.assertIsNone(ler_pasta("X", env={}, arquivo=Path(self.tmp.name) / "sem.env"))
        self.assertIsNone(ler_pasta("X", env={"X": str(self.pasta / "nao-existe")}))
        env = Path(self.tmp.name) / ".env"
        env.write_text(f'JARVIS_TOKEN=abc\nX="{self.pasta}"\n', encoding="utf-8")
        self.assertEqual(ler_pasta("X", env={}, arquivo=env), self.pasta.resolve())


class TestNomeELegenda(unittest.TestCase):
    def test_nome_gerado_aqui_e_nunca_repete(self):
        with tempfile.TemporaryDirectory() as d:
            agora = datetime(2026, 11, 11, 14, 30, 5)
            a = nome_saida(d, 3, agora)
            self.assertEqual(a.name, "2026-11-11-143005-corte-03.mp4")
            a.write_bytes(b"x")
            self.assertEqual(nome_saida(d, 3, agora).name, "2026-11-11-143005-corte-03-2.mp4")

    def test_texto_do_whisper_nao_vira_comando_de_legenda(self):
        self.assertEqual(desarmar_ass("{\\an8\\fs300}oi\nmano"), "(/an8/fs300)oi mano")
        ass = gerar_ass([(0, 2, "{\\pos(0,0)}GG"), (1, 99, "passa do fim")], 20, "@amigo")
        self.assertNotIn("{", ass)
        self.assertIn("Credito,,0,0,0,,@amigo", ass)
        self.assertIn("0:00:01.00,0:00:20.00,Legenda", ass)  # fim cortado na duração

    def test_comando_e_lista_e_nome_malicioso_fica_inteiro(self):
        maldoso = Path("C:/lives/a & del C-x.mp4")
        cmd = comando_corte("ffmpeg", maldoso, 60, 25, Path("C:/cortes/c.mp4"))
        self.assertIsInstance(cmd, list)
        self.assertIn(f"file:{maldoso}", cmd)  # um argumento só, com o "&" dentro
        self.assertIn("-n", cmd)               # nunca sobrescreve
        self.assertIn("-nostdin", cmd)
        self.assertTrue(cmd[cmd.index("-vf") + 1].endswith("subtitles=legenda.ass"))


class ExecutarFalso:
    def __init__(self, codigo=0):
        self.chamadas, self.codigo = [], codigo

    def __call__(self, cmd, **kw):
        self.chamadas.append((cmd, kw))
        if self.codigo == 0 and cmd[-1] == "file:audio.wav":
            Path(kw["cwd"], "audio.wav").write_bytes(b"RIFF")
        return subprocess.CompletedProcess(cmd, self.codigo, b"", b"erro: arquivo quebrado\n")


class TestCortador(unittest.TestCase):
    def test_corte_completo_com_ffmpeg_falso(self):
        execu = ExecutarFalso()
        vistos = {}

        def transcrever(wav):
            vistos["wav"] = Path(wav).name
            return [(0.5, 2.0, "que isso, mano!")]

        with tempfile.TemporaryDirectory() as d:
            saida = Path(d) / "c.mp4"
            robo = Cortador("ffmpeg", transcrever, executar=execu)
            robo.cortar(Path(d) / "live.mp4", 60, 85, 3600, "amigo", saida)
            srt = saida.with_suffix(".srt").read_text(encoding="utf-8")
        self.assertEqual(srt, "1\n00:00:00,500 --> 00:00:02,000\nque isso, mano!\n")
        self.assertEqual(vistos["wav"], "audio.wav")
        self.assertEqual(len(execu.chamadas), 2)
        for cmd, kw in execu.chamadas:
            self.assertIs(kw["shell"], False)
            self.assertIn("-t", cmd)
            self.assertEqual(cmd[cmd.index("-t") + 1], "25")

    def test_porteiro_barra_antes_de_rodar_qualquer_coisa(self):
        execu = ExecutarFalso()
        robo = Cortador("ffmpeg", lambda w: [], executar=execu)
        with self.assertRaises(ErroCorte):
            robo.cortar(Path("live.mp4"), 20, 30, 3600, "@amigo", Path(tempfile.gettempdir()) / "c.mp4")
        self.assertEqual(execu.chamadas, [])

    def test_erro_do_ffmpeg_vira_mensagem_curta(self):
        robo = Cortador("ffmpeg", lambda w: [], executar=ExecutarFalso(codigo=1))
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(ErroCorte, "arquivo quebrado"):
                robo.cortar(Path(d) / "live.mp4", 0, 20, 3600, "@amigo", Path(d) / "c.mp4")
            self.assertFalse((Path(d) / "c.srt").exists())  # corte falhou → sem .srt sozinho

    def test_srt_desarmado_e_dentro_do_corte(self):
        srt = cortador.gerar_srt([(0, 1.5, "{\\b1}oi\nmano"), (3, 99, "fim"), (5, 4, "ao contrário")], 20)
        self.assertEqual(srt, "1\n00:00:00,000 --> 00:00:01,500\n(/b1)oi mano\n\n"
                              "2\n00:00:03,000 --> 00:00:20,000\nfim\n")

    def test_nome_pula_srt_que_ja_existe(self):
        with tempfile.TemporaryDirectory() as d:
            agora = datetime(2026, 11, 11, 14, 30, 5)
            (Path(d) / "2026-11-11-143005-corte-01.srt").write_text("x")
            self.assertEqual(nome_saida(d, 1, agora).name, "2026-11-11-143005-corte-01-2.mp4")

    def test_ler_wav_entrega_numeros_para_o_whisper(self):
        import wave
        with tempfile.TemporaryDirectory() as d:
            ok, ruim = Path(d) / "ok.wav", Path(d) / "ruim.wav"
            for caminho, taxa in ((ok, 16000), (ruim, 44100)):
                with wave.open(str(caminho), "wb") as w:
                    w.setnchannels(1)
                    w.setsampwidth(2)
                    w.setframerate(taxa)
                    w.writeframes(b"\x00\x40" * 160)  # 16384 = metade do volume
            som = cortador.ler_wav(ok)
            self.assertEqual(len(som), 160)
            self.assertAlmostEqual(float(som[0]), 0.5)
            with self.assertRaises(ErroCorte):
                cortador.ler_wav(ruim)

    def test_sem_ffmpeg_avisa_como_instalar(self):
        original = cortador.shutil.which
        cortador.shutil.which = lambda nome: None
        try:
            with self.assertRaisesRegex(ErroCorte, "winget"):
                cortador.achar_ffmpeg()
        finally:
            cortador.shutil.which = original


if __name__ == "__main__":
    unittest.main()
