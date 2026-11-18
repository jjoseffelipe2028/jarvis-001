# ADR 008 — Estúdio 001: autonomia pela Central, com trava

- **Status:** Aceita
- **Data:** 2026-11-13
- **Decidiu:** José (Arquiteto) · escrito por: 001

## Contexto
O José quis pedir pela Central e o trabalho sair feito (jogos, cortes), sem abrir o terminal. Isso quebrava a regra "a IA nunca executa".

## Decisão
**Exceção controlada:** a Central chama o Claude Code com comando **fixo**, ferramentas limitadas por modo e `.env`/`.claude` bloqueados. **Nunca internet + escrita de código no mesmo modo**, porque uma página pode tentar mandar na IA. Cada pedido tem Revisar → Confirmar e deixa log.

## Alternativas consideradas
- **Terminal livre pela Central**: qualquer texto viraria execução.
- **Não ter autonomia**: o José teria que abrir o terminal para tudo.

## Consequências
- ✅ Pedido pela tela, com travas por modo.
- ⚠️ É a parte mais arriscada do Jarvis; cada modo novo precisa de revisão de segurança.

## Como verificar
Tickets 047 e 048; `EstudioComandos.java`.
