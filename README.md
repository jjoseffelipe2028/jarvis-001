# 🤖 Jarvis 001

**Central de automação local com IA offline — Java + Python**

Um assistente estilo Jarvis que roda **no meu próprio PC**: me escuta em português, responde por voz e automatiza minha rotina de estudos — sem enviar meus dados para a nuvem.

> 🚧 Projeto em construção — **Fase 2 de 7: braços em Java**. A base em Python (cérebro, ouvido e boca) já funciona; a API Java já tem a 1ª ação (`/acoes/hora`) com teste.

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

**Base pronta:** repositório com travas de segurança · 🧠 cérebro (`chat.py`) · 🎙️ ouvido (`ouvido.py`) · 🔊 boca (`boca.py`).

- [x] 1. Oficina Turbo — skills, hooks (guarda) e a Banca de revisão
- [ ] 2. Braços em Java — `daily`, `pendencias`, `git-status`, segurança e JUnit · 🔄 1ª ação `/acoes/hora` ✅
- [ ] 3. Integração voz ↔ Java
- [ ] 4. Central web futurista — HUD, bonecos dos agentes, botões
- [ ] 5. Módulos — Sentinela (só avisa), Vídeos (Remotion), Biblioteca offline (RAG)
- [ ] 6. Dupla 001 + 002 em loop (GitHub Issues)
- [ ] 7. Vitrine — gitleaks, OWASP, README, CI

## Tecnologias

| Parte | Ferramenta | Roda onde |
|---|---|---|
| Cérebro | [Ollama](https://ollama.com) + `gemma4:e2b` | 💻 local (`127.0.0.1`) |
| Ouvidos | [faster-whisper](https://github.com/SYSTRAN/faster-whisper) + sounddevice | 💻 local (o áudio não sai do PC) |
| Boca | [Piper](https://github.com/rhasspy/piper) (voz pt_BR) | 💻 local |
| Braços | Java 21 + Spring Boot 4.1.2 ([como rodar](java/README.md)) | 💻 local (`127.0.0.1:8080`) |
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
