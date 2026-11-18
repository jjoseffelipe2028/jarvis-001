# Plano — contar o golpe no segundo 0 (som.py)

_Escrito pela 001 em 15/11/2026 · 1º teste real do `/dupla` (Ticket 070)._

## Objetivo
O `som.py` (skill `raio-x-video`) não conta um golpe de som que acontece logo no começo do vídeo: no teste de 120 BPM ele achou 7 golpes, mas eram 8 (faltou o de 0,0 s). Vídeo que começa com batida ou impacto perde o golpe mais importante: o do gancho.

## Causa
Em `achar_golpes`, a subida da 1ª janela é fixa em `0.0` (não há janela anterior) e o laço começa em `i = 1`.

## Mudança (2 arquivos + 1 teste novo)
- `.claude/skills/raio-x-video/scripts/som.py` → `achar_golpes`:
  1. Primeira linha: `if len(db) < 3: return [], []` (evita `IndexError` com áudio curtíssimo).
  2. **Uma regra só** para a subida: `antes = -60.0 if i == 0 else max(db[i - 1], -60.0)`; `subida[i] = max(0, min(db[i], 0) - antes)`. "Antes do vídeo" conta como silêncio.
  3. Laço de `i = 0` até o fim, comparando só com os vizinhos que existem: `(i == 0 or subida[i] >= subida[i - 1]) and (i == n - 1 or subida[i] >= subida[i + 1])`. Assim o `subida[-1]` do Python (último item) nunca entra por engano.
- `.claude/skills/raio-x-video/SKILL.md`: tirar a linha "Golpe no segundo 0 não é contado".
- **Novo:** `.claude/skills/raio-x-video/tests/test_som.py` (pytest), chamando `achar_golpes` direto com 3 casos: batida em 0,0 s, começo em silêncio (sem golpe falso) e lista curta.

## Riscos
- Vídeo que já começa com som contínuo alto (música desde o 1º quadro) ganharia um "golpe" falso em 0,0 s. Aceitável: o começo do vídeo É um evento para o gancho. Fica documentado.
- A curva `subida` também alimenta o BPM: 1 valor a mais no começo quase não muda a autocorrelação.

## Testes
- `pytest .claude/skills/raio-x-video/tests` (os 3 casos acima).
- Vídeo sintético de 120 BPM (batidas em 0,0 · 0,5 · … · 2,5 · 4,0 · 4,5): esperar **8** golpes (antes: 7), BPM 120 e silêncio igual (2,60 s por 1,40 s).
- Vídeo sem áudio continua dizendo "sem áudio".
- Demo do Capi: golpes mudam no máximo +1.
