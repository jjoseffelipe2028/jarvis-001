# 002 — Ação `daily` (primeiro braço que escreve)
**Status:** 🏁 pronto (2026-11-08)
**Criado:** 2026-11-08 pela 001 · plano aprovado pelo José em 2026-11-08

## O que faz
`POST /acoes/daily` com `{"texto": "..."}` → acrescenta `- HH:mm texto` no fim de `<JARVIS_PASTA_DAILY>/AAAA-MM-DD.md` (data de Brasília).
Resposta: `{"arquivo":"2026-11-08.md","resultado":"ok"}` (só o nome, nunca o caminho).

## Decisão do José
- Pasta permitida: `C:\Users\jjose\OneDrive\Documentos\Obsidian Vault\daily`. O José coloca em `JARVIS_PASTA_DAILY` no `.env` (a IA não lê nem escreve o `.env`).

## Escopo
**Entrou:**
- `acoes/daily/DailyRequest` (texto `@NotBlank @Size(max = 2000)`), `DailyService`, `DailyController`.
- `erros/AcaoIndisponivelException` (503) e `erros/EntradaInvalidaException` (400); `ErrosHandler` sem caminho nem stack trace.
- `seguranca/HostInterceptor` (pendente do Ticket 001): só `localhost`/`127.0.0.1` → contra DNS rebinding, em GET e POST.
- Teste de contrato `RotasPostContratoTest` (pendente do 001): toda rota POST sem token → 401.
- Teste `LogSemDadoCruTest` (pendente do 001): falha se algum `log.*` usar dado de fora sem `LogSeguro.limpar()`.

**Não entrou:** `pendencias`, `git-status`, lado Python, Spring Security.

## 🔒 Segurança
| Risco | Trava |
|---|---|
| Escolher arquivo fora da pasta (Path Traversal) | Pasta vem do `.env`; nome gerado pelo servidor; `normalize()` + `startsWith`; link simbólico recusado |
| Injetar linhas falsas no daily | Quebras de linha e caracteres de controle viram espaço |
| Duas escritas ao mesmo tempo misturam texto | `ReentrantLock` em volta da escrita |
| Pasta não configurada | 503, nada é escrito (falha fechada) |
| Vazar o caminho (mostra o usuário do Windows) | Resposta e log só com o nome do arquivo |
| Texto do usuário no log | Log só tem `cliente= acao=daily resultado=` |
| Site malicioso chamando a API (DNS rebinding) | `HostInterceptor` → 403 |

## Evidências
- `./mvnw test`: **87 testes, 0 falhas, 1 pulado** (eram 37). O pulado é o de link simbólico: no Windows exige modo desenvolvedor; roda no GitHub Actions (Linux).
- Revisão `jarvis-hacker` (2026-11-08): **aprova com ressalvas** → todos os 8 achados tratados:
  1. 🟡 Markdown/HTML ativo na nota (imagem remota vaza IP, DataviewJS `$=`, Templater `<% %>`) → `desarmar()` põe `\` antes de `` \ ` * _ [ ] < > ! $ % { } | ``.
  2. 🟢 TOCTOU entre conferir e escrever → arquivo aberto uma vez só, com `NOFOLLOW_LINKS`. (Hard link no Windows continua fora: exige acesso local à pasta.)
  3. 🟢 Token só em POST → agora todo método fora de GET/HEAD/OPTIONS exige token; contrato testa POST/PUT/PATCH/DELETE.
  4. 🟢 Invisíveis (U+202E etc.) → `\p{Cf}` removido (menos U+200D, dos emojis).
  5. 🟢 Nota sem limite → acima de 1 MB: 503 "daily cheio".
  6. 🟢 Detector de log cego → pega objeto inteiro (`pedido`, `request`) e documenta o limite.
  7. ⚪ Surrogate solto → vira espaço (`\p{Cs}`), não dá mais 500.
  8. ⚪ Faltavam testes → link simbólico e `X-Forwarded-Host`.
- Commit: _(preencher)_
- Teste manual com o Obsidian aberto (José, 2026-11-08): 2 chamadas com `curl` → as 2 linhas ficaram na nota (5521 → 5560 bytes, o OneDrive não desfez). A 2ª (`![](http://x)`) gravou `\!\[\](http://x)`: aparece como texto, não busca nada. ✅

## 🎤 Pergunta para o José
> Por que o **nome do arquivo** é gerado pelo servidor e não vem no pedido? Que ataque isso evita?

**Resposta do José:** "porque o servidor que escolhe" (parcial).
**Completa (001):** se o nome viesse no pedido, um atacante mandaria `../../algo` e escreveria fora da pasta (**Path Traversal**). Com o servidor gerando o nome (`AAAA-MM-DD.md`), quem pede não controla **onde** grava.
