"""Testes do conferidor da instalação (Ticket 033): nada é instalado nem lido de verdade."""

import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

import conferir
from conferir import (AVISO, FALTA, OK, chaves_do_env, conferir_env, conferir_java, conferir_ollama,
                      conferir_python, conferir_venv, modelos_do_ollama, versao_java)


class RespostaFalsa:
    def __init__(self, corpo):
        self._corpo = corpo.encode("utf-8")

    def read(self, limite=-1):
        return self._corpo[:limite] if limite and limite > 0 else self._corpo

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False


class ConferirTest(unittest.TestCase):

    def test_versao_do_java(self):
        self.assertEqual(versao_java('openjdk version "21.0.4" 2024-07-16'), 21)
        self.assertEqual(versao_java('java version "1.8.0_391"'), 8)
        self.assertEqual(versao_java('openjdk version "25" 2025-09-16'), 25)
        self.assertIsNone(versao_java("comando não encontrado"))
        self.assertIsNone(versao_java(None))

    def test_env_devolve_so_os_nomes_nunca_os_valores(self):
        texto = ('# comentário\nJARVIS_TOKEN="segredo-falso-de-teste"\nexport JARVIS_PASTA_DAILY=C:/x\n'
                 "VAZIA=\nASPAS_VAZIAS=\"\"\nlinha sem igual\n1RUIM=x\n")
        nomes = chaves_do_env(texto)
        self.assertEqual(nomes, {"JARVIS_TOKEN", "JARVIS_PASTA_DAILY"})
        self.assertNotIn("segredo-falso-de-teste", repr(nomes))

    def test_conferir_env_nunca_mostra_valor(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            self.assertEqual(conferir_env(raiz)[0][0], FALTA)
            (raiz / ".env").write_text("JARVIS_TOKEN=segredo-falso-de-teste\n", encoding="utf-8")
            linhas = conferir_env(raiz)
            self.assertEqual(linhas[0][0], FALTA)
            self.assertIn("JARVIS_PASTA_DAILY", linhas[0][1])
            self.assertNotIn("segredo-falso-de-teste", repr(linhas))
            (raiz / ".env").write_text(
                "JARVIS_TOKEN=x\nJARVIS_PASTA_DAILY=a\nJARVIS_PASTA_GIT=b\n", encoding="utf-8")
            linhas = conferir_env(raiz)
            self.assertEqual(linhas[0][0], OK)
            self.assertEqual(linhas[1][0], AVISO, "as opcionais viram aviso, não erro")

    def test_modelos_do_ollama(self):
        self.assertEqual(modelos_do_ollama('{"models":[{"name":"gemma4:e2b"},{"x":1}]}'), ["gemma4:e2b"])
        for torto in ("", "não é json", "[]", '{"models": 5}', '{"models":[null]}'):
            self.assertEqual(modelos_do_ollama(torto), [])

    def test_ollama_ligado_sem_modelo_e_desligado(self):
        com = lambda url, timeout: RespostaFalsa('{"models":[{"name":"gemma4:e2b"}]}')
        sem = lambda url, timeout: RespostaFalsa('{"models":[{"name":"outro:1"}]}')

        def fora(url, timeout):
            raise OSError("recusado")
        self.assertEqual(conferir_ollama(com)[0], OK)
        self.assertEqual(conferir_ollama(sem)[0], FALTA)
        self.assertEqual(conferir_ollama(fora)[0], FALTA)

    def test_ollama_so_fala_com_o_proprio_pc(self):
        self.assertTrue(conferir.OLLAMA.startswith("http://127.0.0.1:"))

    def test_java_velho_novo_e_ausente(self):
        def java(versao):
            return lambda comando, **o: SimpleNamespace(stderr=f'openjdk version "{versao}"', stdout="")
        with mock.patch("conferir.shutil.which", return_value="C:/java.exe"):
            self.assertEqual(conferir_java(java("21.0.4")), (OK, "Java 21."))
            self.assertEqual(conferir_java(java("17.0.1"))[0], FALTA)
            self.assertEqual(conferir_java(lambda c, **o: SimpleNamespace(stderr="?", stdout=""))[0], AVISO)

            def trava(comando, **o):
                raise subprocess.TimeoutExpired(comando, 20)
            self.assertEqual(conferir_java(trava)[0], AVISO)
        with mock.patch("conferir.shutil.which", return_value=None):
            self.assertEqual(conferir_java()[0], FALTA)

    def test_python_e_venv(self):
        self.assertEqual(conferir_python((3, 9, 0))[0], FALTA)
        self.assertEqual(conferir_python((3, 13, 1))[0], OK)
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            self.assertEqual(conferir_venv(raiz)[0], FALTA)
            alvo = raiz / "python/.venv/bin/python"
            alvo.parent.mkdir(parents=True)
            alvo.write_text("")
            self.assertEqual(conferir_venv(raiz)[0], OK)


if __name__ == "__main__":
    unittest.main()
