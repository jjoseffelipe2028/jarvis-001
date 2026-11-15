"""Testes do relatório semanal de segurança (Ticket 084). Vigias e gitleaks FALSOS; a nota vai para uma pasta temporária."""

import contextlib
import io
import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace

import relatorio_semanal as rs
from defensor import ALERTA, ATENCAO, OK

AGORA = datetime(2026, 11, 15, 20, 0)


def gitleaks_falso(achados=(), codigo=0, escrever=True):
    chamadas = []

    def executar(comando, **opcoes):
        chamadas.append((comando, opcoes))
        if escrever:
            caminho = Path(comando[comando.index("--report-path") + 1])
            caminho.write_text(json.dumps(list(achados)), encoding="utf-8")
        return SimpleNamespace(returncode=codigo, stdout="", stderr="")
    executar.chamadas = chamadas
    return executar


class RelatorioSemanalTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self.tmp.name)
        self.exe = self.pasta / "gitleaks.exe"
        self.exe.write_text("falso", encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def _main(self, vigias):
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            codigo = rs.main(vigias=vigias, pasta=self.pasta, agora=AGORA)
        return codigo, saida.getvalue()

    # ---------- gitleaks ----------

    def test_gitleaks_comando_fixo_sem_shell_e_redigido(self):
        executar = gitleaks_falso()
        self.assertEqual(rs.rodar_gitleaks(self.exe, raiz=self.pasta, executar=executar), [])
        comando, opcoes = executar.chamadas[0]
        self.assertEqual(comando[:3], [str(self.exe), "git", str(self.pasta)])
        self.assertIn("--redact", comando)
        self.assertFalse(opcoes["shell"])

    def test_gitleaks_achou_vira_alerta_so_com_o_tipo(self):
        achados = [{"RuleID": "generic-api-key", "Secret": "REDACTED", "File": "a.py"},
                   {"RuleID": "generic-api-key", "Secret": "REDACTED"}, {"RuleID": "github-pat"}]
        nivel, frase = rs._gitleaks({"JARVIS_GITLEAKS": str(self.exe)}, executar=gitleaks_falso(achados))
        self.assertEqual(nivel, ALERTA)
        self.assertIn("3 possível(is) segredo(s)", frase)
        self.assertIn("2× generic-api-key", frase)
        self.assertNotIn("REDACTED", frase)
        self.assertNotIn("a.py", frase)

    def test_gitleaks_limpo_e_ok(self):
        self.assertEqual(rs._gitleaks({"JARVIS_GITLEAKS": str(self.exe)}, executar=gitleaks_falso())[0], OK)

    def test_gitleaks_ausente_ou_caminho_estranho_e_atencao(self):
        for valor in (str(self.pasta / "nao-existe.exe"), str(self.pasta)):
            nivel, frase = rs._gitleaks({"JARVIS_GITLEAKS": valor}, executar=gitleaks_falso())
            self.assertEqual(nivel, ATENCAO)
        outro = self.pasta / "calc.exe"
        outro.write_text("x", encoding="utf-8")
        self.assertIsNone(rs.caminho_gitleaks({"JARVIS_GITLEAKS": str(outro)}), "só aceita um arquivo gitleaks")

    def test_gitleaks_que_falha_vira_erro(self):
        with self.assertRaises(OSError):
            rs.rodar_gitleaks(self.exe, executar=gitleaks_falso(codigo=1))
        with self.assertRaises(OSError):
            rs.rodar_gitleaks(self.exe, executar=gitleaks_falso(escrever=False))

    # ---------- nota ----------

    def test_nota_no_cofre_com_tabela_numeros_e_pendencias(self):
        (self.pasta / "2026-11-14-101010-ronda.md").write_text("x", encoding="utf-8")
        (self.pasta / "2026-11-13-duelo.md").write_text("x", encoding="utf-8")
        (self.pasta / "2026-11-12-isca-tocada.md").write_text("x", encoding="utf-8")
        (self.pasta / "2026-10-01-velho.md").write_text("x", encoding="utf-8")
        vigias = [("Sentinela", lambda: (OK, "tudo certo")), ("Inicialização", lambda: (ATENCAO, "1 novo | <b>"))]
        codigo, saida = self._main(vigias)
        self.assertEqual(codigo, 0)
        notas = list(self.pasta.glob("*-relatorio-seguranca.md"))
        self.assertEqual([n.name for n in notas], ["2026-11-15-relatorio-seguranca.md"])
        texto = notas[0].read_text(encoding="utf-8")
        self.assertIn("2026, semana 46", texto)
        self.assertIn("| ⚠️ | Inicialização | 1 novo    b  |", texto)
        self.assertIn("- Rondas do defensor: 1", texto)
        self.assertIn("- Notas de incidente: 1", texto)
        self.assertIn("- Duelos Hacker × Sentinela: 1", texto)
        self.assertIn("- [ ] Inicialização: ", texto)

    def test_nunca_sobrescreve(self):
        vigias = [("Sentinela", lambda: (OK, "tudo certo"))]
        self._main(vigias)
        self._main(vigias)
        nomes = sorted(n.name for n in self.pasta.glob("*-relatorio-seguranca*.md"))
        self.assertEqual(nomes, ["2026-11-15-relatorio-seguranca-2.md", "2026-11-15-relatorio-seguranca.md"])

    def test_alerta_sai_com_1_e_vigia_quebrado_nao_derruba(self):
        def quebrado():
            raise RuntimeError("x")
        codigo, saida = self._main([("Gitleaks", lambda: (ALERTA, "1 segredo")), ("Rede", quebrado)])
        self.assertEqual(codigo, 1)
        self.assertIn("Rede: não consegui conferir agora", saida)

    def test_sem_pasta_nao_grava(self):
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            rs.main(vigias=[("S", lambda: (OK, "ok"))], pasta=None, agora=AGORA)
        self.assertIn("a nota não foi gravada", saida.getvalue())

    def test_vigias_do_relatorio_incluem_inicializacao_e_gitleaks(self):
        nomes = [n for n, _ in rs.VIGIAS]
        for esperado in ("Sentinela", "Inicialização", "Gitleaks", "Backup"):
            self.assertIn(esperado, nomes)


if __name__ == "__main__":
    unittest.main()
