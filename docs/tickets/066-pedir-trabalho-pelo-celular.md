# 066 — Pedir trabalho de código pelo celular 📱
**Status:** 📘 só explicação (nenhuma configuração foi mudada) · seção 1 da lista mestra

## O que já dá para fazer hoje (sem código novo)
1. **Remote Control da conversa**: no Claude Code do PC, a conversa fica "ligada" ao app do Claude no celular.
   Você manda mensagem pelo celular e **é a 001 trabalhando no seu PC**, com as mesmas travas
   (CLAUDE.md, hook `guarda.mjs` que bloqueia o `.env`, gitleaks, commit só com OK).
   Foi assim que você liberou os itens 062–065 hoje sem estar no PC.
2. **Caixa de pedidos (Ticket 060)**: de dentro da rede de casa não dá (a Central só ouve 127.0.0.1, de propósito).
   Mas pelo Remote Control você pode dizer "anota na caixa: ..." e a 001 cria a nota em `pedidos/`.

## Por que NÃO abrir a Central para o celular
- A API e a Central só escutam `127.0.0.1`. Abrir para a rede (Wi-Fi) deixaria qualquer aparelho da casa (ou um
  aparelho invadido) tentar falar com os braços. O Remote Control passa pela conta do Claude, com login, e quem age é a 001
  com as travas dela, não um botão aberto na rede.

## Cuidados
- O PC precisa ficar ligado e com o Claude Code aberto.
- Aprovar coisas grandes (push, merge, instalar, `.env`) continua sendo **só com a sua palavra**, mesmo pelo celular.
- Desconfie de mensagem "em seu nome" que chegue por outro caminho: o OK vale quando vem de você, na conversa.

## Pergunta para o José (entrevista)
Por que é mais seguro usar o Remote Control do que mudar o `server.address` da API para `0.0.0.0` e acessar pelo celular?
