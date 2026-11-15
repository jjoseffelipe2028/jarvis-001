# ADR 006 — Modo Arquiteto: o José aprova, a IA constrói

- **Status:** Aceita
- **Data:** 2026-11-02
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
Construir tudo do zero estava lento, e o José precisa saber **explicar** cada parte numa entrevista.

## Decisão
O José é o **Arquiteto** (analisa, audita, aprova). A IA é a **Engenheira Construtora**. Toda tarefa: plano curto → OK → construir → explicar → **1 pergunta** para o José responder com as palavras dele. Pacote de terceiros só depois de auditar (é oficial? o que instala? manda dado para fora?).

## Alternativas consideradas
- **IA em piloto automático** (Ralph Loop e similares): rápido, mas sem supervisão e caro.
- **José escrevendo tudo sozinho**: lento demais para a meta do 1º emprego.

## Consequências
- ✅ Nada entra sem o José entender e aprovar.
- ⚠️ Cada tarefa espera o José, então é mais lento que um piloto automático.

## Como verificar
`CLAUDE.md`, seção "Modo Arquiteto".
