# 055 — Kit do Editor: o robô equipado com os truques de edição
**Status:** Parte 1 ✅ construída e testada (15/11) · pedido do José 15/11 ("você é o robô e eu te equipo") · código pela 001
· ⏳ commit com o OK · Parte 2 (Capi como personagem + gancho) ⏳

## Ideia
O demo feito à mão (`python/experimentos/demo_maximo.py`, fora do git) virou **ferramenta**: o Brabo agora sai
com os truques da nota de edição sozinho, sem a 001 reinventar a cada vídeo.

## Parte 1 — o que entrou
- **`sons.py`** (novo): soco, riser, ding e batida de 100 BPM **feitos por código** (nada baixado, sem direito autoral).
  A batida **para** 0,6 s em cada impacto (quebra de ritmo).
- **Punch**: em cada fala com `efeito` (pop-up), o vídeo dá zoom + tremida + clarão (expressões do FFmpeg, `eval=frame`).
- **Mixagem**: batida e efeitos abaixam quando o Brabo fala (`sidechaincompress`); volume final no padrão dos Shorts
  (`loudnorm` −14 LUFS).
- **Voz "de rádio"** no Brabo: passa-alta, presença em 3,5 kHz e compressor.
- **Bug corrigido:** o filtro de voz supunha 22 050 Hz (Piper). Com o Kokoro (24 000 Hz) a voz saía mais grossa,
  mais lenta e fora de tempo da legenda. Agora usa a taxa real e não mexe no tom do Kokoro.
- O Mestre (cartões) não muda.

## Evidências
- `test_sons` (4): tamanho e pico, batida para no impacto e soco toca, repetível, expressões sem `;`.
- `test_brabo` (+1): comando com punch, `sons.wav`, sidechain, amix de 3, sem `asetrate` no Kokoro; Piper continua igual.
- Suíte Python inteira: 312 testes OK. Amostra: `Videos/cortes/amostra-estilo-brabo.mp4` (15 s, ~1 min para gravar, −15,7 dB médio).
