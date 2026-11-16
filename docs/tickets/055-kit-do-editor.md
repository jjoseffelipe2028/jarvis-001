# 055 — Kit do Editor: o robô equipado com os truques de edição
**Status:** Parte 1 ✅ construída e testada (15/11) · pedido do José 15/11 ("você é o robô e eu te equipo") · código pela 001
· commit `a5ccf93` + push (OK do José) · Parte 2: retratos 3D + boca por IA ✅ (16/11) · Capi no painel + gancho ⏳

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

## Parte 2 — retratos 3D e boca por IA (15–16/11)
- **Capi** (`desenhar_capi`) entra como 3º personagem no `avatar.py`, sem tirar o Brabo.
- **`retratos.py`** (novo): lê as 3 imagens 3D do Gemini (`fechada`, `meia`, `aberta`) da pasta
  `Videos/personagens/<nome>/` (fora do git), tira o fundo verde, alinha as bocas e reduz. Sem as imagens, volta o desenho.
- **Movimento** (`avatar.movimento`): boca suave, respiração, cabeça inclinando e "tranco" de zoom nos picos da voz;
  moldura arredondada atrás do personagem.
- **`boca_ia.py`** (novo): a MuseTalk v1.5 (código MIT, modelo liberado para uso comercial, instalada à parte em
  `ferramentas/musetalk`) redesenha a boca pela voz. Se faltar ou falhar, sai a boca antiga. `JARVIS_BOCA_IA=0` desliga.
- **Java:** tempo limite do `fazerVideo` 20 → 35 min (15 s de Short ≈ 8 min com a IA).

### Evidências (Parte 2)
- `test_retratos` (6) e `test_boca_ia` (6); suíte Python: 344 OK; Java Brabo: 13 OK.
- Amostra: `Videos/cortes/amostra-boca-ia-brabo.mp4` (15 s, Brabo 3D com boca por IA). José: "Ficou melhor".
