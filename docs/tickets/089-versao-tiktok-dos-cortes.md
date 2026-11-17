# Ticket 089 — Versão TikTok dos cortes (mais de 1 min)

**Status:** ✅ feito (17/11/2026) · **Origem:** pesquisa "Estratégia do Brabo no GTA 6" (melhoria nº 1 dos cortes)

## Problema
O pacote do dia da live só gerava o Short de 25 s. O **TikTok Creator Rewards só paga vídeo com mais de 1 minuto**,
então nenhum corte rendia dinheiro no TikTok.

## O que mudou
- `cacador.janela_tiktok(pico, duracao)`: janela de **70 s** (45 s antes do pico, 25 s depois).
  - Pico perto do começo ou do fim do vídeo: a janela **escorrega** para o outro lado, em vez de encolher.
  - Vídeo que não dá **62 s** (margem sobre "mais de 1 min"): sem versão TikTok, com aviso.
- `pacote.montar(..., tiktok=True)`: cada momento ganha também `<corte>-tiktok.mp4` (com `.srt`).
  - Passa pelo **mesmo porteiro** (`cortador.liberar`): @ do amigo, limites, fim do vídeo.
  - **Nunca sobrescreve** (se o nome existir, avisa e pula).
  - Se a versão TikTok falhar, o **Short continua** (o aviso vai em `aviso_tiktok`, separado).
  - Caçada antiga sem `pico` no JSON: estima `pico = início + 10`.
- Painel no cofre: linha "📱 Versão TikTok (> 1 min, a que paga)" ou o motivo de não ter.
- CLI: `python cortes.py pacote live.mp4 --arroba @amigo --tiktok`. Sem a flag, tudo fica como antes.

## Testes
- `tests/test_cacador.py::TestJanelaTiktok` (5): meio, começo, fim, vídeo curto, bordas.
- `tests/test_pacote.py::TiktokTest` (5): gera as duas, sem flag não gera, falha não derruba o Short,
  vídeo curto avisa, caçada antiga sem pico.

## Custo (⚠️)
O Whisper roda de novo para a versão longa (mais tempo de processamento por momento). Aceito: é local e só roda com `--tiktok`.

## Próximas melhorias (mesma pesquisa)
Detector de pico com risada/palavras-chave · checagem de violência nos 7 s iniciais · -14 LUFS em todo corte ·
compilado semanal narrado · crédito automático na descrição · registro de origem dos cortes.
