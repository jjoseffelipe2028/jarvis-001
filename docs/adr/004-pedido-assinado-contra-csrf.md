# ADR 004 — Pedido assinado nas ações que escrevem (contra CSRF)

- **Status:** Aceita
- **Data:** 2026-11-09
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
Mesmo só em `127.0.0.1`, uma página maliciosa aberta no navegador do José pode tentar chamar a API (CSRF: o navegador manda o pedido "em nome" do José).

## Decisão
Ação que escreve exige **pedido assinado** (HMAC-SHA256 com hora ±60 s e nonce de uso único) e, pela Central, **crachá + `Origin` + Confirmar**. O token fica no `.env`, nunca no código, e não viaja no pedido assinado. Falha **fechada**: sem token configurado, recusa tudo.

## Alternativas consideradas
- **Só checar o `Origin`**: dá para contornar em alguns cenários.
- **Token puro no cabeçalho**: se vazar num log, vale para sempre (replay).

## Consequências
- ✅ Replay e página impostora são recusados.
- ⚠️ O atalho `X-Jarvis-Token` (para `curl`) ainda existe e cada uso vai para o log (ver `docs/seguranca-owasp.md`).

## Como verificar
Tickets 006, 007 e 020; `TokenInterceptor` e seus testes.
