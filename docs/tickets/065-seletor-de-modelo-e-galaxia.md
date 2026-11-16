# 065 — Seletor de modelo (Gemma pequeno/maior) · galáxia 3D das notas 🧠🌌
**Status:** Parte A ✅ construída e commitada no ramo (15/11) · Parte B ⏳ **precisa do OK do José** (baixar Three.js) · seção 2 da lista mestra

## Parte A — Seletor de modelo ✅
- No chat da Central, uma caixinha **🧠 Gemma pequeno / Gemma maior**.
- A tela manda só `"modelo":"pequeno"` ou `"maior"` (`@Pattern`, lista fechada). **O nome do modelo nunca vem da tela.**
  `"modelo":"llama3:70b"` → 400.
- "pequeno" = `jarvis.conversa.modelo` (hoje `gemma4:e2b`, o único instalado).
  "maior" = `jarvis.conversa.modelo-maior=${JARVIS_MODELO_MAIOR:}`. **Vazio = desligado**: a opção aparece cinza e a API devolve 503.
- `GET /central/acoes/chat/modelos` → `{"pequeno":true,"maior":false}` (só liga/desliga, sem nome de modelo).
- Log: `modelo=pequeno|maior`, nunca a conversa.

### Para ligar o "maior" (José, com OK)
1. Escolher o modelo e baixar: `ollama pull <nome>` (download de alguns GB, fonte oficial do Ollama).
2. Pôr `JARVIS_MODELO_MAIOR=<nome>` no arquivo de segredos e reiniciar a API.

### Evidências
- **Java: 483 testes, 0 falhas** (+3): `ConversaServiceTest` (maior usa o nome da configuração; sem configuração 503),
  `CentralChatControllerTest` (+2: maior vai pelo caminho certo, nome fora da lista 400; lista de modelos).
- `paginas.js` conferido com o Node. gitleaks limpo.

## Parte B — Galáxia 3D das notas ⏳ (plano, espera OK)
- Página nova "🌌 Galáxia": cada nota do cofre é uma estrela; links `[[...]]` viram linhas; cor por pasta (daily, decisions, knowledge…).
- Precisa da biblioteca **Three.js** (MIT, mantida por mrdoob/comunidade, sem telemetria). Como a CSP da Central só aceita
  arquivos do próprio Jarvis, ela teria que ser **baixada uma vez** (npm oficial / GitHub releases) e guardada em `static/vendor/`.
  → **Download de pacote = OK do José.** Alternativa sem download: galáxia 2D em `<canvas>` puro (dá para fazer já).
- Java: `GET /central/acoes/galaxia` lendo **só nomes e links** das notas (nunca o conteúdo), dentro da pasta do cofre configurada,
  com limite de notas e anti Path Traversal.
