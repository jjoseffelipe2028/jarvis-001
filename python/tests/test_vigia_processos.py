"""Testes do vigia de processos (Ticket 040): a lista de processos é FALSA (nada do PC é lido)."""

import unittest
from types import SimpleNamespace

from vigia_processos import COMANDO, calcular, classificar, frase, ler_amostra, medir

CSV_1 = '''"Id","ProcessName","CPU","Path"
"100","chrome","10,5","C:\\Program Files\\Google\\Chrome\\chrome.exe"
"200","svc_update","3.0","C:\\Users\\jose\\AppData\\Local\\Temp\\svc_update.exe"
"300","xmrig","1","C:\\tools\\xmrig.exe"
"400","fantasma","2",""
"500","notepad","0,5","C:\\Windows\\notepad.exe"
"abc","torto","x",""
'''
CSV_2 = '''"Id","ProcessName","CPU","Path"
"100","chrome","30,5","C:\\Program Files\\Google\\Chrome\\chrome.exe"
"200","svc_update","11.0","C:\\Users\\jose\\AppData\\Local\\Temp\\svc_update.exe"
"300","xmrig","1,4","C:\\tools\\xmrig.exe"
"400","fantasma","10",""
"500","notepad","0,5","C:\\Windows\\notepad.exe"
"600","novo","50",""
'''


class VigiaProcessosTest(unittest.TestCase):

    def test_le_a_amostra_com_virgula_ou_ponto_e_pula_linha_torta(self):
        a = ler_amostra(CSV_1)
        self.assertEqual(set(a), {100, 200, 300, 400, 500})
        self.assertEqual(a[100][:2], ("chrome", 10.5))
        self.assertEqual(ler_amostra("isso não é csv"), {})
        self.assertEqual(ler_amostra(""), {})

    def test_calcula_e_ordena_suspeitos_primeiro(self):
        # 10 segundos, 8 núcleos: 20 s de CPU = 25% da máquina
        usos = calcular(ler_amostra(CSV_1), ler_amostra(CSV_2), 10, 8)
        por_nome = {u.nome: u for u in usos}
        self.assertEqual(por_nome["chrome"].porcento, 25.0)
        self.assertEqual(por_nome["chrome"].nivel, "pesado")
        self.assertEqual(por_nome["svc_update"].nivel, "suspeito")   # 10% vindo de pasta temporária
        self.assertEqual(por_nome["fantasma"].nivel, "suspeito")     # 10% sem caminho
        self.assertEqual(por_nome["xmrig"].nivel, "suspeito")        # nome de minerador, mesmo usando pouco
        self.assertNotIn("notepad", por_nome, "não gastou nada")
        self.assertNotIn("novo", por_nome, "nasceu no meio da medição")
        self.assertEqual([u.nivel for u in usos], ["suspeito", "suspeito", "suspeito", "pesado"])

    def test_classificacao(self):
        self.assertEqual(classificar("XMRig", "C:/x/xmrig.exe", 0.1)[0], "suspeito")
        self.assertEqual(classificar("java", "C:/Program Files/Java/bin/java.exe", 60)[0], "pesado")
        self.assertEqual(classificar("java", "C:/Program Files/Java/bin/java.exe", 5)[0], "normal")
        self.assertEqual(classificar("jogo", "C:/Users/jose/Downloads/jogo.exe", 12)[0], "suspeito")
        self.assertEqual(classificar("jogo", "C:/Users/jose/Downloads/jogo.exe", 2)[0], "normal")
        self.assertEqual(classificar("System", "", 3)[0], "normal", "processo do sistema parado não é alarme")
        self.assertEqual(classificar("MsMpEng", "", 30)[0], "pesado", "antivírus do Windows varrendo não é minerador")
        self.assertEqual(classificar("desconhecido", "", 30)[0], "suspeito")

    def test_numero_de_processo_reaproveitado_nao_conta(self):
        antes = {100: ("antigo", 1.0, "")}
        depois = {100: ("outro", 90.0, "")}
        self.assertEqual(calcular(antes, depois, 5, 4), [])
        self.assertEqual(calcular(antes, antes, 0, 4), [])

    def test_frases(self):
        usos = calcular(ler_amostra(CSV_1), ler_amostra(CSV_2), 10, 8)
        self.assertEqual(frase(usos), "Vigia de processos: 3 suspeito(s). Veja a lista antes de tudo.")
        self.assertIn("nenhum suspeito. 1 programa(s) pesado(s)", frase([u for u in usos if u.nivel == "pesado"]))
        self.assertEqual(frase([]), "Vigia de processos: nenhum suspeito e nada pesado agora.")

    def test_medir_usa_comando_fixo_sem_shell(self):
        saidas, chamadas = [CSV_1, CSV_2], []

        def executar(comando, **opcoes):
            chamadas.append((comando, opcoes))
            return SimpleNamespace(returncode=0, stdout=saidas.pop(0))
        tempos = iter([0.0, 10.0])
        usos = medir(10, executar, dormir=lambda s: None, relogio=lambda: next(tempos))
        self.assertTrue(usos)
        for comando, opcoes in chamadas:
            self.assertEqual(comando, COMANDO)
            self.assertFalse(opcoes["shell"])
        self.assertEqual(len(chamadas), 2)

    def test_comando_com_erro_vira_excecao(self):
        with self.assertRaises(OSError):
            medir(1, lambda c, **o: SimpleNamespace(returncode=1, stdout=""), dormir=lambda s: None)


if __name__ == "__main__":
    unittest.main()
