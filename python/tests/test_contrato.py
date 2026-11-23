"""
Ticket 081: guardião do contrato Java ↔ Python (lado Python). 🤝
Lê docs/contratos/bracos-python.json — o MESMO arquivo que o ContratoPythonTest (Java) lê — e confere:
1) cada ação do Bracos chama a rota e o método HTTP do contrato (e não existe ação fora do contrato);
2) o corpo mandado tem só campos do contrato, cortados no mesmo tamanho máximo que o Java aceita;
3) tudo que o Python LÊ da resposta (frases, bom dia, alarme, proposta) está no contrato.
Junto com o teste Java (contrato ⊆ o que o Java manda), fecha a corrente: o que o Python lê ⊆ contrato ⊆ Java.
Valores fictícios; nenhuma API de verdade é chamada.
"""

import inspect
import json
import unittest
from pathlib import Path

import proposta
from bracos import Bracos, ErroBraco
from intencoes import responder
from rotinas import _sentinela, aviso_do_alarme, bom_dia

CONTRATO = Path(__file__).resolve().parents[2] / "docs" / "contratos" / "bracos-python.json"


def _rotas():
    with open(CONTRATO, encoding="utf-8") as f:
        return {r["metodoPython"]: r for r in json.load(f)["rotas"]}


class Espiao(dict):
    """Um dict que anota cada campo que o Python tenta ler (get, [ ], in). Listas de objetos viram espiões também."""

    def __init__(self, dados, lidos, prefixo=""):
        super().__init__()
        self._lidos, self._prefixo = lidos, prefixo
        for chave, valor in dados.items():
            if isinstance(valor, list):
                valor = [Espiao(v, lidos, f"{prefixo}{chave}[].") if isinstance(v, dict) else v for v in valor]
            dict.__setitem__(self, chave, valor)

    def _anotar(self, chave):
        self._lidos.add(self._prefixo + str(chave))

    def get(self, chave, padrao=None):
        self._anotar(chave)
        return super().get(chave, padrao)

    def __getitem__(self, chave):
        self._anotar(chave)
        return super().__getitem__(chave)

    def __contains__(self, chave):
        self._anotar(chave)
        return super().__contains__(chave)


def _campos(exemplo, prefixo=""):
    """Todos os campos do exemplo do contrato, com o caminho das listas (ex.: 'portas[].risco')."""
    campos = set()
    for chave, valor in exemplo.items():
        campos.add(prefixo + chave)
        if isinstance(valor, list) and valor and isinstance(valor[0], dict):
            campos |= _campos(valor[0], f"{prefixo}{chave}[].")
    return campos


class _BracosGravador(Bracos):
    """Bracos de verdade, mas o _pedir só anota (método, rota, corpo) e devolve o exemplo do contrato."""

    def __init__(self, rotas):
        super().__init__(token="token-ficticio-de-teste")
        self.rotas, self.chamadas = rotas, []

    def _pedir(self, metodo, rota, corpo=None):
        self.chamadas.append((metodo, rota, corpo))
        for r in self.rotas.values():
            if r["rota"] == rota:
                return dict(r["resposta"])
        return {}


