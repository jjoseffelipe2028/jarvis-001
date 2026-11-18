# Ticket 097 — Brabo comentando nos cortes + erro claro no estúdio

**Status:** ✅ feito (17/11/2026) · **Origem:** pedido do José (print do Estúdio de cortes com "Texto inválido")

## Problema
1. O José colou o link de um **canal** (`youtube.com/@...`) e a tela só disse "Texto inválido (vazio ou grande demais)".
   O servidor sabia o motivo certo, mas o `ErrosHandler` trocava todo erro por "entrada invalida".
2. Para o Brabo comentar os cortes, era preciso ir em outro painel e escolher corte por corte.

## O que mudou
- **Erro claro:** `EntradaInvalidaException` agora volta `{"erro": "entrada invalida", "motivo": "<frase fixa>"}`.
  Seguro: essas frases são escritas no código e nunca repetem o que o usuário digitou.
  Os outros 400 (validação do Spring, JSON quebrado) continuam genéricos.
- `central.js`: no 400, mostra o `motivo` quando vier.
- `paginas.js`: link de canal é barrado **antes** de enviar, com a explicação de qual link usar.
- **Apresentador no estúdio:** campo novo "sem apresentador / 🎙️ + Brabo comentando / 🦫 + Capi (em breve)".
  - `CortesService.iniciar(..., "brabo")`: quando os cortes ficam prontos, o Brabo comenta os cortes **novos**
    (os que não estavam na pasta antes), do mais forte para o mais fraco, até 8. Gancho e versão TikTok ficam de fora.
  - Usa o mesmo fluxo do Estúdio do apresentador (`BraboService`): roteiro da dupla, voz, vídeo.
  - Brabo ocupado ou com erro → os cortes **continuam prontos**, só avisa.
  - "capi" é recusado com motivo: a Capi ainda não tem voz nem modo no `brabo.py`.
  - Lista fechada; o log só grava `nenhum`, `brabo` ou `invalido` (nunca o texto que veio).

## Testes
- `CortesServiceTest` (+3): Brabo comenta só os novos em ordem, sem gancho/TikTok; sem apresentador ninguém comenta
  e Brabo ocupado não derruba os cortes; apresentador ruim (capi, mestre, sem Brabo ligado) dá 400 antes de rodar.
- `CentralCortesControllerTest` (+1): o 400 leva o `motivo`.
- Suíte Java: 514 testes, 0 falhas.

## Falta (⚠️)
- Não testado na tela de verdade (precisa abrir a Central pelo ícone para ter o crachá).
- Capi comentando: dar voz e jeito próprios (modo novo no `brabo.py`).
