# Ticket 090 — Volume padrão nos cortes (-14 LUFS)

**Status:** ✅ feito (17/11/2026) · **Origem:** pesquisa "Estratégia do Brabo no GTA 6" (melhoria nº 8 dos cortes)

## Problema
O corte copiava o volume da live como veio: trecho baixo ficava quase mudo no celular e grito estourava.
O `brabo.py` já usava -14 LUFS, mas o corte cru (`cortador.py`) não.

## O que mudou
- `cortador.VOLUME = "loudnorm=I=-14:TP=-1.5:LRA=11"`, o mesmo alvo do `brabo.py`.
- `comando_corte` aplica `-af VOLUME` e volta o áudio para **48 kHz** (o `loudnorm` sobe a taxa por dentro).
- Vale para todo corte: Short, versão TikTok (Ticket 089) e o que passa pelo pacote. O gancho copia o som já ajustado.

## Testes
- `tests/test_cortador.py::test_volume_padrao_dos_shorts`: filtro, alvo, pico e 48 kHz no comando.
- `python cortes.py autoteste`: 7 de 7 com o FFmpeg de verdade (corte 1080x1920, áudio de 25 s, legenda, original intacto).

## Custo (⚠️)
É o `loudnorm` de **1 passada** (mais rápido). A de 2 passadas acerta o alvo com mais precisão, mas lê o trecho duas vezes.
Para corte de 25 a 70 s, 1 passada basta. O -14 é o alvo do YouTube; no TikTok é estimativa (fonte: blog).
