"""Testes do Guarda do Backup 3-2-1 (Ticket 032): git FALSO e pastas temporárias."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from backup321 import avaliar, cofre_na_nuvem, estado_do_git, frase, idade_da_copia

EM_DIA = {"sem_commit": 0, "sem_push": 0}


def git_falso(status="", atras="0\n", codigo=0):
    chamadas = []

    def executar(comando, **opcoes):
        chamadas.append((comando, opcoes))
        saida = status if "status" in comando else atras
        return SimpleNamespace(returncode=codigo, stdout=saida)
    executar.chamadas = chamadas
    return executar


class Backup321Test(unittest.TestCase):

    def test_cofre_dentro_ou_fora_do_onedrive(self):
        self.assertTrue(cofre_na_nuvem(Path("C:/Users/x/OneDrive/Documentos/Cofre/daily")))
        self.assertTrue(cofre_na_nuvem(Path("C:/Users/x/OneDrive - Empresa/Cofre")))
        self.assertFalse(cofre_na_nuvem(Path("C:/Users/x/Documentos/Cofre")))
        self.assertFalse(cofre_na_nuvem(Path("C:/Users/x/MeuOneDriveFalso/Cofre")))
        self.assertIsNone(cofre_na_nuvem(None))

    def test_git_em_dia_e_atrasado(self):
        self.assertEqual(estado_do_git("repo", git_falso()), EM_DIA)
        self.assertEqual(estado_do_git("repo", git_falso(status=" M a.py\n?? b.py\n", atras="3\n")),
                         {"sem_commit": 2, "sem_push": 3})

    def test_git_roda_sem_shell_sem_rede_e_com_comando_fixo(self):
        executar = git_falso()
        estado_do_git("C:/repo", executar)
        comandos = [c for c, _ in executar.chamadas]
        self.assertEqual(comandos, [["git", "-C", "C:/repo", "status", "--porcelain"],
                                    ["git", "-C", "C:/repo", "rev-list", "--count", "@{u}..HEAD"]])
        for _, opcoes in executar.chamadas:
            self.assertFalse(opcoes["shell"])
            self.assertEqual(opcoes["env"]["GIT_TERMINAL_PROMPT"], "0")
        self.assertFalse(any("fetch" in c or "pull" in c for c in comandos))

    def test_git_com_problema_vira_nao_sei(self):
        self.assertIsNone(estado_do_git(None, git_falso()))
        self.assertIsNone(estado_do_git("repo", git_falso(codigo=128)))
        self.assertIsNone(estado_do_git("repo", git_falso(atras="abc\n")))

        def trava(comando, **opcoes):
            raise subprocess.TimeoutExpired(comando, 15)
        self.assertIsNone(estado_do_git("repo", trava))

    def test_idade_da_copia_offline(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(idade_da_copia(tmp), "pasta vazia não é backup")
            velho, novo = Path(tmp, "velho.txt"), Path(tmp, "sub", "novo.txt")
            novo.parent.mkdir()
            velho.write_text("x")
            novo.write_text("x")
            agora = 1_800_000_000
            os.utime(velho, (agora - 30 * 86400, agora - 30 * 86400))
            os.utime(novo, (agora - 3 * 86400 - 60, agora - 3 * 86400 - 60))
            self.assertEqual(idade_da_copia(tmp, agora), 3, "vale o arquivo mais novo")
        self.assertIsNone(idade_da_copia(None))
        self.assertIsNone(idade_da_copia(Path(tmp) / "nao-existe"))

    def test_tres_de_tres(self):
        itens = avaliar(True, EM_DIA, 2)
        self.assertTrue(all(i.ok for i in itens))
        self.assertEqual(frase(itens), "Backup 3-2-1: 3 de 3. Tudo certo.")

    def test_dois_de_tres_falta_o_offline(self):
        texto = frase(avaliar(True, EM_DIA, None))
        self.assertTrue(texto.startswith("Backup 3-2-1: 2 de 3. Falta: falta a cópia offline"), texto)

    def test_na_duvida_nao_da_o_ponto(self):
        itens = avaliar(None, None, 30)
        self.assertEqual([i.ok for i in itens], [False, False, False])
        texto = frase(itens)
        self.assertIn("0 de 3", texto)
        self.assertIn("30 dias", texto)

    def test_repositorio_atrasado_diz_o_que_falta(self):
        itens = avaliar(False, {"sem_commit": 2, "sem_push": 1}, 7)
        self.assertEqual([i.ok for i in itens], [False, False, True])
        self.assertIn("2 arquivo(s) sem commit e 1 commit(s) sem push", itens[1].frase)
        self.assertIn("só existe neste PC", itens[0].frase)


if __name__ == "__main__":
    unittest.main()
