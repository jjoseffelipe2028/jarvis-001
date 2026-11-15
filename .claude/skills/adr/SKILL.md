---
name: adr
description: Registra uma decisão de arquitetura do Jarvis como ADR em docs/adr (contexto, decisão, alternativas, consequências com custo, como verificar). Use quando o José disser /adr <decisão> ou quando uma decisão importante for tomada (biblioteca nova, regra de segurança, mudança de arquitetura).
argument-hint: "[decisão, ex.: usar Kokoro como voz do Brabo]"
allowed-tools: Read, Glob, Grep
---

<!-- Item 34 da lista mestra (15/11/2026). Write/Edit fora do allowed-tools: criar o ADR pede permissão e o José vê. -->

# 📐 /adr — registrar uma decisão

Decisão: **$ARGUMENTS**

1. **Número:** leia `docs/adr/README.md` e use o próximo número livre (3 dígitos).
2. **Arquivo:** `docs/adr/NNN-titulo-curto.md`, copiando o formato do `docs/adr/000-modelo.md`.
3. **Preencha com fatos**, não com invenção:
   - **Contexto**: o problema real que apareceu (cite ticket, erro ou nota do cofre).
   - **Decisão**: 1 a 3 frases. Quem decidiu é sempre o **José**.
   - **Alternativas**: pelo menos 1, cada uma com o "por que não".
   - **Consequências**: pelo menos 1 ✅ e **pelo menos 1 ⚠️**. Toda decisão tem custo; se não achar, pergunte ao José.
   - **Como verificar**: arquivo, teste ou comando que prova que a regra é seguida.
4. Se a decisão **substitui** outra: no ADR antigo, troque o Status por "Substituída por ADR NNN". Nunca apague um ADR.
5. Acrescente a linha no índice do `docs/adr/README.md`.
6. **Nada de caminho do PC** (`C:\Users\...`) nem segredo no texto: o ADR é público no GitHub.
7. Se a decisão mexe com **segurança**, peça a contestação da 002 com `/dupla` antes do commit.

Termine com 1 pergunta para o José: "Se o entrevistador perguntar por que não fizemos [alternativa], o que você responde?"
