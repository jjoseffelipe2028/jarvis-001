# 008 — Faxina de segurança (os 🟡 e 🟢 que ficaram para depois)
**Status:** 🔄 aguardando aprovação do José (construído com ele dormindo, 2026-11-09)
**Criado:** 2026-11-09 pela 001

## O que entrou
| De onde veio | Problema | Correção | Teste |
|---|---|---|---|
| 004 🟡3 | Um "neto" do git (programa filho do filho) sobrevivia depois que o git saía | A árvore de processos é anotada a cada 50 ms **enquanto** o git roda; no fim, todos morrem | `netoQueFicaVivo_eMortoTambem` (falharia no código antigo) |
| 004 🟢6 | O git lia a config global e do sistema (`~/.gitconfig`) | `GIT_CONFIG_NOSYSTEM=1` e `GIT_CONFIG_GLOBAL=/dev/null`: só vale a config do repositório | `ambienteApagaVariaveisGitDeFora` + git real ainda funciona |
| 002/003 🟢4 | Erro inesperado caía no erro padrão do Spring | Tratador genérico: 404/405 do Spring mantêm o status; o resto vira 500 `erro interno`, sem a mensagem de dentro | `erroInesperado_500SemVazarMensagem`, `metodoErrado_continua405` |
| 003 🟢3 | Letras "em branco" (U+3164, U+2800...) e acentos empilhados ("zalgo") passavam nas pendências | Removidas; no máximo 2 acentos seguidos | `letraEmBrancoEZalgo_saemLimpos` |
| 006 🟡4 | Rota pública `/acoes/prova` sem limite nem log | No máximo 30 por segundo → 503; log sem o desafio | `rajadaDeProvas_eCortada` |

## Evidências
- Java **158** testes, 0 falhas.
- Revisão `jarvis-hacker` (008 + 009, 2026-11-09): **✅ APROVA com ressalvas**, 0 🔴. Corrigidos na hora:
  1. 🟡 Limite de 30/s era baixo: outro programa podia travar o aperto de mão do Python → **300/s** e 1 linha de log por segundo.
  2. 🟡 "API lotada" aparecia como "impostor" → **corrigido** (Python) + teste.
  3. 🟡 O "bom dia" escondia o alarme de impostor → **corrigido** (009) + teste.
  4. 🟡 Perguntar os filhos de um git já morto podia pegar o PID reaproveitado de **outro programa** → **corrigido**: só pergunta com o git vivo.
  6. 🟢 Parâmetro do tipo errado (`?n=abc`) dava 500 → **400** + teste.
  7. 🟢 Ordem da limpeza (invisível antes, acentos depois) → **corrigido**.
  - Aceitos: 5 (um neto que nasce e órfã em menos de 50 ms ainda escapa: limite do Java sem "Job Object"; risco baixo para `git status`).
- **Teste real** (API numa porta separada): tudo funcionando com pedidos assinados.
- Commit: _(preencher)_

## 🎤 Pergunta para o José
> Por que o erro genérico **não** pode simplesmente transformar tudo em 500?

**Resposta do José:** Porque nem todo erro é culpa do servidor: rota errada é 404, não 500. Assim o Jarvis não mente e quem pediu sabe o que corrigir.
