# 050 — Estúdio do Brabo: corte vira Short com apresentador IA (rosto, voz e legenda)
**Status:** ✅ construído e testado com vídeos de ensaio (14/11) · pedido do José 14/11 · código pela 001 · ⏳ commit com o OK

## Ideia
Um corte 9:16 (do Ticket 048) vira um Short com o **apresentador IA original** comentando por cima:
`roteiro JSON (001/002 ou rascunho da Gemma) → voz Piper → rosto animado pela voz → legenda grande → Short`.

Dois canais **separados**:
- **gta** → "o Brabo": facecam no canto, debochado, áudio do jogo a 20 % (rádio do GTA = Content ID), `NOTA x/10` no fim.
- **ensino** → "Mestre Parafuso" (marido de aluguel): meio corpo com o braço, passos grandes na tela.
  `"fundo": "cartoes"` cria a tela desenhada (ex.: tomada) quando ainda não há vídeo próprio.

## Como usar
```
cd python
.venv/Scripts/python brabo.py rascunho <corte.mp4>       # Gemma escreve <corte>.brabo.json (revisar!)
.venv/Scripts/python brabo.py fazer <roteiro.json>        # → <base>-brabo.mp4 / -mestre.mp4 + .post.txt
.venv/Scripts/python brabo.py amostra brabo               # PNG com as 3 bocas
```
Skill: `.claude/skills/brabo-comentarista`.

## Segurança
- Nada novo instalado: numpy + Piper + FFmpeg que já existiam. Rosto **desenhado por código** (arte original, sem modelo de IA de rosto).
- Corte e roteiro: só o **nome**, dentro da pasta dos cortes (sem caminho, sem atalho).
- Roteiro conferido: 1–14 falas, ≤ 160 letras, sem link, sem palavrão pesado, tempos em ordem, nota 0–10, `@` válido.
- Texto da legenda desarmado (`{}` e `\` viram texto). FFmpeg em lista, sem shell, com `-n` (nunca sobrescreve).
- A fala do jogador vai para a Gemma como **dado**, entre marcas.
- **Nada é postado.** O `.post.txt` traz o checklist: rótulo "conteúdo sintético = SIM" e autorização do dono.

## Evidências
- `tests/test_brabo.py`: **18 testes OK** (roteiro, tempos, legenda, comando FFmpeg, post, boca pela voz, rascunho da Gemma falsa). A suíte Python inteira também passa.
- Ensaio GTA: `2026-11-14-034152-corte-01-brabo.mp4` (25 s, 1080x1920, 27 s para gerar).
- Ensaio ensino: `tomada-ensaio-mestre.mp4` (40 s, "como trocar uma tomada", 6 passos).

## Limites e próximos passos
- A boca abre e fecha pelo volume, sem formar as letras. Próximo: Rhubarb Lip Sync (modo `phonetic`), com OK para o download.
- A voz `pt_BR-faber` é a do Jarvis e a licença comercial não está clara. Para publicar: Kokoro `pm_alex` (Apache), com OK.
- O vídeo de ensaio do GTA é um padrão de teste: falta testar com a gameplay real do amigo (com autorização).
