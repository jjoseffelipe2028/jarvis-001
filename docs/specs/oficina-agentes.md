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

## Sessão B — pesquisa (nada foi instalado)

_Fontes: só páginas oficiais (GitHub do dono e documentação oficial), lidas em 02/11/2026. Onde a página não dizia, está escrito "não informado"._

### B1. Ferramentas para o Claude Code

| | 🛡️ Trail of Bits Skills | ⚡ Superpowers | 📋 Spec Kit | 🧭 Serena |
|---|---|---|---|---|
| **O que é** | Loja ("marketplace") de ~45 plugins de segurança e auditoria | Método de trabalho em skills: ideia → plano → TDD → revisão | CLI que cria specs, plano e tarefas antes do código | Servidor MCP que dá ao agente "visão de IDE" (achar símbolo, referências, renomear) |
| **Quem mantém** | Trail of Bits (empresa de segurança) | Jesse Vincent (obra) / Prime Radiant | GitHub (org `github`) | Oraios AI |
| **Última versão** | Sem releases; último commit em 28/10/2026 | v7.0.1 (21/10/2026) | v1.2.5 (28/10/2026) | v2.1.0 (19/10/2026) |
| **O que instala** | Plugins (skills); alguns pedem Semgrep/CodeQL | 15 skills + 1 hook | O CLI `specify` (Python, via `uv`) + comandos `/speckit-*` | O CLI `serena` + servidor MCP; **baixa language servers** na 1ª vez (Java: JDTLS + JRE 21) |
| **Hooks?** | O plugin `gh-cli` tem hook SessionStart; os outros não informado | **Sim:** `SessionStart` (roda em todo início, limpeza e compactação de sessão) | "Extension hooks" próprios (não informado se são hooks do Claude Code) | Não |
| **Manda dado para fora?** | `second-opinion` envia código ao Codex/Antigravity; o resto não informado | Só a versão (logo no "visual companion"); desliga com `SUPERPOWERS_DISABLE_TELEMETRY` | Não informado (baixa catálogos) | **Sim, telemetria leve** (versão, SO, linguagem); desliga com `SERENA_USAGE_REPORTING=false` |
| **Custo em tokens** | Só quando a skill é usada | **Fixo em toda sessão** (texto injetado no início) | Só quando o comando é usado | Esquemas das ferramentas MCP em toda sessão (tamanho não informado) |
| **Windows** | Não informado | ✅ Corrigido na v7.0.0 (precisa do Git Bash) | ✅ Tem scripts PowerShell | ✅ Funciona (config em `%USERPROFILE%\.serena\`) |
| **Licença** | CC-BY-SA-4.0 | MIT | MIT | GPL-3.0 |

**🧭 Serena escuta só em `127.0.0.1`?** ✅ **Sim, por padrão.** O MCP conversa por *stdio* (entrada e saída do próprio processo, sem porta). O painel web abre em `localhost:24282`, e a documentação diz que, por padrão, aceita conexões só do próprio PC. ⚠️ Mas o Serena tem a ferramenta `execute_shell_command` (roda comandos) e ferramentas que editam arquivos. O contexto `claude-code` desliga as que repetem as do Claude Code, e a própria documentação diz que **a única proteção total é rodar dentro de Docker**. Isso vai contra a regra "a IA nunca executa comando livre".

**🛡️ Trail of Bits: 3 skills para Java/Spring e Python**
1. **`insecure-defaults`**: acha senha fixa no código, segredo "de reserva" e configuração que falha aberta (ex.: `application.properties` perigoso). É a cara do Jarvis.
2. **`static-analysis`**: roda Semgrep e CodeQL, que analisam Java e Python. ⚠️ Precisa instalar o Semgrep e/ou o CodeQL antes (avaliar à parte).
3. **`differential-review`**: revisão de segurança **só do que mudou** (pelo git). É barata e combina com a Banca.

❌ Evitar: `second-opinion` (manda código para fora), `code-improver` (corrige sozinho, sem supervisão), `gh-cli` (hook que intercepta o WebFetch). `supply-chain-risk-auditor` não cobre Maven.

### ✅ Recomendação: **Spec Kit** (e não Superpowers)

- **Combina com o Modo Arquiteto:** especificar → planejar → tarefas, com você aprovando cada etapa. É o mesmo caminho de `docs/specs` → `docs/tickets`.
- **Sem custo fixo:** só gasta tokens quando você chama um `/speckit-*`. O Superpowers injeta texto em **toda** sessão e se diz "obrigatório, não sugestão".
- **Mantido pelo GitHub**, licença MIT, com suporte a Windows e PowerShell.
- O Superpowers usa *subagent-driven development* (vários agentes em sequência), que é caro e parecido com os modos que o CLAUDE.md proíbe.
- ⚠️ O README do Spec Kit usa o Copilot como exemplo. A integração com o Claude existe (release 1.2.0), mas o nome exato da opção tem que ser conferido em `specify init --help`.

### Ordem de instalação sugerida (uma por sessão, com sua aprovação)

| # | O quê | Comando oficial | Por quê nessa ordem |
|---|---|---|---|
| 1 | Trail of Bits → só `insecure-defaults` | `/plugin marketplace add trailofbits/skills` → `/plugin menu` (escolher só esse) | Só skill, sem binário e sem hook. É o menor risco e o maior ganho em segurança |
| 2 | Spec Kit | `uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@v1.2.5` | Versão fixa (`@v1.2.5`). Testar o `specify init` numa **pasta de teste** antes de rodar no Jarvis |
| 3 | Serena (opcional, depois) | `uv tool install -p 3.13 serena-agent` → `serena init` → `claude mcp add serena -- serena start-mcp-server --context claude-code --project "$(pwd)"` | Só vale quando o código Java crescer. Hoje são 2 classes. Antes de instalar: `SERENA_USAGE_REPORTING=false` e desligar o `execute_shell_command` |

### B2. Motores de jogo

| | 🟦 Godot 4 | ⬛ Unity 6 | 🟥 Roblox Studio | ⚪ Unreal Engine 5 |
|---|---|---|---|---|
| **Versão** | 4.7.2 (18/08/2026) | 6000.3.27f2 (30/10/2026) | Sempre a mais nova (atualiza sozinho) | ⚠️ não confirmado (site bloqueou) |
| **Linguagem** | GDScript (tipo Python), C# | C# | Luau (tipo Lua) | C++ e Blueprints (blocos visuais) |
| **Licença e custo** | **MIT, grátis, sem royalties** | Personal **grátis até US$ 200 mil/ano** de receita; Pro US$ 2.310/ano. Runtime Fee **cancelada** (set/2024) | Grátis | ⚠️ De memória, não confirmado: grátis, **5% de royalty** acima de US$ 1 milhão por jogo |
| **2D** | ⭐ Excelente (motor 2D dedicado) | Bom | Fraco (o foco é 3D) | Fraco |
| **3D pesado** | Médio | Bom | Leve e estilizado | ⭐ O melhor (gráfico realista) |
| **Multiplayer** | Tem API própria (você hospeda) | Netcode + serviços pagos por uso | ⭐ **Já vem pronto**, servidores da Roblox | Replicação embutida |
| **Publicar** | PC, web, Android, iOS (consoles via terceiros) | PC, web, mobile, AR/VR; console só no Pro | PC, Mac, mobile, Xbox, PlayStation, VR (automático) | PC, consoles, mobile |
| **Ganhar dinheiro** | Vende na Steam/itch.io (100% seu, menos a loja) | Unity Ads e compras no app | Passes, produtos, assinaturas, anúncios → **DevEx: 10.000 Robux = US$ 38**, saque mínimo de 30.000 Robux, 13+ anos | Lojas (Steam, Epic) |
| **IA / MCP oficial** | ❌ Não achei oficial (só da comunidade, então não instalar sem avaliar) | ✅ **MCP oficial + plugin oficial para Claude Code** (grátis); o Assistant interno é pago depois do teste | ✅ **MCP embutido no Studio**, com Claude Code na lista. ⚠️ Roda código Luau e faz `http_get` | ⚠️ não confirmado |
| **RTX 4050 6 GB** | ✅ Leve (pede Vulkan 1.0) | ✅ Roda bem 2D e 3D médio | ✅ Muito leve | ⚠️ Roda, mas 6 GB de VRAM é apertado para Nanite/Lumen (os gráficos mais pesados) |

**Recomendação:**
- **(a) 1º protótipo → Godot 4.7.** Grátis para sempre, leve, e o GDScript é fácil. O 2D é ótimo para aprender o ciclo "ideia → jogo jogável" em dias.
- **(b) Jogo 3D pesado → Unreal 5**, se o objetivo for gráfico realista. Mas no seu notebook (6 GB) **o Unity 6 é a escolha mais prática**: roda melhor, tem MCP oficial para o Claude Code e o C# é parecido com Java.
- **(c) Ganhar dinheiro rápido → Roblox.** Os jogadores já estão lá, o multiplayer e os servidores são de graça, e a monetização vem pronta. Atenção: o DevEx paga pouco (US$ 38 a cada 10 mil Robux) e exige 30 mil Robux para sacar.

> 🔒 Os MCPs de jogo (Unity e Roblox) deixam a IA **ler e mudar o seu projeto e rodar código** dentro do motor. Se for usar, ligue só durante a sessão e com o projeto salvo no Git.

---

## Sessão C — 5 agentes novos (em `docs/agents/`)

| Agente | Foco | Modelo | Ferramentas | Destaque de segurança |
|---|---|---|---|---|
| `roteirista-video` | Vídeo | sonnet | Read, Glob, Write | Nunca inventa fala de Jesus: cita referência e tradução, ou marca `⚠️ CONFERIR` |
| `remotion-dev` | Vídeo | sonnet | + Edit, Bash | Narração local (Piper); apresenta a licença do Remotion antes do `npm install` |
| `game-designer` | Jogo | haiku | Read, Glob, Write | Ideia original; protótipo pequeno primeiro |
| `/game-dev` (**virou skill**, em `docs/skills/game-dev/`) | Jogo | modelo da sessão | `allowed-tools: Read, Grep, Glob` (Write e Edit pedem permissão; **sem Bash**) | Pergunta o motor e espera o José a cada passo. Por isso é skill: um subagente não consegue esperar |
| `mentor-java` | Programar | sonnet | Read, Grep, Glob (**só lê**) | Ensina, não corrige pelo José |

---

## Sessão D — 🎼 o Maestro

- **`docs/agents/maestro.md`:** recebe o pedido, escolhe **quem**, em que **ordem** e com qual **custo**, e escreve um ticket em `docs/tickets/`. Não executa nada. Usa haiku, porque escolher quem faz é tarefa barata.
- **`docs/skills/nova-acao/SKILL.md`:** checklist do braço Java novo (allowlist → `@Valid` → pasta permitida → token → log `cliente=` → JUnit → Banca).

### Como o Maestro vira a Central (Etapa 4)

```
 🎙️ voz ou 🖱️ botão
        ↓
 🎼 Maestro ──► 🎫 ticket (Status: 🟡 aguardando)
                    ↓  José clica "Aprovar" na Central
                 ✅ aprovado ──► agentes trabalham ──► 🏁 pronto
                    ↑                    ↑
       a Central lê o Status      cada agente = 1 boneco
```

1. **Botões = pedidos prontos.** Cada botão da Central ("🎬 Novo vídeo", "🦾 Nova ação", "🕹️ Novo jogo", "🧑‍⚖️ Revisar código") envia um pedido em português para o Maestro, igualzinho a falar com o Jarvis.
2. **Bonecos = a tabela "O time" do Maestro.** Cada linha vira um boneco no HUD. O boneco anima conforme o **Status** do ticket em que ele está: parado (⬜), trabalhando (🔄), terminou (🏁), recusado (🔴).
3. **Os tickets são a "memória" da Central.** A Central não guarda estado próprio: ela **lê** `docs/tickets/` por uma ação nova e só de leitura na API Java (ex.: `GET /acoes/tickets`). Aprovar vira outra ação, que escreve e por isso **exige o token** (`POST /acoes/tickets/{id}/aprovar`). As duas nascem com a `/nova-acao`.
4. **Custo na tela.** Cada cartão de ticket mostra "🟢 grátis / 💰 / 💰💰". Você vê quanto do limite vai gastar **antes** de aprovar.
5. **Voz:** "Jarvis, faz um vídeo sobre perdão" → ouvido (faster-whisper) → Gemma classifica de graça → Maestro → ticket → aparece na Central para aprovar.

🔒 A Central **nunca executa direto**: o clique em "Aprovar" é sempre seu. Isso se chama *humano no circuito*: a IA propõe, a pessoa decide.

---

## 📍 Onde paramos (02/11/2026)

- ✅ **Sessão A:** pronta (commit `c153aa3`).
- ✅ **Sessão B:** pronta (commit `da01a45`; Unreal não confirmada porque o site bloqueia leitura automática).
- ✅ **Sessão C:** pronta (commit `7eba38f`).
- ✅ **Sessão D:** pronta (Maestro + `/nova-acao` + plano da Central).
- ⏳ **Falta o José:** revisar os rascunhos e copiar para `.claude/agents/` e `.claude/skills/`. Depois disso, a Fase 1 pode ser marcada como ✅ no README.
