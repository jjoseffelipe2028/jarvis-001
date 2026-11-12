# 027 — Vídeos V8: historinhas com bonecos de fruta (roteirista + montador)
**Status:** ✅ construído (12/11, 4 dias antes da rota 16–18/11) · pedido do José: "5h de Jarvis, faz o máximo" · plano do V8 aprovado no mapa geral (10/11) · ⏳ commit com o OK
**Módulo de Vídeos, fase V8**

## Ideia
O vídeo dos bonecos vem do **app do Gemini** (o José assina o Pro e gera à mão). O Jarvis faz o resto, local:
1. **Roteirista:** a Gemma escreve 9 cenas de 8 s (gancho → conflito → reviravolta → lição).
2. O **código** monta o prompt de cada cena com o **personagem descrito sempre igual** (o boneco não muda de cara).
3. O José gera cada cena no app do Gemini e salva `cena01.mp4` … `cena09.mp4` numa pasta (ex.: `ep01`).
4. **Montador:** junta as cenas em pé (9:16), põe legenda (Whisper), rótulo ("24 horas antes") e música baixinha.
5. **O José aprova** o roteiro antes de gerar e o episódio antes de postar.

Fora: robô clicando no app do Gemini (regra do Google, risco de perder a conta). Ligação pela API (Vertex AI): só com o OK do José, depois.

## Como usar
```
python historinhas.py roteiro "o Limão aprende a dividir"
python historinhas.py montar ep01 --musica trilha.mp3 --rotulo 1 "24 horas antes"
```
A pasta do episódio e a música ficam dentro de `JARVIS_CORTES_ENTRADA` (`Videos\lives`). O episódio sai em `Videos\cortes`, com o `.srt` ao lado.

## Segurança
- Personagem **só da lista** (Seu Abacaxi, Manguinha, Limão Azedo); a descrição é do código, nunca da IA.
- Resposta da Gemma conferida: 9 cenas, tamanhos, sem link, sem @. Torta → tenta de novo 1 vez → erro. O tema vai separado das instruções (anti prompt injection).
- Pasta do episódio e música: só o nome, dentro da pasta de entrada (`../`, caminho, atalho e extensão estranha → recusa).
- Cena faltando no meio ou repetida → erro (não monta episódio sem a cena 5 sem avisar).
- Cena sem som ganha silêncio; cena deitada é cortada em pé; cena com mais de 60 s → erro.
- FFmpeg sem shell, `-n` (nunca sobrescreve); as cenas originais só são lidas.

## Evidências (12/11)
- Python: **140 testes OK** (+12).
- Ensaio real: 3 cenas falsas (2 com a voz do Jarvis, 1 deitada e sem som) + música gerada aqui → **24 s, 1080x1920**, rótulo "24 horas antes" no topo aos 8 s, legenda embaixo, música baixinha. Com a dica de palavras da novelinha, o Whisper acertou "perco" (antes: "peco").
- Gemma real: tema "o Limão Azedo aprende a dividir as frutas com a Manguinha" → 9 cenas em 48 s, prompts com as descrições fixas. ⚠️ Ela escreveu a Manguinha querendo comer uma manga → **por isso o José revisa o roteiro antes de gerar**.
- Ataques barrados: pasta `../cortes`, música `../x.mp3`.

## Fica para depois
- Ligação direta pela API do Google (Vertex AI, crédito do plano Pro): só com o OK do José (muda a regra "tudo local").
- Transição entre cenas e vinheta de abertura.
