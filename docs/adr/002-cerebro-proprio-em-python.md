# ADR 002 — Cérebro próprio em Python + Gemma local

- **Status:** Aceita
- **Data:** 2026-10-30
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
Precisávamos de um "cérebro" que conversa e entende pedidos. O projeto pronto OpenJarvis só rodava no WSL2, estava em alpha e pedia um modelo grande demais para a placa.

## Decisão
O cérebro é um programa **nosso** em Python (`python/chat.py`) usando a biblioteca **oficial** do Ollama com o Gemma local. Voz com faster-whisper (ouvido) e Piper/Kokoro (boca).

## Alternativas consideradas
- **OpenJarvis**: sem Windows nativo, alpha, sem voz documentada.
- **Assistente pronto em nuvem**: dados saindo do PC (ver ADR 001).

## Consequências
- ✅ Roda no Windows e o José entende cada linha (conta no portfólio).
- ⚠️ Mais trabalho para manter do que um pronto.

## Como verificar
`python/chat.py` e os testes em `python/tests/`.
