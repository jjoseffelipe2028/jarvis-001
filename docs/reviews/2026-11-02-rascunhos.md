# 🧑‍⚖️ Pré-revisão dos rascunhos — 02/11/2026

_Feita pela 001 (Claude, chat), só lendo os arquivos. Nenhum arquivo foi alterado. Base: regras da `/banca` e do `CLAUDE.md`._
_Alvo: 11 agentes em `docs/agents/` + skill `docs/skills/nova-acao/SKILL.md`._

## 1. Veredito por rascunho

| # | Rascunho | Ferramentas | Menor privilégio? | Veredito | Motivo (1 linha) |
|---|---|---|---|---|---|
| 1 | `jarvis-hacker` | Read, Grep, Glob | ✅ só lê | ✅ **aprovar** | Checklist completo (rede, allowlist, @Valid, Path Traversal, token em tempo constante) |
| 2 | `mentor-java` | Read, Grep, Glob | ✅ só lê | ✅ **aprovar** | Máx. 3 pontos + pergunta de entrevista: ótimo para estudar |
| 3 | `jarvis-escritor` | Read, Grep, Glob, Write, Edit | ✅ sem Bash | ✅ **aprovar** | Só mexe em README/docs; documenta o nome da variável, nunca o valor |
| 4 | `maestro` | Read, Glob, Write | ✅ sem Bash/Edit | ✅ **aprovar** | Não executa; só escreve ticket; recusa pedido perigoso |
| 5 | `game-designer` | Read, Glob, Write | ✅ sem Bash | ✅ **aprovar** | Haiku (barato); protótipo primeiro; ideia original |
| 6 | `game-dev` | Read, Grep, Glob, Write, Edit | ✅ **sem Bash** | ✅ **aprovar** | Quem roda o motor é o José; MCP de motor só se pedir |
| 7 | `roteirista-video` | Read, Glob, Write | ✅ sem Bash | ✅ **aprovar** | Regra "nunca inventar fala de Jesus" + `⚠️ CONFERIR` |
| 8 | `jarvis-testador` | + Write, Edit, Bash | 🟡 Bash amplo | ✅ **aprovar** | Escreve só em `src/test/`; Bash limitado por regra escrita |
| 9 | `jarvis-construtor` | + Write, Edit, Bash | 🟡 Bash amplo | ✅ **aprovar** | Precisa do Bash para construir; plano → OK antes |
| 10 | `jarvis-depurador` | Read, Grep, Glob, Bash | 🟡 Bash amplo | 🔧 **ajustar** | Exemplo usa `findstr`, que **não existe no Git Bash** (já deu erro antes) → trocar por `grep` |
| 11 | `remotion-dev` | + Write, Edit, **Bash + npm** | 🔴 o mais arriscado | ⏸️ **ajustar e deixar para depois** | É o único que **instala pacote** (npm = risco de supply chain) |
| 12 | `/nova-acao` | Read, Grep, Glob, Write, Edit, `Bash(./mvnw test:*)` | ✅ Bash só para testes | ✅ **aprovar** (+2 ajustes leves) | Checklist de segurança muito bom |

**Resumo:** 9 aprovados · 2 para ajustar · 0 cortados.

## 2. Riscos e ajustes

### 🔴 `remotion-dev` (deixar para quando for fazer vídeo)
- `npm install` e `npx` **rodam código de terceiros** no PC (scripts de instalação). É o mesmo risco do caso `httpx2` no pip.
- Ajustar antes de usar:
  - Instalar **só** `remotion` e `@remotion/cli`, com **versão fixa** e `package-lock.json` no Git.
  - Pente-fino no `package.json` antes do primeiro `npm install` (pacotes extras = parar).
  - Conferir a licença em remotion.dev/license na hora.

### 🟡 Os 3 com Bash amplo (construtor, testador, depurador)
- A lista `tools:` **não consegue** limitar quais comandos do Bash eles usam. A trava de verdade é a **guarda** + o **modo manual** + o `deny` do `settings.json`. Isso já está de pé. ✅
- `jarvis-depurador`: trocar o exemplo `netstat -ano | findstr :8080` por `netstat -ano | grep :8080`.

### 🟢 `/nova-acao` (2 ajustes leves)
1. **Caminho da pasta do Obsidian:** em vez de escrever o caminho real no `application.properties` (que vai para o GitHub e mostra seu usuário do Windows), usar `jarvis.pastas.daily=${JARVIS_PASTA_DAILY}` e pôr o valor no `.env`.
2. **Obsidian e OneDrive:** já aconteceu de o Obsidian aberto **desfazer** uma escrita. Adicionar no checklist: "testar a escrita com o Obsidian aberto e conferir o tamanho do arquivo depois".
- 👍 Ponto forte: `toRealPath()` dá erro se a pasta não existir, então ele **falha fechado** (na dúvida, nega).

### Como instalar os aprovados (só o José, no Git Bash)
```
cp docs/agents/jarvis-hacker.md .claude/agents/
```
Um arquivo por vez (criar a pasta antes com `mkdir -p .claude/agents`). Sugestão de ordem: `jarvis-hacker`, `mentor-java`, `maestro`, `jarvis-construtor`, `jarvis-testador`. Os de vídeo e jogo podem esperar.

## 3. Plano do braço `daily` (só plano, sem código)

**Objetivo:** o Jarvis escreve no diário do Obsidian (`daily/AAAA-MM-DD.md`) o que o José falar.

| Item | Decisão |
|---|---|
| Rota | `POST /acoes/daily` (escreve → exige token) |
| Corpo | `{ "texto": "..." }` → `DailyRequest(@NotBlank @Size(max = 2000) String texto)` |
| Arquivo | Nome **gerado pelo servidor**: data de hoje no fuso `America/Sao_Paulo` → `2026-11-03.md` |
| Como escreve | **Só acrescenta no final** (append), nunca apaga nem sobrescreve |
| Pasta | `${JARVIS_PASTA_DAILY}` (no `.env`) + conferência `normalize()` / `startsWith(base)` |
| Token | Cabeçalho `X-Jarvis-Token` = `JARVIS_TOKEN`, com `MessageDigest.isEqual`; sem variável → recusa tudo |
| Log | `cliente=python acao=daily resultado=ok` (sem o texto, sem o caminho) |
| Resposta | `{ "arquivo": "2026-11-03.md", "resultado": "ok" }` |

**Arquivos novos (previsão):**
- `acoes/daily/DailyController.java` · `DailyRequest.java` · `DailyService.java`
- `seguranca/TokenInterceptor.java` + `WebConfig.java` (reaproveitado nos próximos braços)
- `ErroHandler.java` (`@RestControllerAdvice`: 400 sem stack trace)
- `pom.xml`: `spring-boot-starter-validation` (apresentar antes)
- Teste: `DailyControllerTest.java` com 6 testes: feliz · vazio · gigante · sem token · token errado · log

**Ordem das sessões (cabe antes de 10/11):**
1. Sessão 1: `pom.xml` (validation) + `ErroHandler` + `TokenInterceptor` + testes do token.
2. Sessão 2: `DailyService` + `DailyController` + testes do daily.
3. Sessão 3: `jarvis-hacker` → `/banca` → README → commit + push.

## 4. Pergunta para o José
> Por que o **nome do arquivo** do daily tem que ser gerado pelo servidor, e não vir no pedido? (dica: pense em `../../`)
