# 🤖 Jarvis 001

**Central de automação local com IA offline — Java + Python**

Um assistente estilo Jarvis que roda **no meu próprio PC**: me escuta em português, responde por voz e automatiza minha rotina de estudos — sem enviar meus dados para a nuvem.

> 🚧 Projeto em construção — Fase 1 de 7.

## Como funciona

```
🎙️ Eu falo
   ↓
🐍 PYTHON — cérebro e voz (IA local)
   • entende o pedido e escolhe uma ação
   ↓  API local (só localhost)
☕ JAVA — braços (Spring Boot)
   • executa apenas ações permitidas
   • registra tudo em log
   ↓
🕹️ Painel — agentes trabalhando em tempo real
```

A IA **nunca** executa comandos direto no computador: ela só **pede**, e a API em Java decide se a ação é permitida.

## Estrutura

| Pasta | O que tem |
|---|---|
| `python/` | Cérebro e voz do Jarvis |
| `java/` | API com as ações (Spring Boot) |

## Fases

- [ ] 1. Oficina — ferramentas e repositório
- [ ] 2. Cérebro e voz (Python)
- [ ] 3. Braços em Java (API REST)
- [ ] 4. Integração Python + Java
- [ ] 5. Escritório dos agentes (pixel art)
- [ ] 6. Segurança e vitrine
- [ ] 7. Loops da dupla Claude + Gemini

## Segurança 🔒

- Tudo roda localmente (`127.0.0.1`), nada aberto para a rede.
- Ações só de uma lista permitida (allowlist).
- Senhas e chaves fora do Git (`.env` no `.gitignore`).
- Revisão com OWASP Top 10.

## Autor

**José Felipe** — estudante de Java Back-end e Cibersegurança (EBAC).

## Licença

MIT
