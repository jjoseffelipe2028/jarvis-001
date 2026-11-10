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
- Aperto de mão: a API prova que conhece o segredo (HMAC). Impostor é recusado.
- O token NUNCA viaja: cada pedido vai assinado (HMAC + hora + nonce de uso único).
"""

import hashlib
import hmac
import json
import os
import re
import secrets
import time
import urllib.error
import urllib.request
from pathlib import Path

ENDERECO = "http://127.0.0.1:8080"
LIMITE_RESPOSTA = 1024 * 1024
TEMPO_LIMITE = 10
ENV_DA_RAIZ = Path(__file__).resolve().parent.parent / ".env"
# Formato aceito para o token: sem espaço nem quebra de linha (evita erro que imprime o valor)
FORMATO_TOKEN = re.compile(r"[A-Za-z0-9._~+/=-]{8,512}")
# Aperto de mão: o mesmo prefixo do ProvaController.java
PREFIXO_PROVA = "jarvis-servidor:"
# Assinatura por pedido: o mesmo rótulo do Assinatura.java
ROTULO_SUBCHAVE = b"jarvis-assinatura-v1"
DESLIGADA = "Os braços Java estão desligados. Rode o iniciar.sh."
IMPOSTOR = "Quem respondeu na porta da API não provou ser o Jarvis. Não mandei nada."


class ErroBraco(Exception):
    """Falha ao falar com os braços. A mensagem já é uma frase curta para o José.
    `status` = código HTTP da API (401, 503...) quando houver."""

    def __init__(self, mensagem, status=None):
        super().__init__(mensagem)
        self.status = status

    @property
    def alarme(self):
        """True = possível ataque (impostor ou assinatura recusada): nunca esconder do José."""
        return self.args[0] == IMPOSTOR or self.status == 401


class _SemRedirecionar(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Seguir um redirecionamento levaria o token para outro endereço
        raise ErroBraco("A API tentou me redirecionar. Parei por segurança.")


def assinar(token, metodo, rota, corpo, hora=None, nonce=None):
    """
    Assina um pedido (Ticket 007), igual ao Assinatura.java:
    subchave = HMAC(token, "jarvis-assinatura-v1")
    assinatura = HMAC(subchave, MÉTODO\\nROTA\\nHORA\\nNONCE\\nSHA256(corpo))
    O token fica no PC: só a assinatura viaja. Um nonce novo por pedido impede repetir o pedido.
    """
    hora = str(int(time.time())) if hora is None else hora
    nonce = secrets.token_hex(16) if nonce is None else nonce
    subchave = hmac.new(token.encode("utf-8"), ROTULO_SUBCHAVE, hashlib.sha256).digest()
    mensagem = "\n".join([metodo, rota, hora, nonce, hashlib.sha256(corpo).hexdigest()])
    assinatura = hmac.new(subchave, mensagem.encode("utf-8"), hashlib.sha256).hexdigest()
    return {"X-Jarvis-Hora": hora, "X-Jarvis-Nonce": nonce, "X-Jarvis-Assinatura": assinatura}


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

    def portas(self):
        """Ticket 021: Sentinela, só lê quais portas estão abertas para a rede."""
        return self._pedir("GET", "/acoes/sentinela/portas")

    def integridade(self):
        """Ticket 022: as travas do projeto mudaram desde que a API ligou? (só lê)"""
        return self._pedir("GET", "/acoes/sentinela/integridade")

    def golpe(self, texto):
        """Ticket 023: essa mensagem tem cara de golpe? Manda só o TEXTO; o Java nunca abre o link."""
        texto = (texto or "").strip()
        if not texto:
            raise ErroBraco("Cola a mensagem depois dos dois pontos. Ex.: isso é golpe: <mensagem>")
        return self._pedir("POST", "/acoes/sentinela/golpe", {"texto": texto[:4000]})

    # ---------- o pedido em si ----------

    def _pedir(self, metodo, rota, corpo=None):
        if not self._token:
            raise ErroBraco("Estou sem o token. Confira o JARVIS_TOKEN no .env.")
        if not FORMATO_TOKEN.fullmatch(self._token):
            raise ErroBraco("O token do .env tem um formato estranho (espaço ou quebra de linha?).")
        self._conferir_servidor()  # a API prova quem é (Ticket 006)
        dados = None if corpo is None else json.dumps(corpo).encode("utf-8")
        pedido = urllib.request.Request(self._endereco + rota, data=dados, method=metodo)
        # Ticket 007: o token NUNCA viaja. O pedido vai assinado (vale 1 vez, por 60 s).
        for nome, valor in assinar(self._token, metodo, rota, dados or b"").items():
            pedido.add_header(nome, valor)
        pedido.add_header("X-Jarvis-Cliente", "python")
        if dados is not None:
            pedido.add_header("Content-Type", "application/json")
        return self._enviar(pedido)

    def _conferir_servidor(self):
        """
        Aperto de mão (challenge-response) ANTES de mandar o token. 🔒
        Mando um número aleatório; só a API verdadeira, que conhece o token, sabe devolver
        HMAC-SHA256(token, "jarvis-servidor:" + número). Um programa impostor na porta 8080
        não sabe, e o token não sai daqui.
        """
        desafio = secrets.token_hex(16)  # 32 letras hex, novo a cada pedido (não dá para repetir)
        pedido = urllib.request.Request(
            f"{self._endereco}/acoes/prova?desafio={desafio}", method="GET")  # SEM o token
        pedido.add_header("X-Jarvis-Cliente", "python")
        try:
            dados = self._enviar(pedido)
        except ErroBraco as e:
            # Desligada ou ocupada (503) é problema comum, não ataque: mostra a frase certa
            if e.args and e.args[0] == DESLIGADA or e.status == 503:
                raise
            raise ErroBraco(IMPOSTOR) from None
        esperado = hmac.new(self._token.encode("utf-8"),
                            (PREFIXO_PROVA + desafio).encode("utf-8"), hashlib.sha256).hexdigest()
        recebido = dados.get("prova")
        # compare_digest: compara em tempo constante (não dá pista pelo tempo de resposta)
        if not isinstance(recebido, str) or not hmac.compare_digest(esperado, recebido):
            raise ErroBraco(IMPOSTOR)

    def _enviar(self, pedido):
        try:
            with self._abridor.open(pedido, timeout=TEMPO_LIMITE) as resposta:
                return _ler_json(resposta)
        except urllib.error.HTTPError as e:
            raise ErroBraco(_frase_do_erro(e.code, _ler_json_seguro(e)), status=e.code) from None
        except ErroBraco:
            raise
        except (urllib.error.URLError, ConnectionError, TimeoutError, OSError):
            raise ErroBraco(DESLIGADA) from None
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
    "sentinela ocupada": "a sentinela já está vigiando, tenta de novo em 1 segundo",
    "sentinela so no windows": "a sentinela só funciona no Windows",
    "netstat indisponivel": "não achei o netstat do Windows",
    "demorou demais": "o netstat demorou demais",
    "projeto nao encontrado": "não achei a pasta do projeto para vigiar as travas",
    "muitos pedidos": "a API está ocupada agora, tenta de novo em 1 segundo",
    "tente de novo": "a API está ocupada agora, tenta de novo em 1 segundo",
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
