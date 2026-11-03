# 🧑‍⚖️ Pré-revisão dos rascunhos — visão do Claude Code

- **Data:** 02/11/2026
- **Revisor:** Claude Code (regras da `/banca` + `CLAUDE.md`), só leitura
- **Alvo:** 11 agentes em `docs/agents/` + `docs/skills/nova-acao/SKILL.md`
- **Resultado:** 6 aprovam · 6 precisam de ajuste · 0 para cortar
- ⚠️ **Autorrevisão:** os rascunhos foram escritos por mim mesmo (Claude Code). Comparar com a revisão da 001 em `2026-11-02-rascunhos.md`.

## 🔴 Problemas que afetam vários arquivos

**1. Um subagente não consegue parar e esperar o OK.**
Ele roda de uma vez e devolve o resultado, sem conversar no meio. Rascunhos afetados:
- `jarvis-construtor.md:15`, `jarvis-testador.md:14` e `remotion-dev.md:14`: "espere o OK".
- `game-dev.md:14`: "Antes de tudo, pergunte".
- `game-dev.md:16`: "Espere o 'funcionou'".

**O que dá errado:** o agente "imagina" o OK e constrói tudo, ou para no plano sem avisar. Nos dois casos o Modo Arquiteto se perde.
**Conserto:** construtor, testador e remotion-dev ganham duas etapas. Na 1ª chamada devolvem só o plano e param; na 2ª, com "plano aprovado", constroem. O `game-dev` vira uma **skill** `/game-dev`, que roda na conversa principal e consegue esperar o José.

**2. O construtor não consegue usar a `/nova-acao`.**
- `SKILL.md:4` tem `disable-model-invocation: true`: só o José chama a skill, pelo `/`.
- Mesmo assim, `jarvis-construtor.md:16` e `maestro.md:43` dizem para o construtor "seguir a skill".
- A lista `tools:` do construtor também não inclui a ferramenta de skill.

**O que dá errado:** o construtor ignora o checklist de segurança sem avisar ninguém.
**Conserto:** o construtor lê o arquivo `.claude/skills/nova-acao/SKILL.md` com a ferramenta Read. A trava `disable-model-invocation` fica como está.

## Revisão arquivo por arquivo

| # | Rascunho | Menor privilégio? | Risco / problema (prova) | Melhorar | Veredito |
|---|---|---|---|---|---|
| 1 | `jarvis-hacker` | ✅ Read, Grep, Glob | Nenhum grave | 🟢 Incluir "confere o cabeçalho `Host`?" (DNS rebinding) e "tem `@CrossOrigin(\"*\")`?" | ✅ Aprovar |
| 2 | `jarvis-testador` | ✅ Bash para rodar teste | 🔴 Problema 1 (l.14) · 🟡 l.18: `@TestPropertySource` não troca o token se ele vier da variável de ambiente (`SKILL.md:49`) | Etapas plano e construção · alinhar o token com a `/nova-acao` | 🟡 Ajustar |
| 3 | `jarvis-construtor` | ✅ Precisa de tudo | 🔴 Problemas 1 e 2 (l.15-16) | Etapas plano e construção · ler o arquivo da skill · atualizar o Status do ticket | 🟡 Ajustar |
| 4 | `jarvis-depurador` | 🟡 Bash só para ler | l.31 usa `netstat`, que não está na lista de comandos permitidos | Pôr `netstat -ano` na lista ou tirar do exemplo | ✅ Aprovar (leve) |
| 5 | `jarvis-escritor` | ✅ Sem Bash | "Só escreve em docs" é regra escrita, sem trava técnica (aceitável) | — | ✅ Aprovar |
| 6 | `roteirista-video` | ✅ Read, Glob, Write | Regra boa contra citação inventada | 🟢 Checklist: marcar como "para crianças" no YouTube · traduções da Bíblia têm direitos autorais, então só citação curta | ✅ Aprovar |
| 7 | `remotion-dev` | 🟡 Precisa de Bash | 🔴 Problema 1 · 🟡 l.19/34: `npx` baixa e roda um pacote se ele não existir (um erro de digitação pode rodar pacote malicioso) · 🟡 a confirmar: download de navegador na 1ª renderização | Trocar `npx` por scripts do `package.json` · apresentar os downloads antes | 🟡 Ajustar |
| 8 | `game-designer` | ✅ (haiku) | Nenhum | — | ✅ Aprovar |
| 9 | `game-dev` | ✅ Sem Bash | 🔴 Problema 1 (l.14, 16): ele é todo feito de conversa | Virar a skill `/game-dev` | 🟡 Ajustar |
| 10 | `mentor-java` | ✅ Só lê | Nenhum | — | ✅ Aprovar |
| 11 | `maestro` | ✅ (haiku) | 🟡 Problema 2 (l.36, 43) · 🟢 l.73 diz `002`, mas ainda não existe nenhum ticket · 🟢 não diz quem muda o Status | Corrigir o exemplo · o construtor atualiza o Status, o Maestro só cria | 🟡 Ajustar |
| 12 | `/nova-acao` | 🟡 l.6: Write e Edit no `allowed-tools` liberam escrever **sem pedir permissão** | Ver abaixo | Deixar só `Read, Grep, Glob, Bash(./mvnw test:*)` | 🟡 Ajustar |

