# Ticket 098 — o Brabo comenta qualquer jogo

**Status:** ✅ feito (18/11/2026) · **Origem:** pedido do José (os cortes de 18/11 eram de Minecraft e o Brabo foi pensado só para GTA)

## Problema
Os textos do Brabo diziam "comentarista de GTA" e "canal de GTA". Num corte de Minecraft o roteiro saiu falando de GTA,
e a revisão da 002 apontou o conflito.

## O que mudou
- `BraboService.promptRoteiro`: o Brabo comenta **qualquer jogo**. Antes de escrever, descobre pelas fotos qual é o jogo
  e usa o jeito e as palavras dele; sem certeza, diz "esse jogo" e nunca inventa nome.
  "Spoiler do GTA 6" virou "spoiler de história de jogo nenhum".
- `promptRevisao`: a 002 também confere se o jogo citado é mesmo o das fotos.
- `textoDoPedido` e a revisão: "canal de GTA" → "canal de games".
- `CortesService.TEMA_BRABO`: sem "GTA".
- `brabo.py`: instruções da Gemma (`INSTRUCOES_GTA`) e texto do post falam de games.
  O modo interno continua se chamando `gta` (voz, facecam, legenda), só o nome ficou.

## Testes
- `BraboServiceTest.brabo_comentaQualquerJogo` e `test_caderno.test_brabo_comenta_qualquer_jogo`.
- Java: 515 testes, 0 falhas. Python: OK.

## Falta (⚠️)
- O pacote dos cortes (`textos.py`) ainda põe `#gta6` fixo em todo corte: num corte de Minecraft isso engana.
- Não testado com um corte de verdade.
