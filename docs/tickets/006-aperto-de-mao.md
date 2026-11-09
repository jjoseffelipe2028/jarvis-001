# 006 — Aperto de mão (challenge-response) antes do token
**Status:** ✅ aprovado pelo José (2026-11-09). Complementado pelo 007: o token não viaja mais.
**Criado:** 2026-11-09 pela 001 · corrige o achado 🟡 1 do Ticket 005

## O problema
Com a API Java desligada, qualquer programa do PC pode abrir a porta 8080 e se passar pelo Jarvis. O Python mandaria o **token** para ele.

## A solução
1. Antes de todo pedido, o Python sorteia um número (`secrets.token_hex(16)`) e chama `GET /acoes/prova?desafio=...` **sem o token**.
2. A API verdadeira responde `HMAC-SHA256(token, "jarvis-servidor:" + desafio)`. Só quem conhece o token consegue.
3. O Python calcula o mesmo e compara em tempo constante (`hmac.compare_digest`). Bateu → manda o pedido com token. Não bateu → **para** e avisa: "Quem respondeu na porta 8080 não provou ser o Jarvis."

**HMAC** em 1 linha: assinatura feita com um segredo; sem o segredo, ninguém faz igual.

## Escopo
- NOVO `seguranca/ProvaController.java` (rota pública `/acoes/prova`, desafio só hex 32–128).
- `TokenInterceptor`: `ROTAS_PUBLICAS` += `/acoes/prova`.
- `python/bracos.py`: `_conferir_servidor()` antes de todo pedido.
- Testes: `ProvaControllerTest` (vetor calculado pelo **Python**, para provar que os dois lados batem) e 5 testes no `test_bracos.py` (impostor não recebe token, desafio novo a cada pedido...).

## Evidências
- Java: **138 testes**, 0 falhas · Python: **43 testes**, 0 falhas.
- Revisão `jarvis-hacker` (2026-11-09): **✅ APROVA**, 0 🔴, 4 🟡 para ticket futuro, 4 🟢 ok:
  1. 🟡 Prova e pedido são 2 conexões: se a API cair **exatamente no meio** e um impostor subir, ele pega o token (janela pequena).
  2. 🟡 Relay: se a API um dia rodar em outra porta, um impostor na 8080 pode repassar o desafio.
  3. 🟡 Token usado direto como chave do HMAC → usar subchave derivada.
  4. 🟡 Rota pública sem limite de uso nem log.
  - **Solução definitiva (ticket futuro):** **nunca mandar o token**. Cada pedido vai **assinado** (`HMAC(token, método|rota|corpo|hora|nonce)`); a API confere e recusa hora velha e nonce repetido. Resolve 1, 2 e 3 de uma vez.
- Teste manual: _(José: reiniciar a API e repetir "quais minhas pendências?" no `chat.py`)_
- Commit: `19e2ff0` (2026-11-09, aprovado pelo José após teste real por voz)

## 🎤 Pergunta para o José
> No aperto de mão, por que o Python manda um número **novo** a cada vez, em vez de sempre o mesmo?

**Resposta do José:** Para um espião não gravar a resposta e repetir depois (replay).
