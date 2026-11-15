# ADR 009 — Pedido à 002 só com caminhos fixos (contra Command Injection)

- **Status:** Aceita
- **Data:** 2026-11-15
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
No plano do loop da dupla, a 001 ia colar a tarefa, o `git diff` e os testes direto em `agy -p="..."`. A 002 apontou: no Git Bash, uma crase ou `$(...)` dentro do texto **seria executada** antes de chegar nela. E textos grandes passam do limite de ~8 mil caracteres da linha de comando do Windows.

## Decisão
Texto variável vai sempre para **arquivo**. O comando da 002 é um **pedido fixo** que só cita caminhos validados (`docs/planos` e `docs/reviews`, nome `AAAA-MM-DD-slug.md`), chamado por lista de argumentos, sem shell (`chamar_002.py`).

## Alternativas consideradas
- **Escapar o texto antes de colar**: fácil de errar, e uma brecha basta.
- **Mandar o texto pela entrada padrão (stdin)**: resolve a injeção, mas não o tamanho, e o registro fica espalhado.

## Consequências
- ✅ Command Injection fechado por esse caminho (testado com `$(whoami)` e `../../.env`).
- ✅ Tudo fica registrado em arquivo.
- ⚠️ A 002 precisa ler arquivos (mais lento que receber o texto pronto).

## Como verificar
Ticket 070 e `.claude/skills/dupla/scripts/chamar_002.py`.
