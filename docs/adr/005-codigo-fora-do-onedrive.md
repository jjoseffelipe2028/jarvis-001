# ADR 005 — Código fora do OneDrive

- **Status:** Aceita
- **Data:** 2026-10-28
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
O OneDrive sincronizou a pasta `.git` no meio de uma escrita, apagou arquivos e quebrou o repositório.

## Decisão
Todo código fica em `workSpace` (fora do OneDrive). **A nuvem do código é o GitHub.** O cofre Obsidian (texto) continua no OneDrive.

## Alternativas consideradas
- **Continuar no OneDrive**: repositório corrompido de novo.

## Consequências
- ✅ Git estável.
- ⚠️ Duas "nuvens" para lembrar: GitHub (código) e OneDrive (notas).

## Como verificar
`CLAUDE.md`: "nunca no OneDrive".
