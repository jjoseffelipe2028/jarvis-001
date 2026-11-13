"""Testes do Caderno de Incidentes (Ticket 031): Sentinela FALSA e pasta temporária."""

import contextlib
import io
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

from bracos import ErroBraco
from incidentes import Alerta, coletar, gravar, limpo, main, nota_markdown, slug

AGORA = datetime(2026, 11, 12, 21, 30)
PORTAS_OK = {"alertas": 0, "jarvisSoNoPc": True, "portas": [
    {"porta": 49664, "alcance": "toda a rede", "risco": "normal", "nota": "porta alta"}]}
TRAVAS_OK = {"alterados": 0, "arquivos": [{"nome": ".gitignore", "estado": "igual", "hash": "ab"}]}


class SentinelaFalsa:
    def __init__(self, portas=PORTAS_OK, travas=TRAVAS_OK):
        self._portas, self._travas = portas, travas

    def portas(self):
        if isinstance(self._portas, Exception):
            raise self._portas
        return self._portas

    def integridade(self):
        if isinstance(self._travas, Exception):
            raise self._travas
        return self._travas


class ColetarTest(unittest.TestCase):

    def test_tudo_tranquilo_nao_gera_alerta(self):
        self.assertEqual(coletar(SentinelaFalsa()), ([], []))

    def test_porta_desconhecida_vira_alerta_e_porta_normal_nao(self):
        portas = {"jarvisSoNoPc": True, "portas": [
            {"porta": 7260, "alcance": "toda a rede", "risco": "atencao", "nota": "desconhecida"},
            {"porta": 49664, "alcance": "toda a rede", "risco": "normal", "nota": "porta alta"}]}
        alertas, falhas = coletar(SentinelaFalsa(portas=portas))
        self.assertEqual([a.slug for a in alertas], ["porta-7260-aberta"])
        self.assertIn("Porta: 7260", alertas[0].fatos)
        self.assertEqual(falhas, [])

    def test_api_exposta_vira_um_alerta_so(self):
        portas = {"jarvisSoNoPc": False, "portas": [
            {"porta": 8080, "alcance": "toda a rede", "risco": "alto", "nota": "a API do Jarvis"}]}
        alertas, _ = coletar(SentinelaFalsa(portas=portas))
        self.assertEqual([a.slug for a in alertas], ["api-do-jarvis-aberta-para-a-rede"])

    def test_trava_alterada_vira_alerta_com_os_arquivos(self):
        travas = {"alterados": 2, "arquivos": [
            {"nome": ".gitignore", "estado": "alterado"}, {"nome": "pom.xml", "estado": "igual"},
            {"nome": "iniciar.sh", "estado": "sumiu"}]}
        alertas, _ = coletar(SentinelaFalsa(travas=travas))
        self.assertEqual(alertas[0].slug, "travas-do-projeto-mudaram")
        self.assertEqual(alertas[0].fatos, ["`.gitignore`: alterado", "`iniciar.sh`: sumiu"])

    def test_um_braco_fora_do_ar_nao_derruba_o_outro(self):
        travas = {"arquivos": [{"nome": "a", "estado": "alterado"}]}
        alertas, falhas = coletar(SentinelaFalsa(portas=ErroBraco("desligada"), travas=travas))
        self.assertEqual(len(alertas), 1)
        self.assertEqual(falhas, ["portas"])

    def test_dados_tortos_nao_derrubam(self):
        portas = {"jarvisSoNoPc": True, "portas": ["texto", {"porta": "abc", "risco": "atencao"},
                                                   {"porta": 99999, "risco": "atencao"}, None]}
        self.assertEqual(coletar(SentinelaFalsa(portas=portas, travas={"arquivos": [None, 5]})), ([], []))
        self.assertEqual(coletar(SentinelaFalsa(portas=[], travas="x"))[0], [])

    def test_muitas_portas_tem_limite(self):
        portas = {"jarvisSoNoPc": True, "portas": [
            {"porta": 5000 + n, "alcance": "rede", "risco": "atencao", "nota": "x"} for n in range(50)]}
        self.assertEqual(len(coletar(SentinelaFalsa(portas=portas))[0]), 8)


class NotaTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def test_nota_tem_perguntas_antes_e_gabarito_dobrado(self):
        alerta = coletar(SentinelaFalsa(portas={"jarvisSoNoPc": True, "portas": [
            {"porta": 7260, "alcance": "toda a rede", "risco": "atencao", "nota": "desconhecida"}]}))[0][0]
        nota = nota_markdown(alerta, AGORA)
        self.assertIn("# 🗂️ Incidente: Porta 7260 aberta para a rede", nota)
        self.assertIn("12/11/2026 às 21:30", nota)
        self.assertLess(nota.index("É ataque ou só incidente?"), nota.index("[!success]- Gabarito"))
        self.assertIn("T1046", nota)
        self.assertIn("- [ ] Resolvido", nota)

    def test_grava_uma_vez_por_dia_e_nunca_sobrescreve(self):
        alerta = Alerta("porta-7260-aberta", "Porta 7260", ["Porta: 7260"], ["g"])
        self.assertEqual(gravar(self.pasta, [alerta], AGORA), (["2026-11-12-porta-7260-aberta.md"], []))
        arquivo = self.pasta / "2026-11-12-porta-7260-aberta.md"
        arquivo.write_text("minha resposta", encoding="utf-8")
        self.assertEqual(gravar(self.pasta, [alerta], AGORA), ([], ["2026-11-12-porta-7260-aberta.md"]))
        self.assertEqual(arquivo.read_text(encoding="utf-8"), "minha resposta")

    def test_slug_nunca_vira_caminho(self):
        self.assertEqual(slug("Popup do Antivírus!"), "popup-do-antivirus")
        self.assertEqual(slug("../../Windows/System32"), "windows-system32")
        self.assertEqual(slug("C:\\x\\y.md"), "c-x-y-md")
        self.assertEqual(slug("!!!"), "")
        self.assertLessEqual(len(slug("a" * 300)), 40)

    def test_texto_de_fora_e_desarmado(self):
        alerta = coletar(SentinelaFalsa(portas={"jarvisSoNoPc": True, "portas": [
            {"porta": 7260, "alcance": "<img src=x>", "risco": "atencao",
             "nota": "![x](http://evil) `=this` <script>\nlinha nova"}]}))[0][0]
        nota = nota_markdown(alerta, AGORA)
        for ruim in ("<img", "<script>", "![x]", "`=this`"):
            self.assertNotIn(ruim, nota)
        self.assertEqual(limpo("a\x1b[2Jb\nc"), "a 2Jb c")

    def test_comando_ronda_e_caso_manual(self):
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            self.assertEqual(main([], bracos=SentinelaFalsa(), pasta=self.pasta), 0)
            self.assertEqual(main(["novo", "Popup: seu antivírus venceu!"], pasta=self.pasta), 0)
            self.assertEqual(main(["novo", "!!!"], pasta=self.pasta), 1)
        texto = saida.getvalue()
        self.assertIn("nenhum alerta", texto)
        notas = list(self.pasta.glob("*-popup-seu-antivirus-venceu.md"))
        self.assertEqual(len(notas), 1)
        self.assertIn("Sem gabarito", notas[0].read_text(encoding="utf-8"))
        self.assertIn("❌", texto)

    def test_api_desligada_avisa_e_devolve_1(self):
        fora = ErroBraco("desligada")
        saida = io.StringIO()
        with contextlib.redirect_stdout(saida):
            self.assertEqual(main([], bracos=SentinelaFalsa(portas=fora, travas=fora), pasta=self.pasta), 1)
        self.assertIn("Não consegui ler: portas", saida.getvalue())
        self.assertEqual(list(self.pasta.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
