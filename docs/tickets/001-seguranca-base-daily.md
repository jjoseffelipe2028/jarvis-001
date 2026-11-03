# 001 — Segurança base do braço daily
**Status:** 🔄 em andamento

## Decisões do José (2026-11-02)
1. O token chega ao Java pelo script `java/bracos/iniciar.sh`, que o José roda no Git Bash: ele carrega o `.env` e roda `./mvnw spring-boot:run`. A IA só escreve o script. Ela **nunca lê nem cria o `.env`**.
2. A pasta do Obsidian (`jarvis.pastas.daily`) fica vazia e é decidida no Ticket 002.
3. O caminho certo da skill é `.claude/skills/nova-acao/`.
**Pedido original:** "crie o ticket da segurança base do braço daily, seguindo o 'Ticket 001' de docs/reviews/2026-11-02-rascunhos-cc.md"
**Criado:** 2026-11-02 pelo Maestro

## O que fazer
Montar as travas de segurança da API Java antes de qualquer ação de escrita: token secreto obrigatório em todo `POST /acoes/**`, validação de entrada (`@Valid`), erro limpo sem stack trace e auditoria do cliente. Nenhuma ação nova entra neste ticket: ele só prepara o terreno para o `daily` (Ticket 002).

## Escopo
**Entra:**
- `java/bracos/pom.xml`: adicionar `spring-boot-starter-validation`.
- `application.properties`: `jarvis.token=${JARVIS_TOKEN:}` e `jarvis.pastas.daily=${JARVIS_PASTA_DAILY:}` (valor vazio por padrão).
- `seguranca/TokenInterceptor` + `WebConfig`: todo `POST /acoes/**` exige o cabeçalho com o token, comparado em tempo constante. Sem token ou token errado: 401. Token não configurado: 503. Bloqueio por padrão: ação nova que esquecer de se registrar continua negada.
- `erros/ErrosHandler`: erro de validação vira 400 com mensagem curta, sem stack trace.
- `auditoria/Cliente`: aceita só `python` ou `central` no cabeçalho `X-Jarvis-Cliente`; qualquer outro valor vira `desconhecido`. Serve só para log, não para decisão de segurança.
- Testes JUnit das regras acima.
- `java/bracos/iniciar.sh` (novo): carrega o `.env` (na mesma pasta ou em um caminho fixo combinado no plano) com `set -a; . ./.env; set +a` e roda `./mvnw spring-boot:run`. Se o `.env` não existir, para com uma mensagem clara e **não cria** o arquivo. Nunca imprime o valor do token. O script não lê, não cria e não mostra o `.env` durante o desenvolvimento.
- Conferir que `.env` está no `.gitignore`.

**NÃO entra:**
- Nenhuma ação (`daily`, `pendencias`, `git-status`): isso é o Ticket 002.
- Caminho real da pasta do Obsidian: a propriedade fica vazia até o José decidir.
- Qualquer valor real de token: quem gera e guarda o token é o José, no `.env` (que a IA não lê).
- Lado Python, Central web, `GET`, `@CrossOrigin("*")` (proibido), liberação de origem.

## Quem faz, em que ordem
| # | Quem | Faz o quê | Entrega | Custo |
|---|---|---|---|---|
| 1 | `jarvis-construtor` (1ª chamada) | Lê `CLAUDE.md` e este ticket; faz o **plano** e para | Plano curto, sem código | 💰💰 sonnet |
| — | **José aprova o plano** | — | "plano aprovado" | — |
| 2 | `jarvis-construtor` (2ª chamada) | Constrói. Lê `.claude/skills/nova-acao/SKILL.md` com a ferramenta Read (não invoca a skill). Atualiza o Status deste ticket para 🔄 e depois 🏁 | Código + `./mvnw test` verde | 💰💰 sonnet |
| 3 | `jarvis-testador` | Escreve/revisa os testes das regras de segurança. O token nos testes vem de `@TestPropertySource` ou propriedade, não de variável de ambiente | Testes passando | 💰💰 sonnet |
| 4 | `jarvis-hacker` | Revisão: CSRF/token, cabeçalho `Host` (DNS rebinding), `@CrossOrigin`, comparação de token, vazamento de stack trace | Lista de achados (ou "nenhum") | 💰💰 sonnet |
| 5 | `mentor-java` | Faz 1 pergunta de entrevista para o José explicar com as palavras dele (ex.: "Por que o filtro vale para todo `POST /acoes/**`?") | Pergunta, sem código | 💰💰 sonnet |

