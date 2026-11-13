# 029 — Sentinela: Escudo do Criador (golpes contra canal)
**Status:** ✅ construído e testado (12/11, lote pedido pelo José) · Java escrito pela 001 · ⏳ commit com o OK

## Ideia
Os canais novos (historinhas e GTA) viram alvo. O detector de golpe 🎣 (Ticket 023) agora também reconhece
os dois golpes mais comuns contra criador:
1. **Falso patrocínio:** "baixe o media kit / instale o jogo" com anexo `.scr`, `.exe` ou compactado com senha.
2. **Falso aviso de direitos autorais:** "seu canal será removido, verifique sua conta neste link".

Não tem tela nova: é o mesmo botão 🎣 da Central e a mesma frase de voz ("isso é golpe: ...").

## O que mudou no `DetectorDeGolpe`
- 4 sinais novos: anexo perigoso, "baixar/instalar para fechar parceria", ameaça de remover o canal, "faça login / verifique a conta".
- Plataformas na lista de imitação: YouTube, Google, Instagram, TikTok, Facebook, Twitch, Kwai.
- Link de site de armazenamento (MediaFire, Mega, Dropbox, WeTransfer...) conta 1 ponto.

## 🐛 Bug antigo achado e corrigido
Um texto de 4000 letras no formato `a.a.a.a...` **derrubava o pedido com erro 500** (estouro de pilha no regex de link).
Existia desde o Ticket 023; o teste antigo rodava o regex direto e não pegava. Pela API de verdade, estourava.
- **Causa:** grupo repetido `(\.nome)*`: no Java, cada volta do grupo usa a pilha.
- **Conserto:** uma classe de letras só (`[a-z0-9.-]*`), sem grupo repetido.
- **Prova:** teste novo pela API (MockMvc) com 4 textos de ataque no limite de 4000 letras → 200.
- **Lição:** testar o ataque **pelo caminho real** (a API), não só a função.

## Evidências
- `DetectorDeGolpeTest`: +5 grupos de teste (falso patrocínio, falso copyright, plataformas oficiais × imitadoras, conversa normal de criador continua "baixo", ReDoS das regras novas).
- Teste real pela API ligada: `a.` × 2000 → responde "baixo" (antes: erro 500).
