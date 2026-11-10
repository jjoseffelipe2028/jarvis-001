# 016 — Central v2: escritório pixel dos agentes
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001 · Fase 5, parte 2

## Ideia
Ver o time trabalhando. Embaixo da Central entra um **escritório em pixel art** com **001** (Claude), **Jarvis** (robô) e **002** (Gemini). Eles chegam, sentam, digitam, vão tomar café e mostram balões com a tarefa. Cada agente tem um cartão com as suas **skills**, e o Jarvis narra o que acontece.

A prévia foi aprovada pelo José (20:27).

## Como foi feito
- Arquivo novo `static/escritorio.js`, dentro de uma função fechada, para não brigar com a `central.js`.
- **Tudo desenhado em código** num `<canvas>` de 256×144 ampliado sem borrar: sem imagem, fonte ou biblioteca de fora. Continua 100% offline.
- O céu da janela segue a hora real (dia, fim de tarde ou noite).
- Texto só com `textContent`. O estilo vem do `central.css`, sem `style=` no HTML, porque a CSP bloqueia.

## Skills: só dado real
| Agente | Skill | Estado |
|---|---|---|
| Jarvis | Hora | ✅ no ar (relógio do PC) |
| Jarvis | Pendências, Git status | ✅ no ar: rotas reais da Central via `pedir()` (crachá HttpOnly) |
| Jarvis | Daily, Porteiro de vídeo, Relatório, Narrar | 🔜 em breve (cada um com ticket próprio) |
| 001 | Planejar, Codar Java, Segurança, Mentor | 🟣 via terminal: só **prepara o pedido no cartão de aceite**; Aceitar copia e Recusar descarta |
| 002 | Contestar, Revisar, Pesquisar | 🔜 em breve (Fase 7, Gemini CLI) |

Sem número inventado: quando a API falha, o boneco diz "⚠️ Não consegui" e a narração mostra o motivo.

## Segurança
- Nenhuma rota nova e nenhum porteiro mudou.
- O `escritorio.js` não chama a rede sozinho (sem `fetch`, WebSocket ou beacon). Ele só usa o `pedir()` da Central.

## Evidências
- Java: **242** testes, 0 falhas (+4). O `CentralPaginaTest` agora também cobre o `escritorio.js`:
  - offline;
  - sem `innerHTML`/`eval`;
  - sem rede própria, token, cookie, storage ou imagem de fora;
  - carrega depois da `central.js`;
  - nenhum `style=` no HTML.
- Teste no navegador (001, sem a API): os 3 agentes chegaram e sentaram, apareceram as 14 skills, e não houve erro de JS.
  - Pendências sem API → "Não consegui", sem dado falso.
  - Planejar ticket → cartão preenchido; Recusar → "nada saiu do PC".
- Teste real (José, 20:40): ícone → "🔓 conectada" → escritório com os 3 agentes → Pendências (log: `pendencias ok total=25`) → Planejar ticket → Aceitar copiou e o José colou no Claude Code. Nada enviado sozinho.
- Commit: `668754f`

## Fica para depois
- Os bonecos reagirem aos **eventos reais** do log (Parte 3).
- Skills "em breve" da Central, cada uma com ticket.
- Narração por voz (Parte 4).