Fora deste ticket (depois do Ticket 002): `/banca` e `jarvis-escritor` (README do `java/`).

## Arquivos envolvidos
- `java/bracos/pom.xml`
- `java/bracos/iniciar.sh` (novo)
- `.gitignore` (só conferir/adicionar `.env`)
- `java/bracos/src/main/resources/application.properties`
- `java/bracos/src/main/java/.../seguranca/TokenInterceptor.java` (novo)
- `java/bracos/src/main/java/.../seguranca/WebConfig.java` (novo)
- `java/bracos/src/main/java/.../erros/ErrosHandler.java` (novo)
- `java/bracos/src/main/java/.../auditoria/Cliente.java` (novo)
- `java/bracos/src/test/java/.../seguranca/` (testes novos)
- `docs/tickets/001-seguranca-base-daily.md` (só o Status, pelo construtor)

Leitura apenas: `CLAUDE.md`, `.claude/skills/nova-acao/SKILL.md`. **Não ler `.env`.**

## 🔒 Segurança (o que pode dar errado e qual trava cobre)
| Risco | Trava |
|---|---|
| Página maliciosa no navegador chama a API (CSRF) | Token obrigatório no cabeçalho em todo `POST /acoes/**` |
| Adivinhar o token caractere por caractere (ataque de tempo) | Comparação em tempo constante |
| Token escrito no código ou no Git | Token só por variável `JARVIS_TOKEN` (de `.env` fora do Git); nenhum valor no código |
| API sem token configurada fica aberta | Sem configuração: 503 e nada é executado |
| Ação nova esquecida fica sem proteção | Filtro vale para todo `POST /acoes/**` (bloqueio por padrão) |
| Entrada inválida ou gigante | `@Valid` + `spring-boot-starter-validation`, resposta 400 |
| Stack trace vaza detalhes internos | `ErrosHandler` responde mensagem curta |
| Cliente falso no log | `X-Jarvis-Cliente` aceito só como `python`/`central`; é só para auditoria |
| Acesso da rede | Servidor só em `127.0.0.1:8080` (já configurado; conferir que não mudou) |

## Pronto quando
- [ ] Plano aprovado pelo José antes de qualquer código.
- [ ] `./mvnw test` passa (verde).
- [ ] Sem token: `POST /acoes/**` responde 401.
- [ ] Token errado: 401.
- [ ] Token certo: passa (200 em rota de teste, ou o filtro deixa seguir).
- [ ] Token não configurado (`JARVIS_TOKEN` vazio): 503.
- [ ] Erro de validação: 400 com mensagem curta, sem stack trace.
- [ ] Cliente fora da lista vira `desconhecido` no log.
- [ ] Nenhum valor de token no código, nos testes ou no Git.
- [ ] `iniciar.sh` existe, carrega o `.env` sem imprimir o token e falha com mensagem clara se o `.env` não existir.
- [ ] `.env` está no `.gitignore`.
- [ ] `jarvis-hacker` sem achados graves (ou achados corrigidos).
- [ ] José responde a pergunta do `mentor-java` com as palavras dele.
- [ ] Status deste arquivo: 🏁 pronto.

## Custo estimado
🟢 grátis: 0 passos · 💰 haiku: 0 · 💰💰 sonnet: 5 (construtor 2×, testador, hacker, mentor-java) · 💰💰💰 opus: 0
