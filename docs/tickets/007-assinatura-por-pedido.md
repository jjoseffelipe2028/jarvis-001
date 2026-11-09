# 007 — Assinatura por pedido: o token nunca viaja
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001 · resolve os 🟡 1, 2 e 3 do Ticket 006

## O problema que sobrou do 006
O aperto de mão prova quem atendeu a **prova**, não quem atende o **pedido seguinte**. E o pedido seguinte levava o token: um impostor que entrasse no meio (ou repassasse o desafio) ficaria com ele.

## A solução
O Python **para de mandar o token**. Cada pedido leva:
| Cabeçalho | O que é |
|---|---|
| `X-Jarvis-Hora` | segundos desde 1970 (agora) |
| `X-Jarvis-Nonce` | número aleatório, vale **uma vez só** |
| `X-Jarvis-Assinatura` | `HMAC(subchave, MÉTODO\nROTA\nHORA\nNONCE\nSHA256(corpo))` |

`subchave = HMAC(token, "jarvis-assinatura-v1")`: o token não é usado direto em dois lugares (separação de domínio).

A API recalcula e só aceita se: assinatura bate · hora dentro de ±60 s · hora depois do início da API · nonce nunca visto.
**Resultado:** quem pegar um pedido não consegue fazer outro (não sabe a chave), nem repetir o mesmo (nonce usado), nem trocar rota, método ou corpo (quebra a assinatura).

O `X-Jarvis-Token` direto continua aceito **só para os seus testes com `curl`**, e cada uso fica no log como `aviso=token_simples`.

## Escopo
- NOVO `seguranca/Assinatura.java` (conferência), `seguranca/CorpoGuardadoFilter.java` (guarda os bytes exatos do corpo, máx. 64 KB → 413).
- `TokenInterceptor`: assinatura primeiro; se a assinatura vier e estiver errada, **não** cai no token (sem rebaixamento).
- `python/bracos.py`: função `assinar()`; `_pedir` não manda mais o token.

## Evidências
- Java **158** · Python **60** testes, 0 falhas. Vetor de teste calculado no **Python** e conferido no **Java** (os dois lados assinam igual).
- **Teste real** (API numa porta separada, token fictício): `hora` ✅ · `git-status` assinado ✅ (o Java aceitou sem receber o token) · token errado → barrado no aperto de mão ✅.
- Revisão `jarvis-hacker`: **✅ APROVA**, 0 🔴, 2 🟡 corrigidos na hora:
  1. 🟡 Replay logo depois de reiniciar a API (a lista de nonces some) → **corrigido**: recusa assinatura anterior ao início da API + teste.
  2. 🟡 Se um pedido escapasse do filtro, a assinatura conferiria corpo vazio → **corrigido**: sem corpo guardado = 401 (falha fechada) + teste.
  3. 🟢 Caminho do token simples aberto → mantido para o `curl`, agora com aviso no log.
  4. 🟢 Lista de nonces lotada (10.000 em 2 min) → 503; só quem tem a chave lota. Aceito.
  5. 🟢 Corrida no mapa → sem nonce duplicado (`putIfAbsent` atômico). Aceito.
  6. 🟢 Lê até 64 KB antes do Host → porta só local, limite existe. Aceito.
- Commit: `19e2ff0` (2026-11-09, aprovado pelo José após teste real por voz)

## 🎤 Pergunta para o José
> Se alguém **copiar** um pedido assinado inteiro (com a assinatura), por que ele não consegue mandar de novo?

**Resposta do José:** Porque o nonce já foi usado: a API vê que ele já entrou e recusa. Trocar o nonce quebra a assinatura.
