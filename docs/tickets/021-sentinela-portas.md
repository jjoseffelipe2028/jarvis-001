# 021 — Sentinela v1: vigia de portas 🛡️
**Status:** ✅ commit aprovado pelo José (10/11) · ⏳ teste real depois de reiniciar a API
**OK:** José, 10/11 de madrugada ("arrocha o máximo... termina hoje o Jarvis")
**Criado:** 2026-11-10 pela 001 · Roteiro: "Módulos: Sentinela (só avisa)" · Ideia do José de 30/10 ([[jarvis-ideias-futuras]])

## Ideia
"Jarvis, vigia as portas." O Jarvis olha quais portas do PC estão **abertas para a rede** e diz o que é cada uma. Ele **só avisa**: quem fecha é o José (regra de 31/10: IA com poder de agir vira arma se for enganada).

## Como funciona
1. **Java:** `PortasService` roda `netstat -a -n`, achado pelo **caminho completo** (`%SystemRoot%\System32\NETSTAT.EXE`).
2. **`LeitorDeNetstat`** (função pura): porta escutando = endereço externo `0.0.0.0:0` ou `[::]:0`. Não depende do idioma do Windows (o estado pode vir "ESCUTANDO").
3. Alcance de cada porta: **só no PC** (127.0.0.1 / [::1]), **uma placa de rede** (ex.: 192.168.0.10) ou **toda a rede** (0.0.0.0 / [::]).
4. Risco e nota fixa para as portas conhecidas (135 RPC, 445 SMB/WannaCry, 3389 RDP, 11434 Ollama…). A porta da própria API do Jarvis aberta para a rede = **alerta alto**.
5. Rotas (só leitura):
   - `GET /acoes/sentinela/portas` (voz, token assinado; vira evento no escritório);
   - `GET /central/acoes/sentinela/portas` (botão 🛡️ Portas + skill "Sentinela: portas").
6. **Python:** "vigia as portas", "tem porta aberta?", "quais portas estão abertas?", "sentinela" → ação `portas`. "Abre a porta 3389" continua **proibida**.

## 🔒 Travas
- Comando **fixo**; nada do pedido entra nele. Sem terminal no meio (`ProcessBuilder` direto).
- `ExecutorFixo`: tempo limite de 8 s, saída de no máximo 512 KB, mata o processo e os filhos.
- Um por vez (`Semaphore`): 100 pedidos juntos não viram 100 netstats.
- A resposta tem **só** número de porta, alcance e notas fixas: nada de IP de quem conecta, PID ou nome de programa.
- No máximo 40 portas na lista. Fora do Windows (CI no Linux) → 503.

## Evidências
- Java: `LeitorDeNetstatTest` (5), `PortasServiceTest` (8, um roda o **netstat de verdade** no Windows), `SentinelaControllerTest`, `CentralSentinelaControllerTest`.
- Python: `test_intencoes` (frases que viram ação, conversa que não vira, "abre a porta" proibida, respostas) e `test_bracos` (rota fixa e assinada, 503 vira frase).
- Teste no navegador (001, cópia da API na 8091): **12 portas abertas para a rede** neste PC, API do Jarvis só no 127.0.0.1 ✅. Achados reais: 445 (SMB), 139 (NetBIOS) e **7260 (desconhecida)**. Sem token → 401; Host falso → 403.
- Teste real (José): _(preencher)_ → "Jarvis, vigia as portas" no chat + botão 🛡️ Portas na Central.
- Commit: _(preencher)_

## Fica para depois (v2)
- UDP; nome do programa de cada porta (precisa de admin); avisar sozinho quando aparecer porta **nova**.

## 🎤 Para a entrevista
"Fiz um vigia de portas em Java: roda o netstat com comando fixo, tempo limite e saída limitada, e explica cada porta aberta. Ele só avisa; quem age sou eu."
