"""
Jarvis 001 — Cérebro (versão 4: ouve, pensa, fala e PEDE ações aos braços)

Conversa com o Gemma, que roda no PC pelo Ollama (offline).
OUVE: Enter vazio = falar (ouvido.py, faster-whisper).
FALA: lê a resposta em voz alta (boca.py, Piper).
AÇÕES: frases da lista (intencoes.py) viram pedido à API Java (bracos.py). 🔒
O Gemma nunca escolhe nem executa ação: quem decide são regras fixas + a API Java.
"""

import ollama  # biblioteca oficial do Ollama
from ouvido import carregar_ouvido, ouvir  # o nosso ouvido (ouvido.py)
from boca import carregar_boca, falar      # a nossa boca (boca.py)
from bracos import Bracos, ErroBraco       # ponte para a API Java (bracos.py)
from intencoes import detectar, responder, seguro  # pedido de ação ou conversa? (intencoes.py)
from rotinas import bom_dia                # "bom dia, Jarvis" (rotinas.py)

MODELO = "gemma4:e2b"  # o modelo que baixamos (cabe na RTX 4050)

# A "personalidade" do Jarvis (inspirada na soul.md)
PERSONALIDADE = (
    "Você é o Jarvis, assistente pessoal do José, inspirado no Jarvis do Homem de Ferro. "
    "Tom: educado, confiante e com um toque leve de humor. "
    "Fale diretamente com o José, usando 'você' (nunca fale dele na terceira pessoa). "
    "Responda sempre em português do Brasil, em até 3 frases: curto, mas completo "
    "(nunca responda com uma frase vaga). "
    "Sobre o José: motorista de Uber que estuda Java back-end e cibersegurança na EBAC, "
    "com a meta de conseguir o primeiro emprego em tecnologia. "
    "Quando fizer sentido, incentive os estudos, principalmente a cibersegurança. "
    "Se não souber algo, diga que não sabe em vez de inventar. "
    "Você NÃO executa nada no computador: nunca diga que apagou, instalou, abriu ou enviou algo. "
    "Se pedirem uma ação, diga que só faz as ações da lista: anotar no daily, pendências, "
    "git status, hora e o resumo de bom dia."
)


def executar_acao(bracos, acao, argumento):
    """Pede a ação aos braços Java e devolve a frase para falar."""
    if acao == "proibida":
        return responder("proibida", None)
    if acao == "bom_dia":
        return bom_dia(bracos)  # junta hora + pendências + git (Ticket 009)
    try:
        if acao == "daily":
            dados = bracos.registrar_daily(argumento)
        elif acao == "pendencias":
            dados = bracos.pendencias()
        elif acao == "git_status":
            dados = bracos.git_status()
        elif acao == "hora":
            dados = bracos.hora()
        else:
            return responder("proibida", None)
    except ErroBraco as e:
        return str(e)
    return responder(acao, dados)


def main():
    # A conversa começa com a personalidade (mensagem de "sistema")
    historico = [{"role": "system", "content": PERSONALIDADE}]
    bracos = Bracos()  # lê o token 1 vez (nunca imprime)

    ouvido = carregar_ouvido()  # liga o ouvido 1 vez só (o "ligar o carro")
    boca = carregar_boca()      # liga a boca 1 vez só

    print("🤖 Jarvis: Olá, José! Digite, ou aperte Enter VAZIO para FALAR. ('sair' encerra)")

    while True:
        pergunta = input("\nVocê: ").strip()
        veio_da_voz = False

        if not pergunta:  # Enter vazio → modo voz 🎙️
            veio_da_voz = True
            pergunta = ouvir(ouvido)
            if not pergunta:
                print("🤖 Jarvis: Não ouvi nada, pode repetir?")
                continue
            print(f"🎙️ Você disse: {pergunta}")

        # .strip(".!? ") tira o ponto que o ouvido coloca no fim da frase
        if pergunta.lower().strip(".!? ") in ("sair", "exit", "tchau"):
            print("🤖 Jarvis: Até mais, José!")
            break

        # Pedido de AÇÃO? Vai direto aos braços Java, sem passar pelo Gemma.
        # (O resultado também não entra no histórico: texto das notas não vira "ordem" para a IA.)
        intencao = detectar(pergunta)
        if intencao:
            # Ação que ESCREVE e veio da voz: a TV ou um erro do ouvido não grava nada sem o "sim"
            if veio_da_voz and intencao[0] == "daily":
                confirma = input(f"📝 Anotar no daily: \"{seguro(intencao[1])}\"? (s/n) ").strip().lower()
                if confirma not in ("s", "sim"):
                    print("🦾 Jarvis: Ok, não anotei.")
                    continue
            resposta = executar_acao(bracos, *intencao)
            print(f"🦾 Jarvis: {resposta}")
            falar(boca, resposta)
            continue

        historico.append({"role": "user", "content": pergunta})

        print("🤖 Jarvis: ", end="", flush=True)
        resposta = ""

        # stream=True → a resposta aparece aos poucos, palavra por palavra
        for pedaco in ollama.chat(model=MODELO, messages=historico, stream=True):
            texto = pedaco["message"]["content"]
            print(texto, end="", flush=True)
            resposta += texto

        print()
        historico.append({"role": "assistant", "content": resposta})
        falar(boca, resposta.replace("*", ""))  # fala a resposta 🔊 (sem asteriscos)


if __name__ == "__main__":
    main()
