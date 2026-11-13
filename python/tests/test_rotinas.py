"""Testes do "Bom dia, Jarvis" (Ticket 009) com braços FALSOS (sem API)."""

import unittest

from bracos import ErroBraco
from intencoes import detectar
from rotinas import _cumprimento, bom_dia


class BracosFalsos:
    """Imita o Bracos. Cada braço pode devolver dados ou falhar."""

    def __init__(self, hora=None, pend=None, git=None, portas=None, integ=None, isca=None):
        self._r = {"hora": hora, "pendencias": pend, "git_status": git, "portas": portas, "integridade": integ,
                   "isca": isca}
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

    def portas(self):
        return self._resp("portas")

    def integridade(self):
        return self._resp("integridade")

    def isca(self):
        return self._resp("isca")

    def golpe(self, texto):
        raise AssertionError("o bom dia não analisa mensagem")

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
        self.assertEqual(sorted(b.chamados), ["git_status", "hora", "integridade", "isca", "pendencias", "portas"])

    def test_sentinela_tranquila_vem_antes_da_cyber(self):
        # Ticket 024
        b = BracosFalsos(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True},
                         portas={"alertas": 0, "jarvisSoNoPc": True}, integ={"alterados": 0})
        frase = bom_dia(b)
        self.assertIn("Sentinela: portas sem alerta e travas iguais. Cyber sem pendência", frase)

    def test_sentinela_em_alerta_vem_logo_depois_do_bom_dia(self):
        b = BracosFalsos(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True},
                         portas={"alertas": 1, "jarvisSoNoPc": False}, integ={"alterados": 1})
        frase = bom_dia(b)
        self.assertTrue(frase.startswith(
            "Bom dia, José! São 08:00. Sentinela em alerta: a API do Jarvis está aberta para a rede; "
            "1 alerta nas portas; 1 trava mudou. Peça os detalhes antes de tudo."), frase)

    def test_isca_tocada_e_o_primeiro_alerta(self):
        # Ticket 034
        b = BracosFalsos(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True},
                         portas={"alertas": 1, "jarvisSoNoPc": True}, integ={"alterados": 0},
                         isca={"ligada": True, "toques": 2, "ultimoToque": "12/11 20:30", "arquivo": "mexido"})
        frase = bom_dia(b)
        self.assertTrue(frase.startswith(
            "Bom dia, José! São 08:00. Sentinela em alerta: a isca foi tocada 2 vezes: alguém usou o token falso; "
            "o arquivo-isca foi mexido; 1 alerta nas portas."), frase)

    def test_isca_quieta_desligada_ou_torta_nao_muda_nada(self):
        base = dict(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True},
                    portas={"alertas": 0, "jarvisSoNoPc": True}, integ={"alterados": 0})
        esperado = "Sentinela: portas sem alerta e travas iguais."
        for isca in (None, {"ligada": False, "toques": 0, "arquivo": "sem"},
                     {"ligada": True, "toques": 0, "arquivo": "igual"}, {"toques": "muitos"}, ErroBraco("x")):
            self.assertIn(esperado, bom_dia(BracosFalsos(isca=isca, **base)), repr(isca))

    def test_sentinela_fora_do_ar_ou_torta_nao_derruba(self):
        fora = ErroBraco("sentinela so no windows")
        frase = bom_dia(BracosFalsos(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True},
                                     portas=fora, integ=fora))
        self.assertNotIn("Sentinela", frase)
        self.assertIn("Cyber", frase)
        frase = bom_dia(BracosFalsos(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True},
                                     portas={"alertas": "muitos"}, integ={"alterados": 0}))
        self.assertIn("Sentinela: travas iguais.", frase)

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

    # --- Melhoria do teste real (12/11): a cobrança da Cyber lê o contexto ---

    def test_cyber_primeira_pendencia_nao_repete(self):
        b = BracosFalsos(hora={"hora": "08:00"}, git=None,
                         pend={"total": 2, "pendencias": [{"texto": "Cyber: aula 03"}, {"texto": "Java"}]})
        frase = bom_dia(b)
        self.assertTrue(frase.endswith("Comece pela Cyber."), frase)
        self.assertEqual(frase.count("aula 03"), 1)

    def test_cyber_no_meio_da_lista_e_falada(self):
        b = BracosFalsos(hora={"hora": "08:00"}, git=None,
                         pend={"total": 2, "pendencias": [{"texto": "Java"}, {"texto": "🔒 **CYBER** repescagem."}]})
        self.assertIn("Cyber em aberto: 🔒 CYBER repescagem. Faça hoje.", bom_dia(b))

    def test_sem_pendencia_de_cyber_nao_inventa_atraso(self):
        b = BracosFalsos(hora={"hora": "08:00"}, git=None,
                         pend={"total": 1, "pendencias": [{"texto": "Java"}]})
        frase = bom_dia(b)
        self.assertIn("Cyber sem pendência aberta.", frase)
        self.assertNotIn("30 minutos", frase)

    def test_pendencias_fora_do_ar_so_pergunta(self):
        frase = bom_dia(BracosFalsos(hora={"hora": "08:00"}, pend=ErroBraco("x"), git=None))
        self.assertTrue(frase.endswith("E a Cyber? Confira se fez a sua hora hoje."), frase)

    def test_pendencia_torta_nao_derruba_a_cobranca(self):
        frase = bom_dia(BracosFalsos(hora={"hora": "08:00"}, git=None,
                                     pend={"total": 1, "pendencias": ["texto solto"]}))
        self.assertIn("Cyber", frase)

    # --- Ticket 038: frases locais (duelo e backup) ---

    def test_extras_entram_antes_da_cyber(self):
        b = BracosFalsos(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True})
        frase = bom_dia(b, lambda: ["Último duelo: a defesa pegou 13 de 13.", "Backup 3-2-1: 2 de 3. Falta: Offline."])
        self.assertIn("Último duelo: a defesa pegou 13 de 13. Backup 3-2-1: 2 de 3. Falta: Offline. Cyber sem", frase)

    def test_extras_quebrados_ou_tortos_nao_derrubam(self):
        b = BracosFalsos(hora={"hora": "08:00"}, pend={"total": 0}, git={"limpo": True})
        sem = bom_dia(b)

        def explode():
            raise RuntimeError("deu ruim")
        for extras in (explode, lambda: None, lambda: [None, 5, "", "  "], lambda: "texto solto"):
            self.assertEqual(bom_dia(b, extras), sem)
        self.assertNotIn("\x1b", bom_dia(b, lambda: ["a\x1b[2Jb"]))

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
