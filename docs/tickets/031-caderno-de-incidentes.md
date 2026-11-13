# 031 — Caderno de Incidentes (mini SOC para treinar)
**Status:** ✅ construído e testado (12/11, lote pedido pelo José) · ⏳ commit com o OK

## Ideia
Cada alerta da Sentinela vira uma nota no cofre com as perguntas de um analista de SOC N1:
é ataque ou só incidente? · qual pilar (C, I ou D)? · quem seria o ator? · o que fazer?
O José responde **primeiro**. O gabarito fica dobrado no fim da nota (com a técnica do MITRE ATT&CK).

## Como usar
```
python incidentes.py                              # ronda: portas + travas
python incidentes.py novo "Popup: seu antivírus venceu"   # caso que você viu
```
Precisa da linha `JARVIS_PASTA_INCIDENTES=` no `.env` (uma pasta que exista).

## Segurança
- Só **lê** a Sentinela (pedido assinado) e só **cria** notas novas. Mesma nota no mesmo dia → pula (nunca sobrescreve).
- Nome do arquivo gerado aqui: data + nome só com `a-z`, `0-9` e hífen (título do usuário nunca vira caminho).
- O que vem da API é limpo antes de ir para a nota; o gabarito é texto fixo do código.
- No máximo 8 notas por ronda. Um braço fora do ar não derruba o outro. Alarme de impostor sobe.

## Evidências
- `test_incidentes.py`: 13 testes (Sentinela falsa, pasta temporária).
- Ronda real em 12/11: 3 notas → portas **139**, **445** e **7260**.
