# Ticket 093 — Checagem dos 7 segundos (violência no começo do corte)

**Status:** ✅ feito (17/11/2026) · **Origem:** pesquisa "Estratégia do Brabo no GTA 6" (melhoria nº 4 dos cortes)

## Problema
Pela regra de jogos do YouTube (Dexerto, 04/09/2026), **violência gráfica nos 7 s iniciais ou na thumb = vídeo sem anúncio**.
O corte começa 10 s antes do pico, mas nada conferia o que tinha no começo.

## O que mudou
O pacote **avisa** (não bloqueia) com dois sinais que já temos, sem modelo de visão:
- **Fala:** `pacote.falas_violentas(srt)` acha "tiro", "atirou", "matei", "sangue", "atropelei", "explodiu", "faca"...
  nas falas que **começam** antes dos 7 s.
- **Áudio:** `cacador.estouros(corte)` lê só os 7 s iniciais em blocos de 50 ms e marca **estouro seco**
  (pulo de 10 dB em 50 ms e 12 dB acima do normal), o jeito do tiro. Grito e música sobem devagar.
- `cacador.energia_por_segundo` ganhou `bloco` e `limite` (antes era só 1 s e o arquivo todo; o padrão não mudou).
- Vale para o Short e para a versão TikTok (alerta com "TikTok:").
- Painel: linha `🩸 Nos 7 s iniciais: ...` e uma caixinha nova em todo corte:
  `- [ ] Sem tiro/sangue nos 7 s iniciais e na thumb (senão: sem anúncio)`.
- Falha ao ler o áudio não derruba o pacote: fica sem o alerta de áudio.

## Testes
- `tests/test_pacote.py::Checagem7sTest` (4): fala só nos 7 s, alerta no item e no painel, estouro e falha, TikTok.
- `tests/test_cacador.py::TestEstouros` (2): áudio de verdade gerado no teste (estalo aos 2 s acha, aos 8,5 s ignora).
- Suíte: 367 OK.

## Limites (⚠️)
- **Não vê a imagem.** Sangue sem som e sem fala passa. Por isso a caixinha manual continua.
- Batida de carro e explosão também dão estouro seco: vai ter alarme falso. É aviso, o José decide.
- "bala" e "mata" também são doce e floresta. Mesmo motivo: só aviso.
- Não testado ainda com corte de live de verdade.
