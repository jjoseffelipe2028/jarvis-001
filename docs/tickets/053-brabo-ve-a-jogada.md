# 053 — O Brabo vê a jogada e comenta vários cortes
**Status:** Parte 1 ✅ construída e testada com falsos (15/11) · pedido do José 15/11 · código pela 001 · ✅ teste real 15/11 (3 cortes do Fortnite, ~8 min, sem fala: o Brabo narrou pelas fotos)
· Parte 2 (botão único ✂️ → 🎙️) ⏳ depois do OK da Parte 1

## Por quê
O primeiro link real (Fortnite do amigo, com autorização) veio **sem fala**: as legendas ficaram vazias e a dupla ficou
"cega". Gameplay sem fala é o melhor caso para o Brabo: ele vira a voz do vídeo — mas precisa **ver** a jogada.

## Parte 1 — como funciona
- Antes do roteiro, o Java tira **fotos do corte** (1 a cada 2 s, até 30, 480 px) com o ffmpeg, comando fixo:
  `ffmpeg -nostdin -v error -i <corte da LISTA> -vf fps=1/2,scale=480:-2 -frames:v 30 QUADROS-<carimbo>/q-%02d.jpg`.
- O `PEDIDO-…md` cita a pasta (`q-01.jpg = 0 s, q-02.jpg = 2 s…`) e o tamanho do corte. A 001 abre as fotos com `Read`
  e narra a jogada; a 002 confere se as falas batem com as fotos. Sem fotos, segue pela legenda e pelo tema.
- **Vários cortes** num pedido (até 8): no painel 🎙️, "Comentar N cortes" = o escolhido e os de baixo dele na lista.
  Um depois do outro; se um falhar, os outros seguem. A tela mostra ▶ 1, ▶ 2… para trocar de vídeo.
- Tema do Brabo agora é opcional ("comente a jogada").

## Segurança (igual ao 052)
- Cortes só da **lista** (nome exato, sem repetir, até 8); @ do dono obrigatório. Comandos fixos; o tema vai em arquivo.
- A dupla só **lê** (fotos, legenda, pedido) e devolve texto. **Nada é postado.**

## Evidências
- `BraboServiceTest` (+3): fotos citadas no PEDIDO e comando do ffmpeg fixo; lote de 2 cortes sem fotos; um corte falha
  e o outro sai; lote repetido/fora da lista/vazio recusado. `CentralBraboControllerTest`: 9 cortes → 400.
- ffmpeg real num corte do Fortnite: 13 fotos em 1,7 s.
- **Java: 447 testes, 0 falhas.**

## Estilo (15/11) — legenda karaokê + pop-ups
- **Legenda karaokê** (só no Brabo): letra Impact, a palavra falada acende em amarelo (`\k` do .ass), bloco entra com "pop".
- **Pop-ups** de lista FECHADA (`brabo.EFEITOS`): nota10, brabo, clutch, eliminou, vacilo, fail, quase, kkk — no máximo 4
  por vídeo; a 001 escolhe a chave (o texto e a cor são nossos). A NOTA final também entra com pop.
- **Roteiro recusado no 1º teste real** (3 de 3): as falas passavam do tempo do corte. O prompt agora dá a conta
  (~2 palavras/s, terminar 1,5 s antes do fim).
- Evidências: `test_brabo` 30 OK (+1: efeito vira pop-up, lista fechada, máximo 4); `BraboServiceTest` OK.
  Amostra: `Videos/cortes/amostra-estilo-brabo.mp4`.

## Voz nova do Brabo (15/11)
- `kokoro-onnx 0.6.1` (PyPI) + modelo `kokoro-v1.0.onnx`/`voices-v1.0.bin` do release oficial do GitHub
  (thewh1teagle/kokoro-onnx), em `python/vozes/` (fora do git), instalados com o OK do José.
- José ouviu 4 amostras e escolheu **pm_alex**. Brabo (gta) → Kokoro; Mestre (ensino) continua no Piper.
  Sem o modelo na pasta, volta para o Piper sozinho. `test_brabo` 31 OK (+1: voz Kokoro com modelo falso).

## Layout pelas zonas seguras (15/11)
- José: "tá muito no meio". Pesquisa (guias de Shorts): meio livre para a ação, nada nos 20% de baixo nem na beirada direita.
- Brabo: faixa (`tela`) no topo, pop-up menor (120) e mais alto (y 720), legenda no terço de baixo (margem 450).
  Notas em `knowledge/edicao-7-tecnicas.md` do cofre.
