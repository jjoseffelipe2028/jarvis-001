# 046 — Chat da Central que também pede ações
**Status:** ✅ construído, testado e conferido no navegador (13/11) · pedido do José ("falar com o Jarvis como falo com a 001") · Java escrito pela 001 · ⏳ commit com o OK

## Ideia
No chat da Central, além de conversar, o José **pede**: "quais minhas pendências?", "tem porta aberta?",
"confere a isca", "status do git", "que horas são?", "isso é golpe: <mensagem>", "abre a cyber".

## Como funciona
`chat` → `RoteadorDoChat` (regras fixas, igual ao `intencoes.py` da voz)
- bateu numa regra → a API devolve só o **nome** da ação (`{"acao":"pendencias"}`) → a tela chama a **rota que já existe**
  pela tabela fechada `ACOES_DO_CHAT` (mesmos porteiros e mesmo crachá);
- pedido de mudar o PC ("apaga", "instala", "faz um pix", "posta"...) → **texto fixo de recusa**, sem chamar a IA;
- não bateu em nada → conversa normal com a Gemma (Ticket 039).

## Segurança
- **A IA nunca escolhe a ação.** Quem escolhe são regras fixas no Java; a IA nem é chamada quando é ação.
- O roteador **não executa nada**: só devolve um nome da lista. Nome fora da tabela da tela = nada acontece.
- Golpe: a mensagem colada é conferida **antes** da recusa ("faz um PIX" dentro da mensagem não vira pedido do José).
- Página: só um dos 4 nomes fixos volta, nunca o texto digitado. "abre a cyber e apaga tudo" → recusa.
- O log guarda só o nome da ação (`rota=pendencias`), nunca a frase nem a mensagem colada.
- Ação não entra no histórico da IA.

## Arquivos
- Java: `RoteadorDoChat` (novo), `CentralChatController` (rota → ação ou conversa).
- Tela: `paginas.js` (`ACOES_DO_CHAT`, `fazerAcao`), `index.html` (dica no campo, página Cyber com os vigias novos).

## Evidências
- **Java 414 testes, 0 falhas** (+6): `RoteadorDoChatTest` (6), `CentralChatControllerTest` (+2).
- **Navegador, API de verdade:** as 8 frases acima responderam certo (26 pendências, 12 portas, isca desligada,
  git com trabalho sem commit, recusa do "apaga", hora de Brasília, 🚨 golpe do falso parente, página Cyber aberta).
  Console sem erro.

## Fica para depois
- Botões e frases para a **ronda do defensor** e o **produtor** (a API Java chamando o Python por lista fixa).
