# 039 — Central v4: falar com a IA na tela e páginas por área
**Status:** ✅ construído, testado e conferido no navegador (13/11) · pedido do José · Java escrito pela 001 · ⏳ commit com o OK

## Ideia
1. **Falar direto com o Jarvis na Central**, sem abrir o terminal. A conversa vai para a IA **local** (Gemma, no Ollama).
2. **Menu com áreas**: 🏠 Início · 🛡️ Cyber · 🎬 Vídeos · 🎮 Jogos. Cada uma abre a sua página. Os cartões do início também levam para a área.
3. **Página Cyber de verdade**: portas, travas, isca, 🎣 golpe e 🤝 proposta num lugar só.
4. Páginas Vídeos e Jogos: o que já existe (com o comando do terminal) e o que está na fila.

## Como funciona o chat
`Central (navegador)` → `POST /central/acoes/chat` → `ConversaService` → `Ollama em 127.0.0.1:11434` → texto de volta.

## Segurança
- **A IA só conversa.** Não existe ferramenta, comando nem ação ligada ao chat. Para agir, só os botões (lista fechada).
- Endereço do Ollama **fixo** em `127.0.0.1` (não vem do pedido nem do `.env`); nunca segue redirecionamento.
- Instruções fixas, separadas da fala do usuário. O histórico só aceita papel `user` ou `assistant`:
  não dá para mandar uma fala de `system` e trocar as regras (400).
- Mesmos porteiros da Central: Host, cabeçalho, **Origin obrigatório** no POST e crachá.
- Limites: fala de 1000 letras, 8 falas de histórico, resposta de 2000. Uma conversa por vez (ocupada → 503).
- A resposta é limpa de caracteres de controle e entra na tela com `textContent` (nunca vira código).
- A conversa **não vai para o log** e **não fica guardada** no navegador (some ao fechar).
- `paginas.js` não faz pedido "por fora": usa só o `pedir()` e o `enviar()` da `central.js`.

## Arquivos
- Java: `ConversaService`, `ChatRequest`, `CentralChatController`; `CentralSentinelaController` ganhou `/proposta` e `/isca`.
- Tela: `index.html` (menu, chat e 3 páginas), `paginas.js` (novo), `central.css`.

## Evidências
- **Java 408 testes, 0 falhas** (+18): `ConversaServiceTest` (9), `CentralChatControllerTest` (4),
  rotas novas da Sentinela (2), página (1 + arquivos novos nas travas que já existiam).
- No navegador (API ligada, IA real): conectou, console sem erro; pergunta ao Jarvis → resposta da Gemma na tela;
  página Cyber → isca "desligada" e proposta falsa → 🚨 com 4 motivos e os 5 passos.
- A 1ª resposta levou perto de 40 s (o modelo estava carregando). As seguintes são mais rápidas.

## Limites conhecidos
- A IA local é pequena: erra e inventa às vezes. Por isso ela não executa nada.
- Vídeos e Jogos ainda não têm botões de ação na Central: mostram os comandos do terminal.
