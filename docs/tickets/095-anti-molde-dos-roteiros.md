# Ticket 095 — Anti-molde dos roteiros do Brabo

**Status:** ✅ feito (17/11/2026) · **Origem:** pesquisa "Estratégia do Brabo no GTA 6" (melhoria nº 6 dos cortes)

## Problema
O YouTube tira o anúncio do **canal inteiro** quando ele parece feito "em massa, por molde"
(conteúdo inautêntico, regra do YPP). Nada impedia o Brabo de gravar o mesmo roteiro com outras palavras.

## O que mudou
- `molde.py` (novo): histórico local `.historico-roteiros.jsonl` na pasta dos cortes.
- `python brabo.py fazer ...` confere **antes de gravar** e barra com motivo claro:
  1. **Abertura repetida:** as 6 primeiras palavras (sem o bordão) iguais às de um dos últimos 30 roteiros.
  2. **Parecido demais:** 35% ou mais dos trechos de 3 palavras em comum com um recente.
- O **bordão pode repetir** ("Chegou o Brabo, e hoje ninguém escapa!" e o curto "Chegou o Brabo"): é a marca.
- Compara só com o **mesmo canal** (Brabo com Brabo, Mestre com Mestre).
- Só entra no histórico **depois do vídeo pronto**. Refazer o mesmo roteiro depois de uma falha não se barra sozinho.
- Linha estragada no histórico é pulada (não derruba).

## Testes
- `tests/test_molde.py` (8): normalizar, parecido, roteiro novo passa, abertura barra, parecido barra,
  outro canal e mesmo arquivo, só os 30 recentes, histórico com linha estragada.
- O teste achou um furo real: o bordão curto "Chegou o Brabo!" não era tirado. Corrigido.
- Suíte: 392 OK.

## Limites (⚠️)
- Compara **palavras**, não ideias: a mesma piada com outras palavras passa.
- Rodízio de quadros (Tribunal, Nota, Crônica) fica para depois: o roteiro ainda não tem o campo "quadro".
- Próximo: Ticket 096, caderno do Brabo (vocabulário sorteado para a Gemma), que ataca a causa da repetição.
