"""Testes das intenções: qual frase vira ação, e qual fica só na conversa."""

import unittest

from intencoes import detectar, responder, seguro


class DetectarTest(unittest.TestCase):

    def test_daily_mantem_acento_e_corta_o_comando(self):
        self.assertEqual(detectar("Jarvis, anota no daily: estudei Spring e café ☕"),
                         ("daily", "estudei Spring e café ☕"))
        self.assertEqual(detectar("registra no diário que terminei o ticket 004"),
                         ("daily", "terminei o ticket 004"))
        self.assertEqual(detectar("Anote isso no meu daily - prova de cyber amanhã"),
                         ("daily", "prova de cyber amanhã"))

    def test_daily_sem_texto_devolve_vazio(self):
        self.assertEqual(detectar("anota no daily"), ("daily", ""))

    def test_pendencias(self):
        for frase in ("Quais minhas pendências?", "tenho tarefas abertas?", "Jarvis, o que falta?"):
            self.assertEqual(detectar(frase), ("pendencias", None), frase)

    def test_git_status(self):
        for frase in ("Tenho algo sem commit?", "como tá o git status?", "fiz o commit de hoje?"):
            self.assertEqual(detectar(frase), ("git_status", None), frase)

    def test_pedir_para_rodar_comando_e_recusado_mesmo_sendo_git(self):
        # "roda ..." é pedido de executar comando livre: fora da lista
        self.assertEqual(detectar("roda um git status pra mim"), ("proibida", None))

    def test_hora(self):
        self.assertEqual(detectar("Que horas são?"), ("hora", None))
        self.assertEqual(detectar("que dia é hoje"), ("hora", None))

    def test_acao_fora_da_lista_e_recusada(self):
        for frase in ("apaga a pasta do projeto", "Jarvis, instala esse programa",
                      "executa rm -rf /", "manda um email pro chefe", "faz push no github",
                      "por favor, desliga o pc"):
            self.assertEqual(detectar(frase), ("proibida", None), frase)

    def test_conversa_normal_vai_pro_gemma(self):
        for frase in ("O que é uma API?", "me explica o que é Path Traversal",
                      "bom dia, tudo bem com você?", "", None):
            self.assertIsNone(detectar(frase), frase)

    def test_texto_do_daily_nao_e_reinterpretado(self):
        # "apaga" DENTRO do texto do daily é só texto, não vira ação proibida
        self.assertEqual(detectar("anota no daily: amanhã apaga a workSpace antiga"),
                         ("daily", "amanhã apaga a workSpace antiga"))


    def test_pergunta_teorica_e_conversa_nao_acao(self):
        # Achado 6 do hacker: falar SOBRE commit/hora não pode sequestrar a conversa
        for frase in ("Me explica o que é um commit", "quantas horas devo estudar por dia?",
                      "qual a diferença entre commit e push?", "o que são pendências técnicas?"):
            self.assertIsNone(detectar(frase), frase)

    def test_letra_que_muda_de_tamanho_nao_corta_errado(self):
        # Achado 8: "İ".lower() vira 2 letras; o texto do daily tem que sair inteiro
        self.assertEqual(detectar("Anota no daily: İstanbul é longe"), ("daily", "İstanbul é longe"))
        self.assertEqual(detectar("İ anota no daily: x"), None)


class SeguroTest(unittest.TestCase):

    def test_tira_escape_de_terminal_e_invisiveis(self):
        self.assertEqual(seguro("ok\x1b[2Jlimpou‮tela"), "ok [2Jlimpou tela")

    def test_limita_tamanho(self):
        self.assertTrue(seguro("a" * 1000).endswith("..."))
        self.assertLessEqual(len(seguro("a" * 1000)), 403)


class ResponderTest(unittest.TestCase):

    def test_resposta_torta_nao_derruba(self):
        # Achado 4: tipo errado vindo da API (ou de um impostor) não pode dar crash
        self.assertEqual(responder("git_status", {"limpo": False, "alterados": "abc"}),
                         "A API respondeu algo inesperado.")
        self.assertEqual(responder("pendencias", {"total": 2, "pendencias": ["nao-e-dict"]}),
                         "A API respondeu algo inesperado.")

    def test_pendencia_com_escape_de_terminal_sai_limpa(self):
        frase = responder("pendencias", {"total": 1, "pendencias": [{"texto": "x\x1b]0;hack\x07y"}]})
        self.assertNotIn("\x1b", frase)
        self.assertNotIn("\x07", frase)

    def test_git_limpo_e_sujo(self):
        self.assertIn("Tudo commitado", responder("git_status", {"branch": "main", "limpo": True}))
        frase = responder("git_status", {"branch": "main", "limpo": False, "alterados": 1, "novos": 3})
        self.assertEqual(frase, "Na branch main você tem 1 arquivo alterado e 3 arquivos novos sem commit.")

    def test_pendencias_fala_so_as_3_primeiras_sem_markdown(self):
        dados = {"total": 5, "pendencias": [
            {"texto": "**Cyber EBAC** — 30 min"}, {"texto": "`git` e \\[x\\]"},
            {"texto": "terceira"}, {"texto": "quarta"}]}
        frase = responder("pendencias", dados)
        self.assertTrue(frase.startswith("Você tem 5 pendências."))
        self.assertIn("Cyber EBAC — 30 min", frase)
        self.assertNotIn("quarta", frase)
        self.assertNotIn("*", frase)
        self.assertNotIn("`", frase)

    def test_nenhuma_pendencia(self):
        self.assertIn("Nenhuma pendência", responder("pendencias", {"total": 0, "pendencias": []}))

    def test_proibida(self):
        self.assertIn("Não posso", responder("proibida", None))


if __name__ == "__main__":
    unittest.main()
