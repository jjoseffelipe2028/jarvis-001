# 🤖 Jarvis 001

**Central de automação local com IA offline — Java + Python**

Um assistente estilo Jarvis que roda **no meu próprio PC**: me escuta em português, responde por voz e automatiza minha rotina de estudos — sem enviar meus dados para a nuvem.

> 🚧 Projeto em construção — **Fase 2 de 7**: o cérebro já conversa por texto; os ouvidos (voz → texto) estão sendo ligados.

## Como funciona

```
🎙️ Eu falo
   ↓
🐍 PYTHON — cérebro e voz (IA local)
   • ouve (faster-whisper) e entende (Ollama + Gemma)
   • escolhe uma ação
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

- [x] 1. Oficina — ferramentas, repositório e travas de segurança
- [ ] 2. Cérebro e voz (Python) — 🧠 cérebro ✅ · 🎙️ ouvidos 🔄 · 🔊 voz ⬜
- [ ] 3. Braços em Java (API REST)
- [ ] 4. Integração Python + Java
- [ ] 5. Escritório dos agentes (pixel art)
- [ ] 6. Segurança e vitrine
- [ ] 7. Loops da dupla Claude + Gemini

## Tecnologias

| Parte | Ferramenta | Roda onde |
|---|---|---|
| Cérebro | [Ollama](https://ollama.com) + `gemma4:e2b` | 💻 local (`127.0.0.1`) |
| Ouvidos | [faster-whisper](https://github.com/SYSTRAN/faster-whisper) + sounddevice | 💻 local (o áudio não sai do PC) |
| Braços | Java 21 + Spring Boot | 💻 local (Fase 3) |
| Time de IA | Claude Code (001) + Antigravity CLI (002) | revisão cruzada |

## Como rodar (Windows)

```bash
cd python
python -m venv .venv
source .venv/Scripts/activate   # Git Bash
pip install -r requirements.txt
python chat.py
```
Ou dois cliques em `iniciar_jarvis.bat`. Precisa do Ollama rodando com o modelo `gemma4:e2b`.

## Segurança 🔒

- Tudo roda localmente (`127.0.0.1`), nada aberto para a rede.
- Ações só de uma lista permitida (allowlist).
- Senhas e chaves fora do Git (`.env` no `.gitignore`).
- Travas para a IA: o Claude Code **não lê o `.env`** e **não edita as próprias regras** (`.claude/`) — testado.
- Dependências conferidas antes de instalar (só fonte oficial).
- Revisão com OWASP Top 10.

## Autor

**José Felipe** — estudante de Java Back-end e Cibersegurança (EBAC).

## Licença

MIT
