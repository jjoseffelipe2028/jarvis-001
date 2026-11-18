# ADR 001 — Tudo local, só em 127.0.0.1

- **Status:** Aceita
- **Data:** 2026-10-30
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
O Jarvis lê o cofre do José, fala com o Git e roda IA. Se a API ou a IA ouvissem a rede, qualquer aparelho do Wi-Fi (ou uma página aberta no navegador) poderia mandar ações.

## Decisão
Tudo roda no PC do José. A API Java escuta **só em `127.0.0.1:8080`**, a IA é local (Ollama) e nenhum dado do José vai para serviço de fora sem ele pedir.

## Alternativas consideradas
- **IA na nuvem (API paga)**: mais forte, mas os textos do cofre sairiam do PC.
- **Escutar em `0.0.0.0` para usar pelo celular**: abriria a API para a rede inteira.

## Consequências
- ✅ Superfície de ataque mínima: de fora do PC não há porta para atacar.
- ✅ Funciona sem internet.
- ⚠️ A IA local é menor (limite da placa de vídeo).
- ⚠️ Usar pelo celular exige outra solução (fica para depois).

## Como verificar
`server.address=127.0.0.1` no `application.properties`.
