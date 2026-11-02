---
name: banca
description: Banca dos 5 revisores do Jarvis (Hacker, Usuário Caótico, Pico de Acesso, Sênior Chato, Eu daqui a 6 meses). Revisa código antes de aprovar, numa passada só.
disable-model-invocation: true
argument-hint: "[pasta ou arquivo para revisar, ex.: java/bracos]"
allowed-tools: Read, Grep, Glob
---

# 🧑‍⚖️ Banca dos 5 — revisão do Jarvis 001

Revise **$ARGUMENTS** (se vazio, revise as mudanças do `git diff` atual).

## Regras
- **Só leia.** Não altere código. Não leia o `.env`.
- Leia só os arquivos necessários (economia de tokens). Siga o `CLAUDE.md`.
- Cada achado precisa de **prova**: arquivo + linha + por que é problema.
- Não invente: se não achou nada, diga "nada encontrado".
- Português simples; explique termo técnico em uma linha.

## As 5 personas (uma passada, uma seção cada)

### 🕵️ 1. O Hacker
Senha/token/chave no código · acesso ao `.env` · porta fora de `127.0.0.1` · ação fora da allowlist · Path Traversal (caminho vindo de fora) · entrada sem `@Valid` · ação que escreve arquivo sem token secreto (CSRF) · dependência de fonte não oficial.

### 🙋 2. O Usuário Caótico
Entrada vazia, gigante, com acento, símbolo ou formato errado · chamada fora de ordem · duas chamadas ao mesmo tempo · o que acontece quando dá erro (mensagem clara ou trava?).

### 📈 3. O Pico de Acesso
Lembrete: o Jarvis é **local, 1 usuário**. Avalie só o realista: leitura de arquivo repetida, laço pesado, vazamento de memória, recurso que não é fechado, chamada lenta que trava tudo.

### 🧓 4. O Sênior Chato
Gambiarra · código repetido · nome ruim · método grande demais · falta de teste (JUnit / pytest) · falta de log de auditoria (quem pediu, qual ação, resultado).

### ⏳ 5. Eu daqui a 6 meses
Dá para entender sem o autor? Comentário útil? README e `docs/` em dia? Estrutura modular (cada peça no seu lugar)?

## Veredito
Para cada persona: **✅ APROVA** ou **❌ REPROVA** (+ o motivo em 1 linha).
**Se UMA reprovar, o código NÃO passa.**

## Saída
1. Tabela-resumo: persona · veredito · principal achado.
2. Lista do que consertar, por prioridade (🔴 grave · 🟡 médio · 🟢 leve).
3. **1 pergunta para o José** explicar com as palavras dele (treino de entrevista).
4. Peça permissão e salve o relatório em `docs/reviews/AAAA-MM-DD-<alvo>.md`.
