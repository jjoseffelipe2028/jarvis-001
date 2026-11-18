# Ticket 101 — Brabo sem fundo preto, maior, e corte mais longo

**Status:** ✅ feito (18/11/2026) · **Origem:** pedido do José depois de ver o corte de Fortnite com o Brabo

## O que mudou
- `brabo.py` (`MODOS["gta"]`): `fundo_avatar` False — sai a moldura escura com aro laranja; o retrato já vem recortado
  (fundo transparente), então o Brabo fica "solto" em cima da gameplay.
- Brabo maior: `lado` 360 → 480 (~33% maior), posição `x=10, y=200` (canto de cima, abaixo da bússola do jogo).
- `cacador.py`: corte sugerido 25 s → **35 s** (`ANTES, DEPOIS = 12, 23`). A versão TikTok (70 s) não mudou.
  O roteiro do Brabo se ajusta sozinho: o prompt conta as fotos (1 a cada 2 s) e calcula quantas palavras cabem.

## Testes
- `test_pacote`: pico estimado agora é início + 12. Python: 433 testes OK.
- Prévia montada com um quadro real do corte 06 (Brabo recortado, 480 px).

## Falta (⚠️)
- Ver um vídeo inteiro com o Brabo novo (o corte 07 em andamento ainda sai no jeito antigo).
- Cortes mais longos = Brabo leva mais tempo para gerar cada vídeo.
