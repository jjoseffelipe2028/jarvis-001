"""Testes do Duelo Hacker × Sentinela (Ticket 035): a API é FALSA (nenhum Java é ligado aqui)."""

import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

import duelo
from bracos import assinar
from duelo import Ataque, ambiente_da_copia, nota_markdown, placar, rodar_ataques

TOKEN, ISCA = "token-falso-de-teste", "isca-falsa-de-teste"


class ApiFalsa:
    """Imita a API do Jarvis. `furos` = defesas desligadas de propósito, para ver se o duelo percebe."""

    def __init__(self, pasta, furos=()):
        self.pasta, self.furos = Path(pasta), set(furos)
        self.toques, self.nonces = 0, set()

    def _autoriza(self, metodo, rota, cab, corpo):
        if cab.get("X-Jarvis-Token") == TOKEN:
            return True
        if cab.get("X-Jarvis-Token") == ISCA:
            if "isca" not in self.furos:
                self.toques += 1
            return False
        if "X-Jarvis-Assinatura" in cab:
            nonce = cab["X-Jarvis-Nonce"]
            if nonce in self.nonces and "replay" not in self.furos:
                return False
            self.nonces.add(nonce)
            certo = assinar(TOKEN, metodo, rota, corpo, cab["X-Jarvis-Hora"], nonce)["X-Jarvis-Assinatura"]
            return certo == cab["X-Jarvis-Assinatura"] or "corpo" in self.furos
        return "sem_token" in self.furos or ("chute" in self.furos and "X-Jarvis-Token" in cab)

    def __call__(self, metodo, rota, cabecalhos=None, corpo=b"", host=duelo.ALVO):
        cab = cabecalhos or {}
        if not host.startswith("127.0.0.1") and "host" not in self.furos:
            return 403, "", 0.01
        if "%2e" in rota:
            return (200, "[fonts]", 0.01) if "caminho" in self.furos else (400, "", 0.01)
        if metodo == "DELETE":
            return (204, "", 0.01) if "delete" in self.furos else (401, "", 0.01)
        if not self._autoriza(metodo, rota, cab, corpo):
            return 401, '{"erro":"nao autorizado"}', 0.01
        if rota == "/acoes/sentinela/isca":
            return 200, json.dumps({"ligada": True, "toques": self.toques}), 0.01
        if rota == "/acoes/daily":
            if len(corpo) > 100_000 and "gigante" not in self.furos:
                return 413, "", 0.01
            texto = json.loads(corpo)["texto"]
            if "linha" not in self.furos:
                texto = texto.replace("\n", " ").replace("#", "\\#").replace("[", "\\[")
            with open(self.pasta / "2026-11-12.md", "a", encoding="utf-8") as f:
                f.write(f"- 21:00 {texto}\n")
            return 200, "{}", 0.01
        if rota == "/acoes/sentinela/golpe":
            texto = json.loads(corpo)["texto"]
            eco = texto if "xss" in self.furos else ""
            return 200, json.dumps({"nivel": "alto", "eco": eco}), (5.0 if "redos" in self.furos else 0.02)
        if rota == "/acoes/sentinela/proposta":
            return 200, json.dumps({"nivel": "baixo" if "prompt" in self.furos else "alto"}), 0.01
        return 200, "{}", 0.01


class DueloTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def _duelo(self, furos=()):
        return rodar_ataques(ApiFalsa(self.pasta, furos), TOKEN, ISCA, self.pasta)

    def test_api_bem_defendida_pega_os_13(self):
        ataques = self._duelo()
        self.assertEqual(len(ataques), 13)
        self.assertEqual([a.nome for a in ataques if not a.defendeu], [])
        self.assertEqual(placar(ataques), "O hacker tentou 13 ataques. A defesa pegou 13.")

    def test_cada_furo_e_percebido_pelo_duelo(self):
        esperado = {
            "sem_token": "Entrar sem token", "chute": "Adivinhar o token (5 chutes)", "isca": "Usar o token-isca",
            "replay": "Repetir pedido copiado (replay)", "corpo": "Trocar o corpo depois de assinado",
            "host": "Site de fora (Host falso)", "caminho": "Fugir da pasta pelo endereço (../)",
            "gigante": "JSON gigante no daily", "linha": "Injetar linha falsa no daily",
            "redos": "Travar o detector de golpe (ReDoS)", "xss": "Código na resposta do detector (XSS)",
            "delete": "Apagar com DELETE sem token", "prompt": "Convencer o verificador (prompt injection)",
        }
        for furo, nome in esperado.items():
            with self.subTest(furo=furo):
                with tempfile.TemporaryDirectory() as tmp:
                    ataques = rodar_ataques(ApiFalsa(tmp, [furo]), TOKEN, ISCA, tmp)
                self.assertIn(nome, [a.nome for a in ataques if not a.defendeu])

    def test_placar_com_ataque_que_escapou(self):
        ataques = [Ataque("A", "T1", True, "ok"), Ataque("B", "T2", False, "passou")]
        self.assertEqual(placar(ataques), "O hacker tentou 2 ataques. A defesa pegou 1. 1 escapou e vira ticket 🔴: B.")

    def test_nota_tem_placar_tabela_e_tecnicas(self):
        nota = nota_markdown(self._duelo(), datetime(2026, 11, 12, 22, 0))
        self.assertIn("# ⚔️ Duelo Hacker × Sentinela (12/11/2026 22:00)", nota)
        self.assertIn("A defesa pegou 13.", nota)
        self.assertIn("| 🛡️ | Usar o token-isca | T1552 Unsecured Credentials |", nota)
        self.assertNotIn(TOKEN, nota)
        self.assertNotIn(ISCA, nota)

    def test_copia_nunca_herda_variavel_de_verdade(self):
        pastas = {"daily": "d", "git": "g", "decisoes": "x"}
        real = {"PATH": "p", "JARVIS_TOKEN": "TOKEN-DE-VERDADE", "jarvis_pasta_daily": "COFRE-DE-VERDADE",
                "JARVIS_PASTA_INCIDENTES": "C:/cofre/incidentes"}
        ambiente = ambiente_da_copia(pastas, TOKEN, ISCA, real)
        self.assertEqual(ambiente["JARVIS_TOKEN"], TOKEN)
        self.assertEqual(ambiente["SERVER_PORT"], "8091")
        self.assertEqual(ambiente["PATH"], "p")
        self.assertNotIn("TOKEN-DE-VERDADE", ambiente.values())
        self.assertNotIn("COFRE-DE-VERDADE", ambiente.values())
        self.assertNotIn("JARVIS_PASTA_INCIDENTES", ambiente)

    def test_alvo_e_fixo_no_proprio_pc(self):
        self.assertEqual(duelo.ALVO, "127.0.0.1:8091")
        self.assertNotEqual(duelo.PORTA, 8080, "o duelo nunca ataca a API de verdade")


if __name__ == "__main__":
    unittest.main()
