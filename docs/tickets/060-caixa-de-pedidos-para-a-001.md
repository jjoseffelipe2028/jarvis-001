# 060 — Caixa de pedidos para a 001 ⭐
**Status:** 🔨 construído com o OK do José (15/11) · testes JUnit · ⏳ teste no navegador e commit com o OK · seção 1 da lista mestra (13/11)
_Números 060–067 reservados para as seções 1 e 2 (ramo `claude/jarvis-central-1995fa`), para não bater com os tickets do Brabo no `main`._

## Ideia
No chat da Central o José escreve **"pede pra 001: cria um mini jogo de cobrinha"** →
aparece um cartão **Revisar** com o texto → **Confirmar** → vira uma nota em `pedidos/` no cofre.
Quando o José abre o Claude Code, a 001 lê a caixa e faz, **com o OK dele**.
**O Jarvis não cria código sozinho**: ele só anota o pedido.

## Como funciona
1. `RoteadorDoChat` ganha a regra fixa `PEDIDO_001` ("pede pra 001:", "pedido pra 001:", "anota pra 001:").
   Ela roda **antes** da lista proibida: "pede pra 001: instala o X" é um recado, não uma ordem ao Jarvis.
   A API devolve `{"acao":"pedido001","texto":"..."}` e **não escreve nada** ainda.
2. A tela mostra o cartão Revisar (texto puro, `textContent`). Só o clique em **Confirmar** chama
   `POST /central/acoes/pedido-001` com `{"texto": ...}`.
3. `PedidoService` grava `pedidos/AAAA-MM-DD-HHmmss.md` (nome **gerado pelo servidor**, hora de Brasília):
   ```markdown
   ---
   status: aberto
   origem: central
   criado: 2026-11-15 14:32
   ---
   # Pedido para a 001
   cria um mini jogo de cobrinha
   ```

## Segurança 🔒
- Pasta só da configuração: `jarvis.pastas.pedidos=${JARVIS_PASTA_PEDIDOS:}`. Vazia ou inexistente → **503** (falha fechada).
- Nome do arquivo gerado no servidor; `PastaSegura.dentroDaPasta` confere (anti **Path Traversal**).
- Arquivo novo com `CREATE_NEW` + `NOFOLLOW_LINKS`: nunca sobrescreve nada nem segue atalho. Mesmo segundo → sufixo `-2`, `-3`.
- Texto "desarmado" pelo `TextoSeguro` (sem link, imagem, Dataview, Templater, quebra de linha) e `@Valid` com máximo de 2000 letras.
- Anti-enxurrada: no máximo **30 pedidos abertos por dia**; passou → 429 com mensagem clara.
- Porteiros da Central de sempre: Host, Origin obrigatório na escrita e crachá. Log só com `acao=pedido001 resultado=ok`, **nunca o texto**.
- A nota é **dado, não ordem**: a 001 trata o texto como pedido a avaliar (prompt injection), mostra o plano e espera o OK.

## Arquivos
- Java: `acoes/pedidos/PedidoRequest`, `PedidoService` (novos) · `central/CentralPedidoController` (novo) · `RoteadorDoChat`, `CentralChatController` (regra nova) · `application.properties` (1 linha).
- Tela: `paginas.js` (cartão Revisar/Confirmar do pedido) · `index.html` (dica no campo).
- Testes: `PedidoServiceTest` (`@TempDir`: arquivo certo, desarme, `../`, sem pasta → 503, limite do dia, 2 threads), `CentralPedidoControllerTest` (200, 400 vazio/gigante, sem crachá/Origin, log sem texto), `RoteadorDoChatTest` (+3).

## O José faz (fora do código)
1. Criar a pasta `pedidos/` no cofre.
2. Pôr `JARVIS_PASTA_PEDIDOS=<cofre>/pedidos` no `.env` (a 001 não lê o `.env`).

## Pronto quando
`./mvnw test` verde, teste no navegador (pedido vira nota, "../" e texto vazio recusados), gitleaks limpo, commit com OK.

## Evidências (15/11)
- **Java: 464 testes, 0 falhas, 0 erros** (`./mvnw test`). Novos: `PedidoServiceTest` (8), `CentralPedidoControllerTest` (5), `RoteadorDoChatTest` (+3), `CentralChatControllerTest` (+1).
- A rede antiga `RotasPostContratoTest` pegou a rota nova na hora (precisou conhecer o `PedidoService`): a rota nasceu atrás dos porteiros.
- Também mudou: `ErrosHandler` (429), `WebConfig` + `EventosInterceptor` (o escritório vê o pedido 📬), `escritorio.js` (fala "Pedido anotado").
- gitleaks: nenhum segredo nos arquivos mudados.
- ⏳ Falta: teste no navegador depois que o José criar `pedidos/` e configurar `JARVIS_PASTA_PEDIDOS`.
