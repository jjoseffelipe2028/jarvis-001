# 📐 ADRs — registros de decisão do Jarvis

**ADR (Architecture Decision Record)** = uma página curta que responde: *o que decidimos, por quê, o que ficou de fora e qual é o custo*. Serve para quem chega depois (e para a entrevista: "por que você fez assim?").

## Regras
- Um arquivo por decisão: `NNN-titulo-curto.md`, numerado em ordem. Comece pelo [modelo](000-modelo.md) ou pela skill `/adr`.
- ADR **não se apaga**: se a decisão mudar, escreva um ADR novo e marque o antigo como "Substituída por ADR NNN".
- Toda decisão tem **custo**: a seção "Consequências" sempre tem pelo menos um ⚠️.
- As notas pessoais continuam no cofre do José (`decisions/`); aqui fica a versão técnica e pública.

## Índice
| Nº | Decisão | Status |
|---|---|---|
| [001](001-tudo-local.md) | Tudo local, só em 127.0.0.1 | Aceita |
| [002](002-cerebro-proprio-em-python.md) | Cérebro próprio em Python + Gemma local | Aceita |
| [003](003-bracos-java-por-allowlist.md) | Braços em Java por allowlist: a IA não roda comando livre | Aceita |
| [004](004-pedido-assinado-contra-csrf.md) | Pedido assinado nas ações que escrevem (contra CSRF) | Aceita |
| [005](005-codigo-fora-do-onedrive.md) | Código fora do OneDrive | Aceita |
| [006](006-modo-arquiteto.md) | Modo Arquiteto: o José aprova, a IA constrói | Aceita |
| [007](007-dupla-001-002.md) | Dupla 001 (Claude) + 002 (Gemini), com a 002 só lendo | Aceita |
| [008](008-estudio-autonomia-com-trava.md) | Estúdio 001: autonomia pela Central, com trava | Aceita |
| [009](009-pedido-a-002-so-com-caminhos.md) | Pedido à 002 só com caminhos fixos (contra Command Injection) | Aceita |
