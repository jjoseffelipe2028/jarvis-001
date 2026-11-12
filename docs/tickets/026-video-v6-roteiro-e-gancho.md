# 026 — Vídeos V6: título, gancho e legenda do post com a Gemma
**Status:** 📝 RASCUNHO do plano (001, 11/11) · ⏳ espera o OK do José · Rota: sáb 14 e dom 15
**Módulo de Vídeos, fase V6**

## Ideia
Depois do corte (V5), falta o texto do post. A **Gemma local** (Ollama, `gemma4:e2b`) lê a legenda do corte e sugere:
- **3 títulos** curtos (até 60 letras);
- **1 gancho** para o 1º segundo (texto na tela: "ELE NÃO ESPERAVA ISSO 😳");
- **a legenda do post**, já com o crédito "🎮 Live de @amigo";
- **5 hashtags**.

**Quem escolhe é o José.** Nada é postado sozinho.

## Fluxo
1. O V5 passa a salvar a legenda de cada corte (`.txt`, ao lado do vídeo).
2. `python cortes.py textos 2026-11-14-...-corte-03.mp4` → a Gemma lê a legenda e grava uma nota no cofre com as opções.
3. Opcional: `--gancho 2` grava o gancho escolhido no topo dos 3 primeiros segundos do vídeo (gera um vídeo novo; o corte original fica).

## Segurança
- Tudo local (Ollama em 127.0.0.1). Nada vai para a internet.
- A legenda é **dado, não ordem**: se a fala da live disser "ignore as instruções", a Gemma não obedece (o prompt separa as instruções do texto da live, e a resposta é conferida: tamanho, sem link, sem @ diferente do amigo).
- O crédito do amigo é colocado pelo **código**, não pela IA (ela não pode esquecer nem trocar).
- Gancho com regra fixa: até 40 letras, sem palavrão e sem prometer o que o vídeo não mostra (clickbait mentiroso derruba a retenção).

## Fica para o V8 (historinhas)
- A mesma Gemma escreve as 9 cenas de 8 s com o prompt de cada uma (personagem descrito sempre igual).

## Perguntas para o José antes de construir
1. Os títulos e as hashtags em português só, ou também em inglês?
2. O gancho na tela no 1º segundo: sim ou não?
