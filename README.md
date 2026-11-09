# 🤖 Jarvis 001

[![Testes Java](https://github.com/jjoseffelipe2028/jarvis-001/actions/workflows/testes-java.yml/badge.svg)](https://github.com/jjoseffelipe2028/jarvis-001/actions/workflows/testes-java.yml)
[![CodeQL](https://github.com/jjoseffelipe2028/jarvis-001/actions/workflows/codeql.yml/badge.svg)](https://github.com/jjoseffelipe2028/jarvis-001/actions/workflows/codeql.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue)

**Assistente local com IA offline, em português: Python no cérebro, Java nos braços e segurança em primeiro lugar.**

O Jarvis roda **no meu próprio PC**. Ele me escuta, responde por voz e automatiza minha rotina de estudos (notas do Obsidian, tarefas pendentes, Git), **sem mandar meus dados para a nuvem**.

A regra central: **a IA nunca executa nada direto no computador.** Ela só *pede*, e uma API em Java decide se a ação existe numa lista fechada (allowlist), confere o token e registra tudo.

---

## ✨ Destaques para quem está avaliando

| | |
|---|---|
| ☕ **API REST em Java 21 + Spring Boot 4** | 4 ações reais, cada uma em camadas (`Controller` → `Service`), com validação e erros padronizados |
| 🔒 **Segurança como requisito** | Token em tempo constante, anti CSRF, anti DNS rebinding, anti Path Traversal, anti injeção de comando, anti log injection |
| 🧪 **158 testes Java + 60 Python** | JUnit 5, unittest + MockMvc + servidor real. Os testes **atacam** a API (truques de URL, `../`, Unicode malicioso, config de git maliciosa) |
| ⚙️ **CI/CD no GitHub** | Testes a cada push (Actions), análise de segurança (CodeQL) e atualização de dependências (Dependabot) |
| 🧠 **IA 100% local** | Ollama + Gemma, faster-whisper e Piper: o áudio nunca sai do PC |
| 📋 **Processo de engenharia** | Cada entrega tem ticket, plano aprovado, revisão de segurança e evidências → [`docs/tickets/`](docs/tickets/) |

## 🏗️ Como funciona

```
🎙️ Eu falo
   ↓
🐍 PYTHON: cérebro e voz (IA local)
   • ouve (faster-whisper) e entende (Ollama + Gemma)
   • escolhe UMA ação da lista
   ↓  HTTP só em 127.0.0.1 + token secreto
☕ JAVA: braços (Spring Boot)
   • porteiros: Host → token → validação
   • executa só ações da allowlist, dentro de pastas permitidas
   • log de auditoria (sem dado pessoal)
   ↓
📁 Obsidian (notas) · 🌿 Git
```

## ☕ API (braços em Java)

| Método | Rota | O que faz |
|---|---|---|
| `GET` | `/acoes/hora` | Data e hora de Brasília (pública) |
| `POST` 🔒 | `/acoes/daily` | Escreve uma linha no daily de hoje do Obsidian |
| `GET` 🔒 | `/acoes/pendencias` | Tarefas abertas (`- [ ]`) dos últimos 7 dailys |
| `GET` 🔒 | `/acoes/git-status` | Diz se o repositório tem algo sem commit (só números) |
| `GET` | `/acoes/prova` | Aperto de mão: prova (HMAC) que esta é a API verdadeira, antes de o Python mandar o token |

Detalhes, exemplos e como rodar: [`java/README.md`](java/README.md).

## 🔒 Segurança: ameaça → defesa → prova

| Ameaça | Defesa | Prova (teste) |
|---|---|---|
| Site malicioso chamando a API pelo navegador (CSRF) | Token secreto em **todo** `/acoes/**`, fechado por padrão, comparado em tempo constante | `TokenInterceptorTest`, `RotasPostContratoTest` |
| DNS rebinding | Só aceita `Host` = `localhost` / `127.0.0.1` | `HostInterceptorTest` |
| Truques de URL para furar o porteiro (`//`, `%2F`, `/../`) | Lista pública com comparação **exata** | `TruquesDeUrlServidorRealTest` (Tomcat de verdade) |
| Ler/escrever fora da pasta (Path Traversal) | Pasta só da config, nome de arquivo gerado pelo servidor, `normalize` + `startsWith`, sem seguir atalhos | `DailyServiceTest`, `PendenciasServiceTest` |
| Injeção de comando | Comando `git` **fixo**, sem shell, sem variáveis `GIT_*` de fora, tempo e saída limitados | `GitStatusServiceTest` |
| `.git/config` malicioso executando programa | `core.fsmonitor` desligado no comando | ataque real no teste: o git cru executa, o Jarvis não |
| Markdown/HTML ativo na nota (imagem que vaza IP, DataviewJS) | Texto "desarmado" com `\` | `DailyServiceTest` |
| Log injection | `LogSeguro.limpar()` + teste que lê o código-fonte | `LogSemDadoCruTest` |
| Programa impostor na porta 8080 roubando o token | Aperto de mão (challenge-response com HMAC) antes de o token sair do Python | `ProvaControllerTest`, `test_bracos.py` |
| Pedido copiado, repetido ou alterado no caminho | **O token nunca viaja**: cada pedido vai assinado (HMAC + hora + nonce de uso único) | `AssinaturaTest`, `AssinaturaDePontaAPontaTest` |
| Vazamento de dado pessoal | Respostas e logs sem caminho, sem texto do usuário e sem stack trace | testes de controller |

Revisões de segurança registradas: [`docs/reviews/`](docs/reviews/) e nas fichas de cada ticket.

**Revisão OWASP Top 10 (2025):** 7 ✅ · 3 🟡 em melhoria, cada item com o arquivo da defesa → [`docs/seguranca-owasp.md`](docs/seguranca-owasp.md). Histórico varrido com **gitleaks**: 0 segredos.

## 🧰 Tecnologias

| Parte | Ferramenta | Roda onde |
|---|---|---|
| Braços | Java 21 + Spring Boot 4.1.2 + Maven | 💻 `127.0.0.1:8080` |
| Testes | JUnit 5, MockMvc, Mockito | 💻 + GitHub Actions |
| Cérebro | [Ollama](https://ollama.com) + `gemma4:e2b` | 💻 local |
| Ouvidos | [faster-whisper](https://github.com/SYSTRAN/faster-whisper) + sounddevice | 💻 local |
| Boca | [Piper](https://github.com/OHF-Voice/piper1-gpl) (voz pt_BR) | 💻 local |
| CI/Segurança | GitHub Actions, CodeQL, Dependabot | ☁️ GitHub |
| Time de IA | Claude Code (001) + Antigravity CLI (002), com revisão cruzada | 💻 |

## 🚀 Como rodar

**API Java** (precisa de Java 21):
```bash
cd java/bracos
./mvnw test              # 158 testes
./iniciar.sh             # carrega o .env e sobe em 127.0.0.1:8080
```

**Cérebro e voz** (Python 3.13 + Ollama com `gemma4:e2b`):
```bash
cd python
python -m venv .venv
source .venv/Scripts/activate   # Git Bash
pip install -r requirements.txt
python chat.py
```

## 🗺️ Roteiro

- [x] Base: cérebro (`chat.py`), ouvido (`ouvido.py`) e boca (`boca.py`), tudo offline
- [x] **Braços em Java**: `hora`, `daily`, `pendencias`, `git-status` · segurança · 158 testes · CI
- [x] Integração voz ↔ Java: pendências, git-status, daily, "bom dia, Jarvis" · pedidos assinados
- [ ] Central web com os agentes trabalhando em tempo real
- [ ] Módulos: Sentinela (só avisa), Vídeos, Biblioteca offline
- [ ] Dupla 001 + 002 em loop com GitHub Issues
- [x] Revisão OWASP Top 10 + gitleaks + portas testadas pela rede
- [ ] Spring Security

## 👤 Autor

**José Felipe**: estudante de Java Back-end e Cibersegurança (EBAC), em busca da primeira vaga em tecnologia.

## Licença

MIT
