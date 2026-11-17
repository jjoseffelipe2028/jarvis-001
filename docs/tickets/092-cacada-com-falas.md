# Ticket 092 — Caçada com falas (palavras fortes e risada)

**Status:** ✅ feito (17/11/2026) · **Origem:** pesquisa "Estratégia do Brabo no GTA 6" (melhoria nº 2 dos cortes)

## Problema
A caçada só olhava o **volume**. Tiroteio e buzina ganhavam de um "NÃO ACREDITO, CLIPA ISSO" falado mais baixo.

## O que mudou
- `python cortes.py cacar live.mp4 --falas`:
  1. O volume acha **60 candidatos** (3x os 20 de sempre).
  2. O Whisper ouve **só esses trechos de 25 s**, nunca a live inteira.
  3. Cada palavra forte diferente vale **+3 dB** (teto de +9): "não acredito", "olha isso", "clipa",
     "que isso", "caraca", "meu deus", "mano do céu", "socorro", e risada escrita ("kkk", "hahaha", "rsrs", "risos").
  4. Reordena por volume + fala e fica com os 20 melhores.
- `cacador.nota_fala` e `cacador.reordenar`; `Momento` ganhou `bonus` e `palavras` (o volume original fica guardado).
- `Cortador.ouvir(...)`: tira só o áudio do trecho e devolve a fala (não gera vídeo).
- Trecho que falha ao ouvir **não derruba** a caçada: fica só com o volume, com aviso.
- Nota do cofre mostra o porquê: `+8.0 dB · 🗣️ não acredito, kkkk`.
- Sem `--falas`, tudo igual a antes. O `pacote` usa a caçada salva: rode `cacar --falas` antes dele.

## Testes
- `tests/test_cacador.py::TestFalas` (4): pontos da fala, reordena e renumera, falha não derruba, nota.
- `tests/test_cortador.py::test_ouvir_so_tira_audio_e_junta_a_fala`.
- Suíte: 361 OK · `autoteste`: 7 de 7.

## Custo e limites (⚠️)
- Whisper no CPU: ~60 trechos de 25 s levam alguns minutos a mais na caçada.
- Risada só conta se o Whisper **escrever** ("kkkk"); com o filtro de silêncio, às vezes ele pula.
  Detector de risada de verdade (YAMNet) ficou de fora: precisa do TensorFlow (pesado). Fica para depois.
- Não testado ainda com uma live de verdade.
