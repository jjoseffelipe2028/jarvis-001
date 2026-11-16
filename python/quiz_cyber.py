"""
Jarvis 001 — Quiz de Cyber por voz 🎓🔊 (Ticket 086)

5 perguntas de cibersegurança tiradas do PRÓPRIO Jarvis (o que você construiu e precisa saber explicar na entrevista).
O Jarvis lê a pergunta e as opções; você responde "A", "B" ou "C" (digitando ou falando) e ele explica o PORQUÊ.

  python quiz_cyber.py            → digitando (o Jarvis também fala, se a boca estiver instalada)
  python quiz_cyber.py --voz      → respondendo pelo microfone
  python quiz_cyber.py --anotar   → no fim, anota o placar no daily (pela API Java, pedido assinado)

Regras:
- Perguntas e explicações são FIXAS no código: nada vem da internet nem é inventado por IA (não dá para "alucinar").
- A resposta falada só é comparada com A/B/C: o texto ouvido nunca vira comando.
- Só anota no daily com --anotar (a ação de escrever é sempre pedida de propósito).
"""

import random
import re
import sys
import unicodedata
from dataclasses import dataclass

POR_RODADA = 5
LETRAS = ("A", "B", "C")


@dataclass(frozen=True)
class Pergunta:
    texto: str
    opcoes: tuple   # 3 opções, na ordem A, B, C
    certa: str      # "A", "B" ou "C"
    porque: str     # a explicação em 1 frase (é isso que se fala na entrevista)


BANCO = [
    Pergunta("Por que a API do Jarvis escuta só em 127.0.0.1?",
             ("Porque é mais rápido", "Para que nenhum outro aparelho da rede consiga chamar a API", "Porque o Java exige"),
             "B", "127.0.0.1 é o próprio PC: quem está no Wi-Fi do hotel não alcança a porta 8080."),
    Pergunta("O que é uma allowlist?",
             ("Uma lista do que é PERMITIDO; todo o resto é negado", "Uma lista do que é proibido", "Uma lista de usuários"),
             "A", "Bloqueio por padrão: ação nova só existe se alguém escrever ela à mão na lista."),
    Pergunta("Que ataque o token secreto no cabeçalho evita na API do Jarvis?",
             ("SQL Injection", "CSRF: um site malicioso no navegador chamando a API", "DDoS"),
             "B", "Um site de fora não sabe o token, então o pedido que ele forja no seu navegador leva 401."),
    Pergunta("Por que comparar o token com MessageDigest.isEqual e não com equals?",
             ("Para evitar o timing attack: o tempo da resposta não pode vazar o token", "Porque é mais curto", "Por causa do UTF-8"),
             "A", "O equals para no primeiro caractere errado; medindo o tempo, dá para adivinhar o token aos poucos."),
    Pergunta("O que é Path Traversal?",
             ("Um vírus de pendrive", "Usar ../ no caminho para sair da pasta permitida", "Um tipo de criptografia"),
             "B", "Por isso o nome do arquivo é gerado pelo servidor e o caminho final é conferido com startsWith."),
    Pergunta("O que é um honeytoken, como a isca do Jarvis?",
             ("Um token falso que ninguém do bem usa: se alguém usar, é alarme", "Um token de backup", "Um token que expira rápido"),
             "A", "Qualquer toque na isca prova que alguém leu onde não devia (MITRE Engage: dissimulação)."),
    Pergunta("5 pedidos negados em 1 minuto disparam o alarme do Jarvis. Que técnica do MITRE isso ajuda a ver?",
             ("T1110, Brute Force: alguém tentando adivinhar o token", "T1566, Phishing", "T1486, Ransomware"),
             "A", "Muitos 401 seguidos é a cara de força bruta; o alarme só avisa, não bloqueia."),
    Pergunta("Por que o alarme de tentativas avisa, mas NÃO bloqueia quem errou?",
             ("Porque bloquear é difícil em Java", "Porque o atacante poderia errar de propósito para trancar o José fora",
              "Porque o log ficaria grande"),
             "B", "Bloqueio automático vira arma de negação de serviço contra o próprio dono."),
    Pergunta("Malware que se coloca na chave Run do registro quer o quê?",
             ("Roubar a senha do Wi-Fi", "Voltar sozinho a cada vez que o Windows liga (persistência, T1547.001)", "Apagar o registro"),
             "B", "É por isso que o vigia da inicialização avisa item novo ou que mudou de programa."),
    Pergunta("O que é prompt injection?",
             ("Texto que tenta dar ordens à IA, escondido no pedido ou num documento", "Um erro de digitação no prompt",
              "Injetar SQL no banco"),
             "A", "OWASP LLM01. Por isso a Gemma nunca executa nada: ação só por frase fixa e allowlist no Java."),
    Pergunta("O jarvis-redteam mostrou que a Gemma repete o prompt de sistema. Qual a lição?",
             ("Pôr no prompt 'nunca repita' resolve", "Tratar o prompt como público: nunca pôr segredo nele", "Trocar de IA"),
             "B", "Instrução no prompt não é barreira de segurança; a frase 'nunca repita' falhou 3 de 3."),
    Pergunta("Para que serve o gitleaks antes do commit?",
             ("Achar senha ou token no código antes de ir para o GitHub", "Deixar o commit mais rápido", "Formatar o código"),
             "A", "Segredo publicado fica no histórico e em cópias: o certo é nunca deixar ele subir."),
    Pergunta("Por que o Jarvis limpa quebra de linha antes de escrever no log?",
             ("Para economizar espaço", "Contra log injection: um texto com quebra de linha forjaria uma linha falsa no log",
              "Por causa do Windows"),
             "B", "Quem controla o texto poderia escrever 'resultado=ok' falso no meio da auditoria."),
    Pergunta("O que o filtro de Host (só localhost e 127.0.0.1) evita?",
             ("DNS rebinding: um site apontando o próprio nome para 127.0.0.1", "Vírus no pendrive", "Senha fraca"),
             "A", "O navegador manda 'Host: evil.com' e a API responde 403."),
    Pergunta("O guardião do contrato Java ↔ Python pega qual problema?",
             ("Senha fraca", "Campo renomeado de um lado só, que faria o alarme sumir em silêncio", "Porta aberta"),
             "B", "O Python lê com .get(campo, padrão): sem o teste, 'toques' renomeado viraria 0 e ninguém veria."),
]


