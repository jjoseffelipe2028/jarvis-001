# 🛠️ Oficina Turbo — análise de agentes e ferramentas

_Escrito pela 001 (Claude Code) em 02/11/2026 para o José revisar. Nada aqui foi instalado ou copiado para `.claude/`._

---

## Sessão A — os 19 agentes do oh-my-claudecode (v5.7.21)

Fonte: `~/.claude/plugins/cache/omc/oh-my-claudecode/5.7.21/agents/*.md` (só leitura).

**Legenda de custo:** 💰 haiku (barato) · 💰💰 sonnet (médio) · 💰💰💰 opus (caro, gasta limite rápido).
**Legenda de risco:** 🟢 só lê (sem Write/Edit) · 🟡 lê + Bash · 🔴 todas as ferramentas (escreve, apaga, roda comando).

> ⚠️ **Achado importante:** os agentes "só leitura" do plugin bloqueiam apenas `Write` e `Edit`, mas **continuam com `Bash`**. Pelo Bash dá para escrever arquivo (`echo > arquivo`), apagar e instalar. Então eles **não são** realmente só leitura. Por isso as versões Jarvis abaixo usam a lista `tools:` (só o que pode), e não `disallowedTools:` (só o que não pode). Isso é **menor privilégio**: dar ao agente só as ferramentas de que ele precisa.

| # | Agente | O que faz | Modelo (custo) | Ferramentas (risco) | Serve ao Jarvis? | Banca? |
|---|---|---|---|---|---|---|
| 1 | analyst | Analisa requisitos antes de planejar | opus 💰💰💰 | 🟡 sem Write/Edit | Pouco: o Maestro e o Modo Arquiteto já fazem isso | Complementa (antes) |
| 2 | architect | Conselheiro de arquitetura e depuração | opus 💰💰💰 | 🟡 | Às vezes, em decisões grandes (Etapa 4) | Complementa |
| 3 | code-reviewer | Revisão de código com severidade | opus 💰💰💰 | 🟡 | Sim, mas a Banca já cobre | **Substitui** (redundante, caro) |
| 4 | code-simplifier | Simplifica código e **edita sozinho** | opus 💰💰💰 | 🔴 | Não | Concorre com a Banca |
| 5 | critic | Crítica de planos e de código, multi-perspectiva | opus 💰💰💰 | 🟡 | Pouco | **Substitui** (a Banca já é multi-perspectiva) |
| 6 | debugger | Acha a causa raiz de erros e de build quebrado | sonnet 💰💰 | 🔴 (e **instala dependência sozinho**, linha 73) | **Sim**: erros do Spring são comuns | Complementa (depois do erro) |
| 7 | designer | Cria interfaces bonitas (UI/UX) | sonnet 💰💰 | 🔴 | Sim, na Etapa 4 (Central web) | Complementa |
| 8 | document-specialist | Pesquisa documentação externa | sonnet 💰💰 | 🟡 + web | Sim (Spring Boot 4 é novo) | Complementa |
| 9 | executor | Implementa uma tarefa | sonnet 💰💰 | 🔴 | **Sim**: o construtor do dia a dia | É revisado pela Banca |
| 10 | explore | Busca arquivos e padrões no código | haiku 💰 | 🟡 | **Sim**: barato, usar como está | Complementa |
| 11 | git-master | Commits atômicos, **rebase**, histórico | sonnet 💰💰 | 🔴 | ❌ Não (ver abaixo) | — |
| 12 | planner | Planejamento com entrevista | opus 💰💰💰 | 🔴 | Pouco: o Maestro faz isso mais barato | — |
| 13 | qa-tester | Testa a app rodando, usando **tmux** | sonnet 💰💰 | 🔴 | ❌ Não (tmux não existe no Windows) | — |
| 14 | scientist | Análise de dados num Python isolado | sonnet 💰💰 | 🟡 | Não por enquanto | — |
| 15 | security-reviewer | OWASP Top 10, segredos, padrões inseguros | opus 💰💰💰 | 🟡 | **Sim**: a prioridade do projeto | Complementa (aprofunda a persona Hacker) |
| 16 | test-engineer | Estratégia de testes, TDD, testes instáveis | sonnet 💰💰 | 🔴 | **Sim**: JUnit e pytest | Complementa (resolve a persona Sênior Chato) |
| 17 | tracer | Investiga causa com hipóteses e evidências | sonnet 💰💰 | 🔴 | Pouco: o debugger basta | — |
| 18 | verifier | Confere se "está pronto" com provas | sonnet 💰💰 | 🟡 | Às vezes | Complementa (depois da Banca) |
| 19 | writer | Documentação: README, API, comentários | haiku 💰 | 🔴 | **Sim**: barato, e os READMEs atrasaram | Resolve a persona "Eu daqui a 6 meses" |

