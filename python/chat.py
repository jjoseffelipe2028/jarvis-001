"""
Jarvis 001 — Cérebro (versão 1: chat por texto)

Conversa com o Gemma, que roda no PC pelo Ollama (offline).
Só conversa: não executa nada no computador. 🔒
"""

import ollama  # biblioteca oficial do Ollama

MODELO = "gemma4:e2b"  # o modelo que baixamos (cabe na RTX 4050)

# A "personalidade" do Jarvis (inspirada na soul.md)
PERSONALIDADE = (
    "Você é o Jarvis, assistente pessoal do José. "
    "Responda sempre em português do Brasil, de forma curta e direta. "
    "José estuda Java back-end e cibersegurança. "
    "Se não souber algo, diga que não sabe."
)


def main():
    # A conversa começa com a personalidade (mensagem de "sistema")
    historico = [{"role": "system", "content": PERSONALIDADE}]

    print("🤖 Jarvis: Olá, José! Digite sua mensagem (ou 'sair' para encerrar).")

    while True:
        pergunta = input("\nVocê: ").strip()

        if pergunta.lower() in ("sair", "exit", "tchau"):
            print("🤖 Jarvis: Até mais, José!")
            break

        if not pergunta:  # ignora mensagem vazia
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


if __name__ == "__main__":
    main()
