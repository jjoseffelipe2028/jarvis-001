# 017 — Escritório ao vivo: os bonecos reagem ao trabalho real
**Status:** ✅ aprovado (testado pelo José em 10/11, 01:18)
**Criado:** 2026-11-09 pela 001 · Fase 5, parte 3

## Ideia
Antes, os bonecos só se mexiam quando o José clicava. Agora, quando um braço trabalha de verdade (voz, terminal, Python), o **Jarvis reage na tela** e a narração registra. Exemplos:
- "Bom dia, Jarvis" e salvar o daily pela voz → "Daily salvo 📝".
- Um pedido sem token → "🚨 Pedido recusado!", e o 001 olha (👀).

O plano foi aprovado pelo José às 20:41 ("faz o máximo que conseguir").

## Como funciona
| Peça | O que faz |
|---|---|
| `QuadroDeEventos` | Só na **memória**, guarda no máximo 30 eventos. Cada um tem **nomes fixos** (ação, resultado, cliente) e a hora. Nunca guarda texto de nota, caminho ou corpo de pedido. |
| `EventosInterceptor` | Roda **depois** de cada pedido em `/acoes/**` e só **anota**, sem decidir nada. Só entra o que está na **lista fixa**, com comparação exata: daily, pendências, git-status, video-avaliar e video-relatorio. `/acoes/hora` fica fora (seria barulho). Fica antes dos porteiros, então pedido recusado também aparece. |
| `GET /central/acoes/eventos?depois=N` | Rota **só de leitura**, atrás do crachá da Central. Número torto vira 0. Sem log por consulta, porque a Central pergunta a cada 10 s. |
| `escritorio.js` | Consulta a cada 10 s e confere tudo de novo contra listas fixas: ação desconhecida é ignorada e cliente estranho vira "sem identificação". Se a API reiniciar, recomeça sem repetir o passado. Mostra **📡 ao vivo / sem sinal**. |

Resultado vira só: `ok`, `recusado` (401/403), `invalido` (4xx), `indisponivel` (503) ou `erro`.

## Evidências
- Java: **269** testes, 0 falhas (+27):
  - `QuadroDeEventosTest` (3);
  - `EventosInterceptorTest` (5 rotas da lista, 7 fora da lista, cliente inventado, 7 status, exceção);
  - `EventosDePontaAPontaTest` (com token → `ok`; sem token → `recusado`; a hora não entra);
  - `CentralControllerTest` +2 (sem crachá 401; só os novos; número torto);
  - `CentralPaginaTest` (o escritório ouve o quadro).
- Teste no navegador (001, com a API simulada):
  - 1ª leitura só marca o ponto de partida;
  - daily novo → "Jarvis fez o daily pela voz/terminal ✔";
  - pedido recusado → balão "🚨 Pedido recusado!";
  - ação desconhecida (`rm -rf`) foi ignorada;
  - sinal "📡 ao vivo".
- Teste real (José, 10/11 01:18): pelo chat (`iniciar_jarvis.bat`), "quais minhas pendências". O log registrou `cliente=python acao=pendencias resultado=ok`, e a narração da Central mostrou "Jarvis fez as pendências pela voz/terminal ✔". ✅
- Commit: junto com o Ticket 018 (mesmo commit, arquivos em comum)

## Fica para depois
- Narração por voz (Parte 4).
- Cada skill "em breve" da Central com ticket próprio.
