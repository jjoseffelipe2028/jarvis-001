# 047 — Estúdio 001: a dupla cria o jogo sozinha
**Status:** ✅ construído, testado e com teste real (13/11) · decisão do José: [[2026-11-13 Jarvis com autonomia (Estudio 001)]] · Java escrito pela 001 · ⏳ commit com o OK

## Ideia
O José pede um jogo **na Central** e recebe o jogo pronto, sem abrir o terminal:
`001 (Claude) planeja → 002 (Antigravity) revisa o plano → 001 corrige e constrói → pronto`.

## Como funciona
- Painel **🎮 Estúdio 001** no meio da página Início: escrever → **Revisar → Confirmar** → a tela mostra cada etapa.
- `POST /central/acoes/estudio` cria a pasta `workSpace/estudio-jogos/projetos/<data>-<nome>` e grava o `PEDIDO.md`.
- O ciclo roda em segundo plano; `GET /central/acoes/estudio` diz a etapa. Cada etapa deixa um arquivo:
  `PLANO.md` (001), `REVISAO.md` (002), o jogo + `LEIAME.md` + `RESUMO.md` (001).
- A 001 trabalha com as **79 skills de game-dev** auditadas (Ticket da auditoria, 13/11).

## Segurança (exceção controlada à regra "a IA nunca executa")
- **O pedido nunca vira argumento de comando:** vai num arquivo; os comandos são 100% fixos.
- 001 no plano: só **lê**. Na construção: só **lê e escreve arquivos**. **Sem terminal, sem internet, sem MCP**
  (`--disallowedTools Bash,PowerShell,WebFetch,WebSearch`, `--strict-mcp-config`, `--permission-mode dontAsk`).
- Regras de *deny* (`.env`, pasta `.claude`) num arquivo **fora** da pasta onde a 001 escreve.
- 002 em `--mode plan` (só analisa).
- **Segredos fora do ambiente dos agentes:** variáveis `JARVIS_*` e tudo com TOKEN/SECRET/KEY/SENHA são removidas.
- Um pedido por vez (ocupado → 503), tempo máximo por etapa (6 / 6 / 25 min), saída de até 64 KB, erro cru nunca vai para a tela.
- Mesmos porteiros da Central (Host, cabeçalho, **Origin obrigatório**, crachá). O log guarda só o tamanho do pedido.
- Agente nenhum faz commit, push, posta ou compra: não tem ferramenta para isso.

## Arquivos
- Java: `EstudioService`, `EstudioRequest`, `CentralEstudioController`; `WebConfig` e `EventosInterceptor` (rota nova).
- Tela: `index.html` (painel Estúdio, página Jogos), `paginas.js` (Revisar → Confirmar e acompanhamento), `escritorio.js` (evento).
- Config: `jarvis.estudio.pasta` (padrão `~/workSpace/estudio-jogos`).

## Evidências
- **Java 425 testes, 0 falhas** (+11): `EstudioServiceTest` (7, com agentes falsos: ciclo, comandos fixos, sem terminal,
  um por vez, falha sem derrubar, sem pasta), `CentralEstudioControllerTest` (4).
- **Teste real pela Central (13/11, 03:18 → 03:21, 3 minutos):** pedido "mini jogo de clicar no alvo, 30 s, placar, alvo que
  diminui e recorde" → 001 planejou (escolheu sozinha as skills `game-ui-ux`, `game-feel`, `save-systems`) → 002 revisou
  (8 pontos: onda de impacto no lugar do clique, alvo mínimo maior no celular, zona segura longe do placar, sons, combo) →
  001 aceitou tudo e construiu `index.html` (15 KB) + `LEIAME.md`. **Jogo aberto no navegador e jogado:** o acerto conta ponto,
  a onda de impacto aparece, console sem erro. A tela da Central mostrou as 4 etapas e o resumo.