class ContratoTest(unittest.TestCase):

    def setUp(self):
        self.rotas = _rotas()

    # ---------- 1) rota e método ----------

    def test_nenhuma_acao_do_bracos_fora_do_contrato(self):
        publicos = {nome for nome, _ in inspect.getmembers(Bracos, inspect.isfunction) if not nome.startswith("_")}
        sem_contrato = publicos - set(self.rotas)
        self.assertEqual(sem_contrato, set(), "ação nova no bracos.py? Ponha no docs/contratos/bracos-python.json")
        self.assertIn("_conferir_servidor", self.rotas, "o aperto de mão também é contrato")

    def test_cada_acao_chama_a_rota_e_o_metodo_do_contrato(self):
        for nome, r in self.rotas.items():
            if nome == "_conferir_servidor":
                continue
            with self.subTest(nome):
                b = _BracosGravador(self.rotas)
                args = ["x"] * len(r["pedido"])
                getattr(b, nome)(*args)
                self.assertEqual(len(b.chamadas), 1)
                metodo, rota, _ = b.chamadas[0]
                self.assertEqual((metodo, rota), (r["metodo"], r["rota"]))

    def test_aperto_de_mao_usa_a_rota_do_contrato_e_le_so_a_prova(self):
        r = self.rotas["_conferir_servidor"]
        b = Bracos(token="token-ficticio-de-teste")
        vistos, lidos = [], set()

        def enviar(pedido):
            vistos.append((pedido.get_method(), pedido.full_url))
            return Espiao(r["resposta"], lidos)

        b._enviar = enviar
        with self.assertRaises(ErroBraco):  # a prova do exemplo é falsa: impostor, como deve ser
            b._conferir_servidor()
        metodo, url = vistos[0]
        self.assertEqual(metodo, r["metodo"])
        self.assertTrue(url.split("?")[0].endswith(r["rota"]), url)
        self.assertLessEqual(lidos, _campos(r["resposta"]))

    # ---------- 2) corpo do pedido ----------

    def test_corpo_so_com_campos_do_contrato_e_cortado_no_maximo(self):
        for nome, r in self.rotas.items():
            if not r["pedido"]:
                continue
            with self.subTest(nome):
                b = _BracosGravador(self.rotas)
                gigantes = ["a" * (regra["max"] + 500) for regra in r["pedido"].values()]
                getattr(b, nome)(*gigantes)
                corpo = b.chamadas[0][2]
                self.assertEqual(set(corpo), set(r["pedido"]), "o Python manda campo fora do contrato?")
                for campo, regra in r["pedido"].items():
                    self.assertEqual(len(corpo[campo]), regra["max"], f"{campo}: corte diferente do @Size do Java")

    def test_campo_obrigatorio_vazio_nem_chama_a_api(self):
        for nome, r in self.rotas.items():
            obrigatorios = [c for c, regra in r["pedido"].items() if regra["obrigatorio"]]
            if not obrigatorios:
                continue
            with self.subTest(nome):
                b = _BracosGravador(self.rotas)
                with self.assertRaises(ErroBraco):
                    getattr(b, nome)(*(["  "] * len(r["pedido"])))
                self.assertEqual(b.chamadas, [])

    # ---------- 3) o que o Python lê da resposta ----------

    def _espiar(self, nome):
        lidos = set()
        return Espiao(self.rotas[nome]["resposta"], lidos), lidos

    def _conferir_leitura(self, nome, lidos):
        fora = lidos - _campos(self.rotas[nome]["resposta"])
        self.assertEqual(fora, set(), f"{nome}: o Python lê campo que não está no contrato")
        self.assertTrue(lidos, f"{nome}: nenhum campo lido? o espião não pegou nada")

    def test_frases_faladas_so_leem_campos_do_contrato(self):
        acoes = {"hora": "hora", "pendencias": "pendencias", "git_status": "git_status", "portas": "portas",
                 "integridade": "integridade", "golpe": "golpe"}
        for nome, acao in acoes.items():
            with self.subTest(nome):
                dados, lidos = self._espiar(nome)
                frase = responder(acao, dados)
                self.assertNotIn("formato estranho", frase)
                self._conferir_leitura(nome, lidos)

    def test_bom_dia_so_le_campos_do_contrato(self):
        espioes = {n: self._espiar(n) for n in ("hora", "pendencias", "git_status", "portas", "integridade",
                                                 "isca", "alarme")}

        class BracosEspiao:
            pass

        b = BracosEspiao()
        for nome, (dados, _) in espioes.items():
            setattr(b, nome, lambda d=dados: d)
        frase = bom_dia(b)
        self.assertNotIn("formato estranho", frase)
        for nome, (_, lidos) in espioes.items():
            with self.subTest(nome):
                self._conferir_leitura(nome, lidos)

    def test_sentinela_le_isca_e_alarme_com_os_nomes_do_contrato(self):
        isca, lidos_isca = self._espiar("isca")
        alarme, lidos_alarme = self._espiar("alarme")
        frase = _sentinela(None, None, isca, alarme)
        # O exemplo do contrato tem isca tocada e alarme disparado: os dois PRECISAM virar alerta.
        self.assertIn("a isca foi tocada", frase)
        self.assertIn("alarme de tentativas", frase)
        self._conferir_leitura("isca", lidos_isca)
        self._conferir_leitura("alarme", lidos_alarme)

    def test_aviso_do_alarme_le_so_o_contrato(self):
        dados, lidos = self._espiar("alarme")

        class B:
            def alarme(self):
                return dados

        frase, visto = aviso_do_alarme(B(), 0)
        self.assertIsNotNone(frase, "o exemplo tem disparo: tem que avisar")
        self.assertEqual(visto, 1)
        self._conferir_leitura("alarme", lidos)

    def test_proposta_so_le_campos_do_contrato(self):
        dados, lidos = self._espiar("proposta")
        self.assertTrue(proposta.formatar(dados))
        self._conferir_leitura("proposta", lidos)


if __name__ == "__main__":
    unittest.main()
