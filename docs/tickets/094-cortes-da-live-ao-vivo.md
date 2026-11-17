# Ticket 094 — Cortes da live ao vivo (captura em blocos)

**Status:** ✅ feito (17/11/2026), sem commit · **Origem:** pipeline "live em tempo real"

## Problema
O Estúdio de cortes só trabalhava **depois** que a live acabava. Agora os cortes saem **durante** a live.

## Como usar
- `python ao_vivo.py <link-da-live> --arroba @amigo [--falas]` → grava e corta ao vivo (Ctrl+C para parar).
- `python ao_vivo.py --pasta <pasta-com-blocos> --arroba @amigo` → modo teste com blocos já gravados.
- Cortes em `JARVIS_CORTES_SAIDA/ao-vivo/`; resumo `...-ao-vivo.md` no cofre. **Nada é postado.**

## Como funciona (tudo local, sem conector)
1. `streamlink --stdout <url> best | ffmpeg -c copy -f segment -segment_time 60` → `bloco_0000.ts`, `bloco_0001.ts`...
   Os blocos ficam numa pasta nova dentro de `JARVIS_CORTES_ENTRADA` (`ao-vivo-<data>`). Erros do gravador vão para `gravador.log`.
2. `Vigia` só mexe no bloco **fechado** (o N está pronto quando aparece o N+1).
3. Janela de 2 blocos (anterior + novo), colada sem reprocessar (`ffmpeg -f concat -c copy`).
4. `cacador` acha os picos na janela (com `--falas`, o Whisper reordena). No máximo 2 cortes por minuto novo.
5. Pico colado no fim da janela espera a próxima (pega o momento inteiro). Pico a menos de 45 s de um já cortado é ignorado.
6. `Cortador` faz o 9:16 com legenda e -14 LUFS (o de sempre). A janela-rascunho é apagada.
7. Só os 30 blocos mais novos ficam no disco (~30 min).
- Link aceito: só `https` de YouTube, Twitch ou Kick (`validar_url`).

## Instalado (com OK do José)
- `streamlink==8.6.3` (BSD-2) e dependências, no `requirements.txt`.

## Testes
- `tests/test_ao_vivo.py` (17): link, blocos fechados, ordem, limpeza, janela, pico repetido, pico no fim, máximo, erro não derruba, horário absoluto, nota.
- Suíte: 384 OK.
- Ensaio real 1: "live falsa" de 3 min com grito em 1:40 → 1 corte em **1:42**, 1080x1920, 25 s.
- Ensaio real 2: live pública de verdade no YouTube por ~2 min → 3 blocos gravados e juntados sem erro (música, sem pico: 0 cortes, certo).

## Limites (⚠️)
- Atraso: o corte sai ~1 a 3 min depois do momento.
- Ao parar com Ctrl+C, o último bloco fica pela metade: vira aviso ("áudio corrompido"), não derruba.
- Com `--falas`, o Whisper no CPU pode atrasar mais numa live agitada.
- Ainda não testado numa live de GTA do amigo.