def sortear(banco=BANCO, quantas=POR_RODADA, aleatorio=random):
    return aleatorio.sample(list(banco), min(quantas, len(banco)))


_FALADO = {"a": "A", "ah": "A", "á": "A", "b": "B", "be": "B", "bê": "B", "c": "C", "ce": "C", "cê": "C", "se": "C"}


def entender(resposta, pergunta=None):
    """Texto digitado ou ouvido → "A"/"B"/"C" ou None. Aceita "b", "letra B", "a resposta é cê" ou o texto da opção."""
    texto = unicodedata.normalize("NFC", str(resposta or "")).strip().lower()[:200]
    texto = re.sub(r"[^\w\sáéíóúâêôãõç]", " ", texto)
    palavras = texto.split()
    for marca in ("letra", "opção", "opcao", "é", "e"):
        if marca in palavras[:-1]:
            depois = palavras[palavras.index(marca) + 1]
            if depois in _FALADO:
                return _FALADO[depois]
    if len(palavras) == 1 and palavras[0] in _FALADO:
        return _FALADO[palavras[0]]
    if pergunta is not None and len(texto) >= 6:
        for letra, opcao in zip(LETRAS, pergunta.opcoes):
            if texto in opcao.lower():
                return letra
    return None


def ler_em_voz(pergunta, numero):
    opcoes = " ".join(f"{letra}: {o}." for letra, o in zip(LETRAS, pergunta.opcoes))
    return f"Pergunta {numero}. {pergunta.texto} {opcoes}"


def jogar(perguntas, responder, falar, tentativas=2):
    """`responder()` devolve o texto do José; `falar(texto)` mostra/fala. → acertos."""
    acertos = 0
    for n, p in enumerate(perguntas, start=1):
        falar(ler_em_voz(p, n))
        letra = None
        for _ in range(tentativas):
            letra = entender(responder(), p)
            if letra:
                break
            falar("Não entendi. Responda A, B ou C.")
        if letra == p.certa:
            acertos += 1
            falar(f"Certo! {p.porque}")
        else:
            falar(f"Era a {p.certa}. {p.porque}")
    falar(placar(acertos, len(perguntas)))
    return acertos


def placar(acertos, total):
    if acertos == total:
        return f"Você acertou {acertos} de {total}. Pronto para a entrevista!"
    if acertos >= total * 0.6:
        return f"Você acertou {acertos} de {total}. Bom! Revise as que errou."
    return f"Você acertou {acertos} de {total}. Vale reler os tickets de segurança e tentar de novo."


def main(argv=None, responder=None, falar=None, bracos=None, aleatorio=random):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    argv = sys.argv[1:] if argv is None else argv
    if not set(argv) <= {"--voz", "--anotar"}:
        print("Uso: python quiz_cyber.py [--voz] [--anotar]")
        return 2
    if falar is None:
        falar = _falar_de_verdade()
    if responder is None:
        responder = _ouvir_de_verdade() if "--voz" in argv else (lambda: input("Sua resposta (A/B/C): "))
    perguntas = sortear(aleatorio=aleatorio)
    acertos = jogar(perguntas, responder, falar)
    if "--anotar" in argv:
        try:
            from bracos import Bracos, ErroBraco
            (bracos or Bracos()).registrar_daily(f"Quiz de Cyber: {acertos}/{len(perguntas)}")
            print("📝 Anotei o placar no daily.")
        except Exception as e:  # noqa: BLE001 - API desligada não estraga o quiz
            print(f"(não consegui anotar no daily: {e})")
    return 0


def _falar_de_verdade():
    """Mostra na tela e fala, se a boca (Piper) estiver instalada. Sem boca → só tela."""
    try:
        from boca import carregar_boca, falar
        boca = carregar_boca()
    except Exception:  # noqa: BLE001
        return print

    def mostrar_e_falar(texto):
        print(f"🎓 {texto}")
        falar(boca, texto)
    return mostrar_e_falar


def _ouvir_de_verdade():
    from ouvido import carregar_ouvido, ouvir
    ouvido = carregar_ouvido()

    def responder():
        input("(Enter e fale a resposta) ")
        texto = ouvir(ouvido)
        print(f"🎙️ Você disse: {texto}")
        return texto
    return responder


if __name__ == "__main__":
    sys.exit(main())
