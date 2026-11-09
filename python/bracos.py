"""
Jarvis 001 — Ponte para os BRAÇOS (API Java) 🦾

O cérebro (Python) só PEDE; quem executa é a API Java, e só ações da lista.
Só biblioteca padrão do Python (urllib): nenhuma instalação nova. 🔒

Travas:
- Endereço FIXO: http://127.0.0.1:8080 (nada sai do PC).
- Token lido da variável JARVIS_TOKEN ou do .env da raiz. Nunca é impresso.
- Proxy desligado: uma variável HTTP_PROXY no PC não desvia o pedido (nem o token).
- Redirecionamento recusado: a API nunca redireciona; se acontecer, é golpe → para.
- Resposta com no máximo 1 MB e tempo limite de 10 s.
"""

import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path

ENDERECO = "http://127.0.0.1:8080"
LIMITE_RESPOSTA = 1024 * 1024
TEMPO_LIMITE = 10
ENV_DA_RAIZ = Path(__file__).resolve().parent.parent / ".env"
# Formato aceito para o token: sem espaço nem quebra de linha (evita erro que imprime o valor)
FORMATO_TOKEN = re.compile(r"[A-Za-z0-9._~+/=-]{8,512}")


class ErroBraco(Exception):
    """Falha ao falar com os braços. A mensagem já é uma frase curta para o José."""


class _SemRedirecionar(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Seguir um redirecionamento levaria o token para outro endereço
        raise ErroBraco("A API tentou me redirecionar. Parei por segurança.")


def ler_token(env=os.environ, arquivo=ENV_DA_RAIZ):
    """Token da variável de ambiente; se não tiver, só a linha JARVIS_TOKEN do .env."""
    token = env.get("JARVIS_TOKEN", "").strip()
    if token:
        return token
    try:
        # utf-8-sig: aceita .env salvo com BOM pelo Bloco de Notas
        for linha in arquivo.read_text(encoding="utf-8-sig").splitlines():
            linha = linha.strip()
            if linha.startswith("export "):
                linha = linha[len("export "):].strip()
            if linha.startswith("JARVIS_TOKEN="):
                return linha.split("=", 1)[1].strip().strip('"').strip("'")
    except OSError:
        pass
    return ""


class Bracos:
    """Cliente da API Java. Uma função por ação da lista; nada de URL livre."""

    def __init__(self, token=None, endereco=ENDERECO):
        # O token só pode ir para o próprio PC: qualquer outro endereço é recusado
        if not re.fullmatch(r"http://127\.0\.0\.1:\d{1,5}", endereco):
            raise ValueError("endereco precisa ser http://127.0.0.1:<porta>")
        self._endereco = endereco
        self._token = ler_token() if token is None else token
        # ProxyHandler({}) = nenhum proxy, mesmo que o PC tenha HTTP_PROXY configurado
        self._abridor = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), _SemRedirecionar())

    def __repr__(self):
        return "Bracos(token=***)"  # o token nunca aparece, nem em print/erro

    # ---------- as ações (allowlist) ----------

    def hora(self):
        return self._pedir("GET", "/acoes/hora")

    def registrar_daily(self, texto):
        texto = (texto or "").strip()
        if not texto:
            raise ErroBraco("O que eu anoto? Não ouvi o texto.")
        return self._pedir("POST", "/acoes/daily", {"texto": texto[:2000]})

    def pendencias(self):
        return self._pedir("GET", "/acoes/pendencias")

    def git_status(self):
        return self._pedir("GET", "/acoes/git-status")

    # ---------- o pedido em si ----------

    def _pedir(self, metodo, rota, corpo=None):
        if not self._token:
            raise ErroBraco("Estou sem o token. Confira o JARVIS_TOKEN no .env.")
        if not FORMATO_TOKEN.fullmatch(self._token):
            raise ErroBraco("O token do .env tem um formato estranho (espaço ou quebra de linha?).")
        dados = None if corpo is None else json.dumps(corpo).encode("utf-8")
        pedido = urllib.request.Request(self._endereco + rota, data=dados, method=metodo)
        pedido.add_header("X-Jarvis-Token", self._token)
        pedido.add_header("X-Jarvis-Cliente", "python")
        if dados is not None:
            pedido.add_header("Content-Type", "application/json")
        try:
            with self._abridor.open(pedido, timeout=TEMPO_LIMITE) as resposta:
                return _ler_json(resposta)
        except urllib.error.HTTPError as e:
            raise ErroBraco(_frase_do_erro(e.code, _ler_json_seguro(e))) from None
        except ErroBraco:
            raise
        except (urllib.error.URLError, ConnectionError, TimeoutError, OSError):
            raise ErroBraco("Os braços Java estão desligados. Rode o iniciar.sh.") from None
        except Exception:
            # Qualquer outra falha (resposta torta etc.): frase fixa, sem traceback e sem dados
            raise ErroBraco("Falha ao falar com os braços Java.") from None


def _ler_json(resposta):
    bruto = resposta.read(LIMITE_RESPOSTA + 1)
    if len(bruto) > LIMITE_RESPOSTA:
        raise ErroBraco("Resposta grande demais. Parei por segurança.")
    try:
        dados = json.loads(bruto.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise ErroBraco("A API respondeu algo que não entendi.") from None
    if not isinstance(dados, dict):
        raise ErroBraco("A API respondeu algo que não entendi.")
    return dados


def _ler_json_seguro(erro):
    try:
        return _ler_json(erro)
    except ErroBraco:
        return {}


# Mensagens fixas da API (503) → frase para o José. Texto de fora nunca é repetido cru.
_MOTIVOS_503 = {
    "token nao configurado": "a API está sem token. Confira o .env e reinicie",
    "pasta nao configurada": "a pasta não está configurada no .env",
    "pasta indisponivel": "a pasta configurada não existe",
    "daily cheio": "o daily de hoje passou de 1 MB",
    "git indisponivel": "não achei o git",
    "git ocupado": "o git já está rodando, tenta de novo",
    "git demorou demais": "o git demorou demais",
    "repositorio indisponivel": "a pasta não é um repositório git",
    "saida do git grande demais": "a saída do git ficou grande demais",
}


def _frase_do_erro(status, corpo):
    if status == 401:
        return "Meu token não confere com o da API. Confira o .env."
    if status == 403:
        return "A API recusou o endereço do pedido."
    if status == 400:
        return "A API não aceitou o pedido. Tenta com outras palavras."
    if status == 503:
        motivo = _MOTIVOS_503.get(str(corpo.get("erro", "")), "a ação está indisponível")
        return f"Não deu: {motivo}."
    return f"A API deu erro {int(status)}."
