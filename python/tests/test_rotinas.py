"""Testes do "Bom dia, Jarvis" (Ticket 009) com braços FALSOS (sem API)."""

import unittest

from bracos import ErroBraco
from intencoes import detectar
from rotinas import _cumprimento, bom_dia


class BracosFalsos:
    """Imita o Bracos. Cada braço pode devolver dados ou falhar."""

    def __init__(self, hora=None, pend=None, git=None):
        self._r = {"hora": hora, "pendencias": pend, "git_status": git}
        self.chamados = []

    def _resp(self, nome):
        self.chamados.append(nome)
        valor = self._r[nome]
        if isinstance(valor, Exception):
            raise valor
        return valor

    def hora(self):
        return self._resp("hora")

    def pendencias(self):
        return self._resp("pendencias")

    def git_status(self):
        return self._resp("git_status")

    def registrar_daily(self, texto):
        raise AssertionError("o bom dia NUNCA pode escrever")


class BomDiaTest(unittest.TestCase):

    def test_resumo_completo(self):
        b = BracosFalsos(hora={"hora": "08:10"},
                         pend={"total": 16, "pendencias": [{"texto": "**Cyber EBAC** — 30 min"}]},
                         git={"limpo": False, "alterados": 2, "novos": 1})
        frase = bom_dia(b)
        self.assertTrue(frase.startswith("Bom dia, José! São 08:10."))
        self.assertIn("Você tem 16 pendências. A primeira: Cyber EBAC — 30 min.", frase)
        self.assertIn("3 arquivos sem commit", frase)
        self.assertIn("Cyber", frase)
        self.assertNotIn("*", frase)

    def test_so_le_nunca_escreve(self):
        b = BracosFalsos(hora={"hora": "09:00"}, pend={"total": 0}, git={"limpo": True})
        bom_dia(b)
        self.assertEqual(sorted(b.chamados), ["git_status", "hora", "pendencias"])

    def test_um_braco_falhando_nao_derruba_o_resto(self):
        b = BracosFalsos(hora={"hora": "14:00"}, pend=ErroBraco("x"), git={"limpo": True})
        frase = bom_dia(b)
        self.assertIn("Boa tarde", frase)
        self.assertIn("Não consegui ler suas pendências.", frase)
        self.assertIn("tudo commitado", frase)

    def test_api_desligada_ainda_responde(self):
        erro = ErroBraco("desligada")
        frase = bom_dia(BracosFalsos(hora=erro, pend=erro, git=erro))
        self.assertTrue(frase.startswith("Olá, José!"))
        self.assertIn("Cyber", frase)

    def test_alarme_de_impostor_nao_e_escondido(self):
        # Achado 3 (009): com impostor na porta, o José precisa OUVIR o alarme
        from bracos import IMPOSTOR
        frase = bom_dia(BracosFalsos(hora=ErroBraco(IMPOSTOR), pend={"total": 0}, git={"limpo": True}))
        self.assertTrue(frase.startswith("Atenção, José:"))
        self.assertIn("não provou ser o Jarvis", frase)

    def test_401_tambem_e_alarme(self):
        frase = bom_dia(BracosFalsos(hora={"hora": "08:00"}, pend=ErroBraco("token", status=401), git=None))
        self.assertTrue(frase.startswith("Atenção, José:"))

    def test_numero_infinito_nao_derruba(self):
        # Achado 8: o JSON do Python aceita Infinity
        b = BracosFalsos(hora={"hora": "08:00"}, pend={"total": float("inf")},
                         git={"limpo": False, "alterados": float("inf")})
        self.assertIn("formato estranho", bom_dia(b))

    def test_dados_tortos_nao_derrubam(self):
        b = BracosFalsos(hora={"hora": "xx"}, pend={"total": "abc"}, git={"limpo": False, "alterados": "?"})
        frase = bom_dia(b)
        self.assertIn("formato estranho", frase)

    def test_texto_de_nota_com_escape_sai_limpo(self):
        b = BracosFalsos(hora={"hora": "08:00"},
                         pend={"total": 1, "pendencias": [{"texto": "x\x1b[2Jy"}]}, git=None)
        self.assertNotIn("\x1b", bom_dia(b))

    def test_pendencia_terminando_em_ponto_nao_vira_ponto_duplo(self):
        # Bug visto no teste por voz de 09/11: "...menu Share).."
        b = BracosFalsos(hora={"hora": "08:00"},
                         pend={"total": 1, "pendencias": [{"texto": "Mostrar o protótipo ao irmão."}]}, git=None)
        frase = bom_dia(b)
        self.assertIn("A primeira: Mostrar o protótipo ao irmão.", frase)
        self.assertNotIn("..", frase)

    def test_cumprimento_pela_hora(self):
        self.assertEqual(_cumprimento("05:00"), "Bom dia")
        self.assertEqual(_cumprimento("11:59"), "Bom dia")
        self.assertEqual(_cumprimento("12:00"), "Boa tarde")
        self.assertEqual(_cumprimento("18:30"), "Boa noite")
        self.assertEqual(_cumprimento("02:50"), "Boa noite")
        self.assertEqual(_cumprimento(None), "Olá")


class DetectarBomDiaTest(unittest.TestCase):

    def test_frase_inteira_vira_rotina(self):
        for frase in ("Bom dia, Jarvis!", "jarvis, bom dia", "boa noite", "Resumo do dia", "como tá meu dia?"):
            self.assertEqual(detectar(frase), ("bom_dia", None), frase)

    def test_bom_dia_dentro_de_conversa_continua_conversa(self):
        for frase in ("bom dia, me explica o que é API", "bom dia é com acento?"):
            self.assertIsNone(detectar(frase), frase)


if __name__ == "__main__":
    unittest.main()
