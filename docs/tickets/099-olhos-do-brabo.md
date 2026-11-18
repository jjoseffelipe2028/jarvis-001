# Ticket 099 — Os olhos do Brabo 👀

**Status:** ✅ feito (18/11/2026) · **Origem:** pedido do José ("eu queria que ele comentasse qualquer coisa… entendesse só pelo vídeo")

## Problema
Corte só de gameplay (tiroteio, Minecraft, sem ninguém falando) → a legenda `.srt` sai vazia.
Sem fala, os textos (`textos.py`) e o rascunho do Brabo (`brabo.py rascunho`) não tinham o que usar e davam erro.

## O que mudou
- **`python/olhos.py` (novo):** tira 1 foto a cada 2 s (no máximo 16) com o FFmpeg e manda cada uma para a Gemma local (visão).
  Para cada foto, a Gemma devolve o que acontece e uma **nota de momento épico** (0 a 10).
  O **momento épico** é a cena mais forte, e a nota dos vizinhos desempata (uma sequência forte vale mais que um pico isolado).
  Salva `<corte>.cenas.json` (para o código) e `<corte>.cenas.txt` (para gente e para a dupla).
  Olha uma vez só; depois só lê.
- **`textos.sugerir(..., cenas=)`:** corte sem fala usa as cenas, e o momento épico vira o centro do título e do gancho.
- **`brabo.rascunho(..., cenas=)`:** a fala mais forte do Brabo cai no segundo do momento épico.
- Isso já está ligado no `pacote.py`, em `cortes.py textos`, em `brabo.py rascunho` e no `estudio_mcp` (o estúdio só usa cenas já olhadas, porque olhar demora).

## Travas 🔒
- Tudo local: só Ollama em 127.0.0.1. As fotos ficam numa pasta temporária e somem depois.
- Texto que aparece na imagem é DADO, nunca ordem.
- A resposta da Gemma é conferida:
  - JSON;
  - até 90 letras;
  - sem link, @ ou palavrão;
  - nota de 0 a 10;
  - desarmada para a legenda.
- FFmpeg em lista (`shell=False`, `-n`). Os arquivos de cenas são criados com `open("x")`, nunca sobrescritos.
- O corte é passado só pelo NOME, dentro da pasta dos cortes (`resolver_entrada`).

## Custo
Uns 15 s por foto na RTX 4050, ou seja, ~3 min por corte de 25 s. Rode à noite ou antes da dupla.

## Testes
- `tests/test_olhos.py` usa uma Gemma falsa: validar, pular foto torta, momento épico, cache, textos e rascunho com cenas.
- Python: 433 testes OK.

## Falta (⚠️)
- A dupla em Java (`CortesService`, "escolha da dupla") ainda lê só o `.srt`. Ela deve ler `<corte>.cenas.txt` quando a legenda estiver vazia.
  Não mexi porque outra sessão (Ticket 098) estava editando esse arquivo ao mesmo tempo.
- A Gemma pequena (e2b) às vezes descreve genérico ("correndo em campo aberto") e nem sempre reconhece o jogo.