### Detalhes da `/nova-acao`
- 🔴 **Token (l.49):** usar a propriedade `jarvis.token=${JARVIS_TOKEN:}` em vez de `System.getenv`, para os testes conseguirem trocar o valor.
- 🔴 **Bloquear por padrão (l.52):** o filtro vale para todo `POST /acoes/**`. Se uma ação nova esquecer de se registrar, ela não fica aberta.
- 🟡 **Falta de configuração (l.51):** responder 503 ("token não configurado"), e não 401. Continua negando e fica mais fácil descobrir o problema.
- 🟡 **Pasta (l.41):** `toRealPath()` dá erro se a pasta não existir. Conferir a pasta ao ligar a API.
- 🟡 **Faltando:** trava contra duas escritas ao mesmo tempo no mesmo arquivo.
- 🟡 **Faltando:** "nunca `@CrossOrigin(\"*\")`". Na Central, liberar só a origem `http://127.0.0.1:<porta>`.
- 🟢 **Teste de `../` (l.63):** testar a trava direto no Service, porque o nome do arquivo é gerado pelo servidor.
- ✅ **`X-Jarvis-Cliente` (l.55):** pode ser falsificado, e a skill já diz que serve só para auditoria. Está certo.

## 🦾 Plano do braço `daily` (sem código)

**O que faz:** acrescenta `- HH:mm <texto>` na nota diária de hoje do Obsidian.
**Fora desta versão:** ler a nota (`GET`), apagar, editar linhas antigas e o lado Python (Etapa 3).

**Decisões do José:**
1. Caminho da pasta da daily. Proposta: guardar no `.env` como `JARVIS_PASTA_DAILY`, porque tem o nome de usuário.
2. Nome do arquivo (`AAAA-MM-DD.md`?).
3. Onde a linha entra: no fim da nota ou embaixo de `## Jarvis`.

**Ticket 001: segurança base**
- `pom.xml`: + `spring-boot-starter-validation`.
- `application.properties`: + `jarvis.token=${JARVIS_TOKEN:}` e `jarvis.pastas.daily=${JARVIS_PASTA_DAILY:}`.
- `seguranca/TokenInterceptor` + `WebConfig`: todo `POST /acoes/**` exige o token (tempo constante) → 401 se faltar ou estiver errado, 503 se não estiver configurado.
- `erros/ErrosHandler`: erro de validação → 400 claro, sem stack trace.
- `auditoria/Cliente`: aceita só `python` ou `central`; o resto vira `desconhecido`.
- Testes: sem token 401 · token errado 401 · certo passa · sem configuração 503.

**Ticket 002: ação `daily`**
- `DailyRequest`: record com `@NotBlank @Size(max = 2000)`.
- `DailyService`: nome do arquivo pela data de Brasília · `normalize()` + `startsWith` · troca quebras de linha por espaço · UTF-8, acrescentando ao fim · escrita com trava.
- `DailyController`: `POST /acoes/daily` com `@Valid`. Resposta `{"arquivo":"AAAA-MM-DD.md","resultado":"ok"}` (sem o caminho completo) · log `cliente= acao=daily resultado=`.
- Testes do Controller: 200 · vazio 400 · gigante 400 · sem token 401 · log capturado.
- Testes do Service (`@TempDir`): cria a nota · acrescenta sem apagar · acento ok · quebra de linha removida · fora da pasta recusado · 2 escritas ao mesmo tempo sem misturar.
- `java/README.md`: nova linha na tabela de ações e o token passa para "Já tem".

**Ordem:** construtor → testador → `jarvis-hacker` → `/banca` → escritor.
**Pronto quando:** `./mvnw test` passa, a `/banca` aprova as 5 personas e o teste manual com `curl` (usando o token) mostra a linha na nota.

## 🎤 Pergunta para o José
> Por que o filtro do token vale para *todo* `POST` em `/acoes/**`, em vez de cada ação nova se registrar nele? Que erro humano isso evita?

**Resposta do José:** _(preencher)_
