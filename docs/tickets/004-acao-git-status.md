# 004 — Ação `git-status` (primeiro braço que roda um programa)
**Status:** 🏁 pronto (2026-11-09) — testado pelo José com `curl`
**Criado:** 2026-11-09 pela 001 · José pediu "faz o máximo" e aprova na volta

## O que faz
`GET /acoes/git-status` 🔒 → diz se o repositório tem algo sem commit.
```json
{"branch":"main","limpo":false,"alterados":2,"novos":1,"aFrente":0,"atras":0}
```

## ⚠️ Para o José fazer
- Pôr no `.env` (a IA não mexe nele):
  `JARVIS_PASTA_GIT="C:/Users/jjose/workSpace/jarvis-001"`
- Revisar `application.properties` (+1 linha: `jarvis.pastas.git`).

## Escopo
**Entrou:** `acoes/git/GitStatusService` + `GitStatusController`, testes, README.
**Não entrou:** `git log`, mostrar nomes de arquivo, qualquer comando que escreve no Git.

## 🔒 Segurança
| Risco | Trava |
|---|---|
| **Injeção de comando** (rodar outro programa) | Comando e argumentos **fixos** no código; `ProcessBuilder` sem shell (`;`, `&&`, `|` não viram comando) |
| `git.exe` falso plantado na pasta do Jarvis | Git achado pelo **caminho completo**, só em pastas absolutas do PATH, nunca dentro do repo |
| `.git/config` malicioso rodando programa | `core.fsmonitor=false` + `--ignore-submodules=all` |
| Variáveis `GIT_*` trocando repositório ou config | Apagadas antes de rodar |
| Git travado ou saída gigante | 5 s e 64 KB no máximo → processo morto |
| Muitos pedidos juntos (DoS) | Um git por vez (`Semaphore`); o segundo recebe 503 "git ocupado" |
| Vazar nomes de arquivo ou caminhos | Resposta e log só com **números**; branch filtrada; erro do git descartado |
| Site malicioso | Token + filtro `Host` (já existentes) |

## Evidências
- `./mvnw test`: **134 testes, 0 falhas, 2 pulados** (eram 112).
- **Ataque real no teste:** `core.fsmonitor` num `.git/config` → o git "cru" **executou** o programa; pelo Jarvis, **não**.
- Revisão `jarvis-hacker` (2026-11-09): **✅ aprova com ressalvas**, 0 🔴, 5 🟡, 3 🟢:
  1. 🟡 `git.exe` plantado na pasta atual (Windows) → **corrigido** (`resolverGit` + teste).
  2. 🟡 Filtros `clean` e submódulos podem rodar programa → submódulos **corrigido** (`--ignore-submodules=all`); filtros: **risco aceito** (exige escrever no `.git` do próprio dono; não há `-c` que desligue todos).
  3. 🟡 Neto do processo pode sobreviver → **depois** (ticket próprio: matar a árvore toda).
  4. 🟡 Thread presa na leitura → **corrigido** (fecha a saída no `finally`).
  5. 🟡 DoS com muitos pedidos → **corrigido** (um por vez + teste).
  6. 🟢 Config global do git ainda é lida → depois (testar `GIT_CONFIG_NOSYSTEM` no Windows e no CI).
  7. 🟢 Branch com `..` passa (só texto) → aceito.
  8. 🟢 `UncheckedIOException` → conferido: `ErrosHandler` responde "falha ao gravar" fixo, sem a mensagem.
- Teste manual com `curl` (José, 2026-11-09): **200**, `{"branch":"main","limpo":false,"alterados":4,"novos":3,...}`. Bate com o `git status` real (4 `M`, 3 `??`). Nenhum nome de arquivo na resposta ✅
- Commit: _(preencher)_

## 🎤 Pergunta para o José
> Por que o Jarvis roda o `git` **sem** passar por um terminal (`cmd`/`bash`)? O que um atacante faria se passasse?

**Resposta do José:** "porque o Jarvis roda no próprio PC" ❌ (isso é a trava do `127.0.0.1`).
**Certa (001):** num terminal, `;`, `&&` e `|` juntam comandos. Se algo de fora entrasse, `git status; del arquivo` rodaria o `del` também (**injeção de comando**). Sem terminal, o `ProcessBuilder` passa cada argumento separado: o `;` é só texto. → **Rever com o José.**