### Os 5 escolhidos → versão Jarvis (em `docs/agents/`)

| Versão Jarvis | Baseada em | Por quê | Modelo | Ferramentas |
|---|---|---|---|---|
| `jarvis-hacker` | security-reviewer | Segurança é regra nº 1; aprofunda a persona Hacker da Banca | sonnet (em vez de opus) | Read, Grep, Glob (**sem Bash**) |
| `jarvis-testador` | test-engineer | Toda ação Java nova precisa de JUnit | sonnet | Read, Grep, Glob, Write, Edit, Bash |
| `jarvis-construtor` | executor | Constrói 1 ticket seguindo o Modo Arquiteto | sonnet | Read, Grep, Glob, Write, Edit, Bash |
| `jarvis-depurador` | debugger | Explica o erro sem instalar nada e sem editar | sonnet | Read, Grep, Glob, Bash |
| `jarvis-escritor` | writer | Mantém o README e o `docs/` em dia, em português | haiku | Read, Grep, Glob, Write, Edit (**sem Bash**) |

O `explore` (haiku, barato) serve do jeito que está e não precisa de versão Jarvis.

### ❌ Agentes do plugin que você nunca deve usar

| Agente | Por quê |
|---|---|
| **git-master** | Faz `rebase` e `--force-with-lease`, ou seja, **reescreve histórico**. O CLAUDE.md proíbe isso sem confirmação. Um rebase errado no `main` pode apagar commits que já estão no GitHub. |
| **qa-tester** | Depende do `tmux`, que não existe no Windows/Git Bash. Ele falharia, ou tentaria instalar algo para contornar. |
| **code-simplifier** | Opus (caro) com **todas as ferramentas**, e **edita o código sozinho**. Fere o Modo Arquiteto, em que você aprova antes. Para isso já existe o `/simplify`, que mostra antes. |

**Evitar** (não é proibido, mas é caro ou repetido): `critic` e `code-reviewer` (a Banca já faz isso, com 1 passada), `planner` e `analyst` (opus; o Maestro planeja mais barato) e `tracer` (o depurador basta).

**Cuidado com o `debugger` original:** a linha 73 manda "instalar dependências que faltam". Isso fere a regra "antes de qualquer pacote: o que é, quem mantém...". Use o `jarvis-depurador`.

> 🔒 **Limite das versões Jarvis:** a lista `tools:` não consegue limitar **quais comandos** o Bash roda. A trava de verdade é o hook `guarda` mais as permissões do `settings.json`. As regras escritas no agente são a 2ª camada (defesa em profundidade: várias travas, uma atrás da outra).

---

## 📍 Onde paramos (02/11/2026)

- ✅ **Sessão A:** pronta (análise acima + 5 versões Jarvis em `docs/agents/`, commit `c153aa3`).
- ⏸️ **Sessão B:** parada no começo, a pedido do José. Nenhuma pesquisa foi feita e nada foi instalado.
- ⬜ **Sessão C** (5 agentes novos) e ⬜ **Sessão D** (Maestro + skill `/nova-acao`): não começaram.

**Para retomar:** pesquisar Trail of Bits, Superpowers, Spec Kit e Serena (só sites oficiais), depois os motores de jogo (Godot 4, Unity, Roblox Studio, Unreal).
