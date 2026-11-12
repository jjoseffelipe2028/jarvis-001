# 026 — Vídeos V6: título, gancho e legenda do post com a Gemma
**Status:** ✅ construído (12/11, 2 dias antes da rota) · OK do José ("Bora, V6") com os padrões da 001: **só português** e **gancho na tela: sim** · ⏳ commit com o OK

## Como usar
- `python cortes.py textos <corte.mp4> --arroba @amigo` → nota no cofre com 3 títulos, gancho, legenda (crédito pelo código) e 5 hashtags.
- `python cortes.py gancho <corte.mp4> [--texto "MEU GANCHO"]` → `<corte>-gancho.mp4`, com o gancho no meio da tela nos 3 primeiros segundos (o corte original fica igual).

## Evidências (12/11)
- Python: **128 testes OK** (+12: validação, link, @ de outra pessoa, palavrão, JSON torto, nova tentativa, prompt injection na fala, emoji fora da tela).
- **Gemma real** no corte do ensaio ("pulou de moto em cima do helicóptero"): 3 títulos, gancho "Pulo de moto no helicóptero! 🤯", legenda com "🎮 Live de @amigo_gta" e 5 hashtags, em ~70 s (1ª carga do modelo).
- Gancho gravado: 1080x1920, 25 s, som copiado. Emoji saía como um desenho estranho (a fonte não tem) → agora sai **só da tela**; o acento fica ("A POLÍCIA CHEGOU").
- Ataques barrados: gancho com link, gancho repetido no mesmo corte, @ com `;`.
- O V5 agora grava o `.srt` (o V6 lê dele). Corte sem fala (tiroteio) → aviso para escrever os textos à mão.
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
