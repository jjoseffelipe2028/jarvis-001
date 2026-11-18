"""
Ticket 082: teste-guarda de ARQUITETURA (lado Python). 🏛️
As regras de ouro do CLAUDE.md viram teste para os programas em python/*.py:
1) nada escuta na rede toda (0.0.0.0, "::");
2) nada de comando pelo shell (shell=True, os.system, os.popen): o texto viraria comando;
3) só os arquivos já revisados citam o arquivo de segredos (novo arquivo citando = revisão obrigatória).
Lê o código pelo `tokenize`: comentário não conta, e "shell=True" dentro de um texto (ex.: regra do auditor) também não.
"""

import io
import tokenize
import unittest
from pathlib import Path

PASTA = Path(__file__).resolve().parents[1]
ARQUIVO_SEGREDOS = "." + "env"  # montado: este teste também não cita o nome cru

# Só estes leem (ou conferem se existe) o arquivo de segredos. Novo? Revise e acrescente com o número do ticket.
#   bracos.py (token da API) · cortador.py (pastas dos cortes) · conferir.py (só confere SE existe).
PODEM_CITAR_SEGREDOS = {"bracos.py", "conferir.py", "cortador.py"}
ESCUTA_NA_REDE = {"0.0.0.0", "::", "[::]"}


def _tokens(caminho):
    with open(caminho, "rb") as f:
        return [t for t in tokenize.tokenize(f.readline)
                if t.type not in (tokenize.COMMENT, tokenize.NL, tokenize.NEWLINE, tokenize.INDENT, tokenize.DEDENT)]


def _texto(token):
    """Valor de um token de texto ("abc", f"abc"...), sem as aspas. Outros tokens → None."""
    if token.type != tokenize.STRING:
        return None
    return token.string


def _e_o_arquivo(texto):
    """O texto É o nome/caminho do arquivo de segredos? ("…/.e·nv" sim; "confira no .e·nv" numa frase, não.)"""
    miolo = texto.strip("rbfuRBFU").strip("'\"")
    return miolo == ARQUIVO_SEGREDOS or miolo.endswith(("/" + ARQUIVO_SEGREDOS, "\\" + ARQUIVO_SEGREDOS))


def _programas():
    arquivos = sorted(PASTA.glob("*.py"))
    assert len(arquivos) > 20, "não achou os programas em python/"
    return arquivos


class ArquiteturaTest(unittest.TestCase):

    def test_nada_escuta_na_rede_toda(self):
        for arquivo in _programas():
            for t in _tokens(arquivo):
                texto = _texto(t)
                if texto is None:
                    continue
                miolo = texto.strip("rbfuRBFU").strip("'\"")
                with self.subTest(arquivo.name, linha=t.start[0]):
                    self.assertNotIn(miolo, ESCUTA_NA_REDE, "endereço que escuta na rede toda")
                    self.assertNotIn("0.0.0.0", miolo)

    def test_nada_de_shell(self):
        for arquivo in _programas():
            tokens = _tokens(arquivo)
            for i, t in enumerate(tokens[:-2]):
                trio = (t.string, tokens[i + 1].string, tokens[i + 2].string)
                with self.subTest(arquivo.name, linha=t.start[0]):
                    self.assertNotEqual(trio, ("shell", "=", "True"), "shell=True: o texto viraria comando")
                    self.assertFalse(trio[0] == "os" and trio[1] == "." and trio[2] in ("system", "popen"),
                                     "os.system/os.popen: comando pelo shell")

    def test_so_arquivos_revisados_citam_o_arquivo_de_segredos(self):
        citam = set()
        for arquivo in _programas():
            for t in _tokens(arquivo):
                texto = _texto(t)
                if texto is not None and _e_o_arquivo(texto):
                    citam.add(arquivo.name)
        novos = citam - PODEM_CITAR_SEGREDOS
        self.assertEqual(novos, set(), "arquivo novo citando o arquivo de segredos: revise e acrescente na lista")

    def test_o_guarda_ignora_comentario_e_texto_mas_pega_codigo(self):
        def achar(fonte):
            tokens = [t for t in tokenize.tokenize(io.BytesIO(fonte.encode()).readline)
                      if t.type != tokenize.COMMENT]
            return any((tokens[i].string, tokens[i + 1].string, tokens[i + 2].string) == ("shell", "=", "True")
                       for i in range(len(tokens) - 2))

        self.assertFalse(achar("# subprocess.run(x, shell=True)\n"))
        self.assertFalse(achar('regra = "shell=True"\n'))
        self.assertTrue(achar("subprocess.run(x, shell=True)\n"))


if __name__ == "__main__":
    unittest.main()
