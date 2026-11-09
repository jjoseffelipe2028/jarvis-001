# 003 — Ação `pendencias` (primeiro braço que lê o Obsidian)
**Status:** 🏁 pronto (2026-11-09)
**Criado:** 2026-11-09 pela 001 · plano aprovado pelo José em 2026-11-09

## O que faz
`GET /acoes/pendencias` 🔒 → lê os dailys dos **últimos 7 dias** em `<JARVIS_PASTA_DAILY>` e devolve as tarefas abertas (`- [ ]`). Hoje primeiro.
```json
{"pendencias":[{"dia":"2026-11-08","texto":"estudar Git"}],"total":1,"ignorados":0}
```
- `total` = quantas existem · `pendencias` = no máximo 200 · `ignorados` = notas puladas por segurança.

## Decisão do José
- **Token também no GET**: pendências são dado pessoal. Recomendação da 001, aceita.

## Escopo
**Entrou:**
- `acoes/PastaSegura` — regras da pasta permitida, agora compartilhadas por `daily` e `pendencias` (uma regra só, um lugar só).
- `acoes/pendencias/PendenciasService` + `PendenciasController`.
- `TokenInterceptor` **fechado por padrão também no GET**: só `ROTAS_PUBLICAS` (`/acoes/hora`) passa sem token.
- Contrato novo: toda rota GET fora da lista pública sem token → 401.

**Não entrou:** marcar tarefa como feita (escrita), `git-status`, lado Python.

## 🔒 Segurança
| Risco | Trava |
|---|---|
| Site malicioso lendo suas pendências | Token exigido no GET + filtro `Host` |
| Ler arquivo fora da pasta (Path Traversal) | Nada vem do pedido: 7 nomes `AAAA-MM-DD.md` gerados pelo servidor + `PastaSegura` |
| Atalho apontando para fora | Link simbólico e "não arquivo" → pulados (`ignorados`) |
| Nota gigante travando a API (DoS) | Nota > 1 MB pulada · até 200 itens · até 300 caracteres por item |
| Alterar o Obsidian sem querer | Só leitura: abre com `READ`; teste confere bytes e data iguais |
| Unicode malicioso (U+202E inverte texto) | Invisíveis viram espaço |
| Vazar a vida pessoal no log | Log só tem `cliente= acao=pendencias resultado= total= ignorados=` |
| Truque de URL furando o porteiro (`//`, `/../`, barra final) | Comparação **exata** com a lista pública: qualquer variação pede token |

## Evidências
- `./mvnw test`: **112 testes, 0 falhas, 2 pulados** (eram 87). Os pulados são de link simbólico (Windows); rodam no GitHub Actions.
- Revisão `jarvis-hacker` (2026-11-09): **✅ APROVA**, 0 🔴, 0 🟡, 4 🟢:
  1. 🟢 Truques de URL só testados no MockMvc → **feito:** `TruquesDeUrlServidorRealTest` sobe o Tomcat de verdade e tenta 10 variações (`//`, `;x=1`, `%70`, `%2F`, `/../`, maiúsculas, barra final). Nenhuma lê sem token.
  2. 🟢 Hard link não é detectado → **risco aceito:** exige alguém já escrevendo dentro da pasta do cofre.
  3. 🟢 Caracteres "em branco" (U+3164, U+2800) e marcas empilhadas passam → efeito só visual; **depois**.
  4. 🟢 Falta handler genérico de erro → **depois**: um `Exception.class` genérico transformaria 404/405 em 500; fazer com cuidado junto do Spring Security.
- Teste manual com `curl` (José, 2026-11-09), API real lendo o cofre real:
  - sem token → `{"erro":"nao autorizado"}` **401** ✅
  - com token → **200**, `total=13`, `ignorados=0`: tarefas abertas de 03, 04 e 08/11 ✅
  - Bônus: mostrou tarefas já feitas que ficaram `[ ]` no cofre (Ticket 002, Actions) → faxina no cofre.
- Commit: _(preencher)_

## 🎤 Pergunta para o José
> Por que o `GET /acoes/pendencias` pede token, se ele **só lê** e não muda nada?

**Resposta do José:** _(preencher)_
