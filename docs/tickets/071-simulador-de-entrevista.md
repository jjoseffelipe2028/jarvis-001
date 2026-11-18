# Ticket 071 — Simulador de entrevista por voz ⭐⭐

_Item 33 da lista mestra. Plano da 001 em 15/11/2026 (José liberou mudanças sem OK enquanto está fora; o commit fica local)._

## Ideia
O Jarvis faz uma **entrevista de emprego de treino**: pergunta em voz alta, o José responde falando (ou digitando), e o Gemma local dá **nota, ponto forte, o que faltou e uma resposta modelo curta**. No fim, um relatório vai para o cofre, para o José ver onde precisa estudar.

## Como funciona
- `python entrevista.py` (temas: `java`, `cyber`, `jarvis`, `pessoal` ou `misto`; padrão: 5 perguntas, misto). `--sem-voz` usa só o teclado.
- **Perguntas fixas** num banco nosso (`entrevista_perguntas.py`), cada uma com os **pontos-chave** que uma boa resposta precisa ter. As do tema `jarvis` vêm dos ADRs: é o "por que você fez assim?" da entrevista.
- **Nota em duas partes:**
  1. **Pontos-chave** (regra fixa, sem IA): quantos pontos a resposta citou. Não dá para enganar com texto bonito.
  2. **Retorno do Gemma**: ponto forte, o que faltou e a resposta modelo (em JSON, lido com cuidado).
  A nota final é a média das duas (0 a 10).
- **Relatório:** `AAAA-MM-DD-HHMM-entrevista.md` na pasta `JARVIS_PASTA_ENTREVISTAS` do `.env` (ex.: `knowledge/entrevistas` do cofre). Sem a pasta configurada, só mostra na tela e avisa (falha fechada, igual aos outros braços).

## Segurança 🔒
- A resposta do José vai para o Gemma **como dado**, dentro de um pedido fixo. O Gemma só devolve texto, e **nada vira ação** (o simulador não chama a API Java nem roda comando).
- Se o Gemma devolver lixo ou um JSON quebrado, vale só a nota dos pontos-chave (não quebra).
- O nome do arquivo é gerado pelo programa (data e hora), nunca vem de fora. A pasta vem do `.env`, que a IA não lê: quem lê é o programa, com a mesma função `ler_pasta` dos outros braços.
- Tudo local: Ollama + Piper + faster-whisper, já instalados. Nada novo para baixar.

## Testes (unittest, sem Ollama e sem microfone)
- Banco: todas as perguntas têm tema válido e pelo menos 2 pontos-chave.
- Sorteio: respeita o tema e o número de perguntas e não repete pergunta.
- Pontos-chave: ignora acento e maiúscula, e aceita sinônimos.
- Leitura do retorno do Gemma: JSON certo, JSON dentro de texto, lixo e nota fora de 0–10.
- Relatório: nome gerado pelo programa; sem pasta, não grava.

## Pronto quando
O José roda `python entrevista.py --tema jarvis`, responde 5 perguntas e recebe nota, retorno e o relatório no cofre.
