---
name: raio-x-video
description: Desmonta um vídeo (de preferência Short) cena por cena — cortes, ritmo, gancho, som e batida — e gera uma nota de raio-X com padrões para o cofre. Use quando o José pedir para analisar, estudar ou fazer "raio-x" de um vídeo próprio ou autorizado.
argument-hint: "[caminho do vídeo .mp4]"
allowed-tools: Read, Bash(python .claude/skills/raio-x-video/scripts/raiox.py:*), Bash(python .claude/skills/raio-x-video/scripts/cortes.py:*), Bash(python .claude/skills/raio-x-video/scripts/fotos.py:*), Bash(python .claude/skills/raio-x-video/scripts/som.py:*)
---

<!-- Skill criada pela 001 em 15/11/2026. Ideia inspirada no rea-video, mas o código é nosso (só ffmpeg + Python padrão, nada baixado). -->
<!-- Write e Edit ficam FORA do allowed-tools de propósito: preencher a nota pede permissão e o José vê a mudança. -->

# 🩻 /raio-x-video

Analise o vídeo **$ARGUMENTS**.

## 0. Antes de começar
- [ ] O vídeo é **do José** ou ele tem **autorização do dono**? Se não tiver, pare. Baixar vídeo de terceiros é proibido.
- [ ] Nada sai do PC: sem upload, sem API externa, sem ler `.env`.
- [ ] Pergunte ao José a pasta do cofre se não souber. As notas vão em `<cofre>/knowledge/raio-x/`.

## 1. Rodar tudo de uma vez
```bash
python .claude/skills/raio-x-video/scripts/raiox.py "VIDEO.mp4" --nota "<cofre>/knowledge/raio-x" --fonte "vídeo próprio"
```
Ele roda os 3 passos abaixo e cria `<nome>.md` com os números preenchidos, mais a pasta `<nome>/` com a folha e a onda. Nunca sobrescreve um raio-X antigo (cria `-2`, `-3`...).

Os passos também rodam soltos:

### 1a. Cortes e ritmo — `cortes.py`
- Se vierem cortes demais (vídeo com muito movimento), use `--limiar 0.4`. Se vierem de menos, use `--limiar 0.2`.
- **Gameplay com câmera, @ ou legenda fixas na tela** (ex.: Shorts do Brabo/Capi): use `--modo jogo` (também no `raiox.py`). O modo normal dá "0 cortes" nesses vídeos.
- ⚠️ No modo jogo, o resultado é uma **lista de candidatos**, não a verdade: **clarão (flash) e explosão parecem corte**. Confira cada um na foto antes de escrever na nota. (1º teste, 15/11: 5 candidatos, 1 corte real.)
- Gera `raio-x_<nome>/cortes.json` ao lado do vídeo.

### 1b. Fotos de cada cena — `fotos.py`
- Tira uma foto do **meio** de cada cena, com número e tempo, em `raio-x_<nome>/cenas/`.
- Junta tudo em `folha_1.jpg` (6 colunas, até 48 cenas por folha; depois vem `folha_2.jpg`...).
- Cores parecidas (ex.: amarelo → branco) podem esconder um corte. Se a folha mostrar duas cenas diferentes juntas, rode de novo com `--limiar 0.2`.

### 1c. Som — `som.py`
- **Silêncios** (abaixo de -40 dB por 0,3 s ou mais): pausas de fala e "respiros" da edição.
- **Golpes**: subidas fortes de volume, como batida, impacto ou efeito sonoro (whoosh, ding).
- **BPM**: batida da música, com confiança de 0 a 1. Abaixo de 0.3, trate como palpite.
- **Cortes na batida**: quantos cortes caem a até 80 ms de um golpe. Acima de 50% = edição no ritmo da música.
- Golpe no segundo 0 não é contado (não tem nada antes para comparar).

## 2. Olhar o vídeo
- **Leia as folhas e a `onda.png` com a ferramenta Read.** Abra uma cena sozinha (`cenas/cena_007.jpg`) só quando tiver dúvida.
- Não invente: o que não der para ver na foto (ex.: a fonte exata da legenda) fica como "não dá para saber".

## 3. Completar a nota
Troque cada `_preencher_` da nota:
1. **Gancho**: o que acontece antes do 1º corte?
2. **Cortes e ritmo**: com os números e o que eles querem dizer.
3. **Legenda**: fonte, cor e como entra.
4. **Som**: voz, música e efeitos (use golpes, silêncios e BPM).
5. **Tipo de cada cena**: filmada, IA, tela gravada ou texto animado.
6. **Material próprio**: o que o José teria que gravar.

Depois escreva **um padrão aprendido** (uma frase) e adicione a mesma frase, com o link da nota, em `knowledge/raio-x/raio-x-padroes.md`.

## 4. Responder ao José
Em português e curto: 3 descobertas, o padrão aprendido e o **próximo passo**.
