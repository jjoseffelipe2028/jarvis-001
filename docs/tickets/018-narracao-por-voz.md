# 018 — Narração por voz: o Jarvis fala o que está acontecendo
**Status:** ✅ aprovado (testado pelo José em 10/11, de madrugada)
**OK do plano:** José, 09/11 à noite ("A": constrói agora, testa junto com o 017)
**Criado:** 2026-11-09 pela 001 · Fase 5, parte 4 · Depende do Ticket 017 (eventos ao vivo)

## Ideia
O escritório já **mostra** os eventos reais (017). Agora o Jarvis também **fala** em voz alta: "Daily salvo", "Pedido recusado!".

## Plano curto
1. **Botão 🔇/🔊 "Voz"** no topo do escritório, **desligado por padrão**. O navegador só deixa falar depois de um clique.
2. Usa a voz do próprio Windows pela API de fala do navegador (`speechSynthesis`), sem instalar nada.
3. **Só voz local:** entra apenas voz com `localService === true` e pt-BR (ex.: "Microsoft Maria").
   - Vozes "Natural/Online" do Edge mandam o texto para a nuvem, então ficam **bloqueadas**.
   - Se não houver voz local, o botão mostra "sem voz local" e não fala.
4. Fala **só frases fixas** da lista do 017 (`ACOES`), nunca texto vindo da API.
5. Anti-barulho:
   - no máximo 1 fala a cada 3 s;
   - fila de até 3 frases;
   - pedido recusado 🚨 tem prioridade.
6. A escolha 🔊 não fica guardada no navegador: fecha a Central, volta mudo.

## Segurança (cyber na prática)
- Nada sai do PC: voz online bloqueada, e o teste confere `localService`.
- Nada de texto livre: só frases da lista fixa.
- A CSP continua igual.

## Testes
- `CentralPaginaTest`:
  - o `escritorio.js` usa `speechSynthesis` e confere `localService`;
  - continua sem rede, storage ou `innerHTML`.
- Teste no navegador (001), com a fala simulada:
  - mudo por padrão;
  - liga no clique;
  - voz online é recusada;
  - a fila limita a 3.
- Teste real (José): ligar 🔊, falar "Quais minhas pendências?" e ouvir o Jarvis responder pela caixa de som.

## Evidências
- Java: **270** testes, 0 falhas (+1: `vozSoLocal_comecaMuda_eSoFrasesFixas`).
- Teste no navegador (001), com a API e a fala simuladas:
  - mudo por padrão (🔇, `aria-pressed=false`); evento com voz desligada → 0 falas;
  - ao ligar, a voz "Online (Natural)" foi **pulada** e a escolhida foi a local;
  - 4 eventos juntos → o 🚨 recusado **furou a fila**, a fila parou em **3**, intervalo de **3 s** entre falas;
  - sem voz pt-BR local → "🔇 sem voz local" e não fala; desligar limpa a fila.
- Achado no teste: um erro na hora de falar travava a fila para sempre. Corrigido com `try/catch` (a fila sempre anda).
- A skill "Narrar em voz alta" saiu de "em breve" e virou "no ar": liga/desliga o 🔊 (pedido do teste real).
- Vozes locais no PC do José: **Microsoft Daniel** e **Maria** (pt-BR, local).
- Teste real (José, 10/11):
  - 1ª tentativa sem som, mas o teste do Windows (System.Speech) tocou na caixa, então o código e a caixa estavam ok;
  - depois de recarregar a Central e seguir o roteiro, o 🔊 "Voz ligada." tocou;
  - pelo chat, "quais minhas pendências" fez a Central falar "Pendências lidas.". ✅
- Commit: junto com o Ticket 017 (mesmo commit, arquivos em comum)

## Tamanho
Pequeno: só `escritorio.js`, `index.html`, `central.css` e 1 teste, sem mudar o Java.

## Fica para depois
- Narrar eventos do Claude Code: "o build passou", "achei um erro" (precisa de ticket próprio, lendo logs com allowlist).
