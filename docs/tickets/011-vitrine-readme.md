# 011 — Vitrine: diagrama no README + ponto duplo do "bom dia"
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001 · Fase 6, parte 2 (vitrine)

## O que mudou
| # | Antes | Agora | Arquivo |
|---|---|---|---|
| 1 | Diagrama em texto (setas `↓`) | Diagrama **Mermaid**: o GitHub desenha sozinho, sem instalar nada | `README.md` |
| 2 | README falava em "token secreto" no caminho | Atualizado: pedido assinado, o token nunca viaja (Ticket 007) | `README.md` |
| 3 | Selos só de Java e CodeQL | Selos dos **4 robôs**: Java, Python, CodeQL, gitleaks | `README.md` |
| 4 | "bom dia" falava `"...Share).."` quando a nota já terminava em ponto | Tira a pontuação do fim antes de pôr o ponto | `python/rotinas.py` |

**Mermaid** em 1 linha: um jeito de desenhar diagramas escrevendo texto. O GitHub transforma em figura.

## Evidências
- Python: **61** testes, 0 falhas (+1: `test_pendencia_terminando_em_ponto_nao_vira_ponto_duplo`).
- Diagrama conferido com o Mermaid oficial (v11.4.1) no navegador: sintaxe OK. Corrigido um rótulo que começava com `1.` e virava lista.
- Commit: _(preenchido no próximo ticket)_

## 🎤 Pergunta para o José
> No diagrama, por que a seta "não é ação" vai para o Gemma, e não para o Java?
