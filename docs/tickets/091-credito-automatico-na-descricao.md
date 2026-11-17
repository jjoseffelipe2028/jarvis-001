# Ticket 091 — Crédito automático na descrição

**Status:** ✅ feito (17/11/2026) · **Origem:** pesquisa "Estratégia do Brabo no GTA 6" (melhoria nº 10 dos cortes)

## Problema
A descrição saía com "🎮 Live de @amigo": não dizia que o uso é **autorizado** nem que o canal usa IA.
Os títulos podiam ter 60 letras (cortam no celular) e vinham 5 hashtags genéricas.

## O que mudou (`textos.py`)
- `credito(arroba)`: rodapé fixo, posto pelo **código** (a IA não escreve nem troca):
  - `🎮 Gameplay: @amigo (autorizado)`
  - `🤖 IA com roteiro humano`
- Títulos com no máximo **50 letras** (`LIMITE_TITULO`). Passou → a Gemma tenta de novo 1 vez.
- Hashtags: **#gta6 sempre a 1ª** (posta pelo código) + até **2 de nicho** da Gemma = 2 a 3 no total.
  - `#GTA6` da Gemma não repete (compara sem maiúscula). Sem nenhuma de nicho válida → erro.
- Vale para o comando `textos` e para o pacote do dia da live (os dois usam `validar`).

## Testes
- `tests/test_textos.py::test_credito_e_hashtags_do_ticket_091`: rodapé, #gta6 sem repetir, só a fixa barra,
  título de 51 letras barrado e de 50 passa.
- Testes antigos ajustados para o novo rodapé. Suíte: 356 OK.

## Custo (⚠️)
Limite de 50 letras é mais apertado: a Gemma pode errar mais vezes e pedir a 2ª tentativa.
Não testado ainda com a Gemma de verdade.
