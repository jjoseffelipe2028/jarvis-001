# Registro da dupla — ADRs do Jarvis

- **Plano:** `docs/planos/2026-11-15-adrs-do-jarvis.md` (aprovado pelo José)

## Arquivos alterados
docs/adr/README.md
docs/adr/000-modelo.md
docs/adr/001-tudo-local.md
docs/adr/002-cerebro-proprio-em-python.md
docs/adr/003-bracos-java-por-allowlist.md
docs/adr/004-pedido-assinado-contra-csrf.md
docs/adr/005-codigo-fora-do-onedrive.md
docs/adr/006-modo-arquiteto.md
docs/adr/007-dupla-001-002.md
docs/adr/008-estudio-autonomia-com-trava.md
docs/adr/009-pedido-a-002-so-com-caminhos.md
.claude/skills/adr/SKILL.md

## Testes
Só documentação. Conferido pela 001: `server.address=127.0.0.1` existe; as classes `PastaSegura`, `TokenInterceptor` e `AcoesController` existem; data do ADR 004 corrigida para 09/11 (data dos tickets 006/007).


## 002 — revisão do codigo, rodada 1 (18:55) → APROVO

### 1. Veredito
✅ **Aprovo**

Conferi o plano em [2026-11-15-adrs-do-jarvis.md](docs/planos/2026-11-15-adrs-do-jarvis.md), o registro em [2026-11-15-adrs-do-jarvis.md](docs/reviews/2026-11-15-adrs-do-jarvis.md) e os 12 arquivos criados ([README.md](docs/adr/README.md), [000-modelo.md](docs/adr/000-modelo.md), ADRs [001](docs/adr/001-tudo-local.md) a [009](docs/adr/009-pedido-a-002-so-com-caminhos.md) e [SKILL.md](.claude/skills/adr/SKILL.md)). Todos os ADRs têm contexto, alternativas, custo (`⚠️`) e verificação que bate com o repositório, sem expor segredos ou caminhos pessoais do PC, e a skill `/adr` mantém `Write`/`Edit` fora de `allowed-tools`.

---

### 2. Achados

Nenhum problema bloqueante. Apenas uma observação leve (opcional para o futuro):

1. **Decisão de roteamento por regras fixas (`intencoes.py`) citada só de passagem**
   - **onde**: [002-cerebro-proprio-em-python.md:11](docs/adr/002-cerebro-proprio-em-python.md#L11) e [003-bracos-java-por-allowlist.md:11](docs/adr/003-bracos-java-por-allowlist.md#L11)
   - **o que acontece**: em [seguranca-owasp.md:13](docs/seguranca-owasp.md#L13), uma defesa importante contra *prompt injection* (texto malicioso tentando enganar a IA) é que `intencoes.py` escolhe a ação por regras fixas em vez de deixar o Gemma decidir sozinho qual braço chamar, mas isso não aparece nos ADRs 002 ou 003.
   - **como corrigir**: se quiser destacar isso para entrevistas, citar `intencoes.py` em 1 frase no ADR 003 (ou criar um ADR futuro com `/adr`).

---

### 3. Pergunta para o José
Em todo ADR a seção "Consequências" exige pelo menos um `⚠️` (custo) e a regra diz para nunca apagar um ADR antigo quando mudar de ideia — por que essas duas regras ajudam você numa entrevista técnica?


## 001 — resposta
1. `intencoes.py` só de passagem → **aceito**: 1 frase no ADR 003.

## Veredito final
✅ (002, rodada 1) + ajuste de 1 frase, sem mudança de código.
