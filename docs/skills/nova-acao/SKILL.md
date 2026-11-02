---
name: nova-acao
description: Checklist para criar um braço Java novo no Jarvis (endpoint na allowlist, @Valid, pasta permitida, token secreto, log com cliente=, teste JUnit). Use sempre que for criar uma ação nova em java/bracos.
disable-model-invocation: true
argument-hint: "[nome da ação, ex.: daily]"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(./mvnw test:*)
---

<!-- 📝 RASCUNHO escrito pela 001 em 02/11/2026. O José revisa e copia para .claude/skills/nova-acao/SKILL.md. -->

# 🦾 /nova-acao — novo braço do Jarvis

Crie a ação **$ARGUMENTS** em `java/bracos`, seguindo o `CLAUDE.md` (Modo Arquiteto, Java 21, Spring Boot 4.1.2).

## 0. Antes de construir
- [ ] Existe um ticket ou spec da ação (`docs/tickets/` ou `docs/specs/`)? Se não existir, **pare** e peça um ao Maestro.
- [ ] Mostre o plano curto (arquivos, método HTTP, se lê ou escreve) e **espere o OK do José**.
- [ ] **Nunca** leia o `.env`. **Nunca** use `ProcessBuilder` ou `Runtime.exec` (comando livre).

## 1. Endpoint na allowlist
- [ ] Pasta própria: `acoes/<nome>/` com `<Nome>Controller`, `<Nome>Request` (record) e `<Nome>Service`.
- [ ] Rota fixa `/acoes/<nome>`. **Só lê** → `GET`. **Escreve** → `POST`.
- [ ] Nada de rota genérica (ex.: `/acoes/{qualquer}`). Cada ação é escrita à mão: **é isso que torna a lista uma allowlist**.

## 2. Validar a entrada (`@Valid`)
- [ ] `spring-boot-starter-validation` no `pom.xml`. Se ainda não estiver, apresente antes (oficial do Spring, sem custo) e adicione uma vez só.
- [ ] Record com limites:
  ```java
  public record DailyRequest(
      @NotBlank @Size(max = 2000) String texto
  ) {}
  ```
- [ ] Controller com `@Valid @RequestBody DailyRequest pedido`.
- [ ] Erro de validação → **400 com mensagem clara**, sem stack trace (`@RestControllerAdvice`).

## 3. Pasta permitida (anti Path Traversal)
- [ ] A pasta vem da **configuração** (`application.properties`, ex.: `jarvis.pastas.daily=...`), **nunca do pedido**.
- [ ] O **nome do arquivo é gerado pelo servidor** (ex.: `2026-11-03.md`), nunca vem do cliente.
- [ ] Confira sempre:
  ```java
  Path base = Path.of(pastaDaily).toRealPath();
  Path alvo = base.resolve(nomeArquivo).normalize();
  if (!alvo.startsWith(base)) {
      throw new IllegalArgumentException("caminho fora da pasta permitida");
  }
  ```

## 4. Token secreto (só ações que escrevem)
- [ ] Cabeçalho `X-Jarvis-Token`, comparado com a variável de ambiente `JARVIS_TOKEN`. O `.env` é carregado pelo inicializador; **o agente nunca o lê**.
- [ ] Compare em tempo constante: `MessageDigest.isEqual(recebido.getBytes(UTF_8), esperado.getBytes(UTF_8))`. Com `equals` comum, o tempo de resposta pode vazar o token aos poucos (*timing attack*).
- [ ] Sem token ou token errado → **401**. Variável não configurada → **recusa tudo** (falha fechada: na dúvida, nega).
- [ ] Faça o filtro uma vez só (`HandlerInterceptor`) e reaproveite nas próximas ações.

## 5. Log de auditoria
- [ ] Cabeçalho `X-Jarvis-Cliente`, aceito só se estiver na lista (`python`, `central`). Fora dela, vira `desconhecido`. Serve **para auditoria, não para segurança**.
- [ ] Uma linha por pedido: `log.info("cliente={} acao={} resultado={}", cliente, "<nome>", "ok|erro|negado");`
- [ ] **Nunca** logar token, texto do usuário ou caminho completo.

## 6. Testes JUnit (`@WebMvcTest` + `MockMvc`)
- [ ] ✅ Caminho feliz → 200 e o formato da resposta.
- [ ] 🚫 Entrada vazia ou gigante → 400.
- [ ] 🔒 Sem token → 401 · token errado → 401 (se escreve).
- [ ] 🗂️ Path Traversal (`../`) → recusado (se mexe com arquivo).
- [ ] 📝 Log com `cliente= acao= resultado=` (via `OutputCaptureExtension`).
- [ ] Token de teste fixo via `@TestPropertySource`. Nunca o token real.
- [ ] Rode `./mvnw test` → **BUILD SUCCESS**.

## 7. Fechar
- [ ] Nova linha na tabela de ações do `java/README.md`.
- [ ] Rode `/banca java/bracos/src`. Só está pronto se as **5 personas aprovarem**.
- [ ] `git status` → `git add` arquivo por arquivo → commit em português ("Adiciona ação <nome>").
- [ ] **1 pergunta** para o José explicar com as palavras dele.
