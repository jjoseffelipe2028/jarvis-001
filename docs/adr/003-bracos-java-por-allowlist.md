# ADR 003 — Braços em Java por allowlist: a IA não roda comando livre

- **Status:** Aceita
- **Data:** 2026-11-02
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
O cérebro (IA) precisa fazer coisas no PC: escrever o daily, ver o git, rodar a Sentinela. Se a IA pudesse rodar qualquer comando, um texto malicioso (prompt injection) viraria comando no PC.

## Decisão
Toda ação passa pela **API Java** (`java/bracos`) e só existem as ações escritas à mão (uma rota fixa por ação, **sem rota genérica**). Entrada validada com `@Valid`, arquivos só em **pastas permitidas** (`PastaSegura`), processos externos só por **lista de argumentos fixa**. Quem escolhe **qual** ação chamar também não é a IA: o roteador `python/intencoes.py` decide por regras fixas, e o Gemma só conversa.

## Alternativas consideradas
- **IA com terminal livre**: rápido de fazer, mas qualquer injeção vira execução.
- **Rota genérica `/acoes/{qualquer}`**: deixa de ser allowlist.

## Consequências
- ✅ O pior que uma injeção consegue é pedir uma ação que já existe e é segura.
- ✅ Cada ação tem teste JUnit.
- ⚠️ Cada ação nova dá trabalho (ticket + skill `/nova-acao`).

## Como verificar
`AcoesController` e a skill `.claude/skills/nova-acao/SKILL.md`.
