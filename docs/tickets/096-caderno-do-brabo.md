# Ticket 096 — Caderno do Brabo (vocabulário e ideias)

**Status:** ✅ feito (17/11/2026) · **Origem:** pedido do José ("melhorar o vocabulário e colocar mais ideias pra ele falar")

## Problema
A Gemma só recebia uma instrução genérica de 6 linhas. Sem repertório, ela repetia as mesmas frases,
e o canal corria risco de parecer "feito por molde" (ver Ticket 095).

## O que mudou
- `caderno_brabo.json` (novo, **editável pelo José**): 5 listas com o repertório do Brabo:
  - `girias` (16), `comparacoes` (8), `vereditos` (6), `tipos_de_piada` (7), `quadros` (5:
    Tribunal, Nota, Crônica, Plantão, Aula).
- `caderno.py` (novo): `carregar` confere o caderno (até 80 letras, sem link, sem @, sem palavrão,
  sem símbolo de legenda); `sortear` escolhe 1 quadro, 1 estilo, 3 gírias, 1 comparação e 1 veredito;
  `tempero` vira o texto que vai junto das instruções.
- `brabo.py rascunho`: cada rascunho leva um **tempero sorteado diferente**.
  - O tempero vai nas **instruções** (sistema); a fala da live continua separada como DADO.
  - Caderno estragado → avisa e segue sem tempero (não trava o rascunho).
- Regra do caderno: julgar **a jogada, nunca a pessoa**.

## Testes
- `tests/test_caderno.py` (7): caderno real válido, item ruim recusado, repetido some, sorteio varia,
  arquivo estragado, tempero nas instruções e fala separada, sem tempero fica como antes.
- Suíte: 402 OK.

## Limites (⚠️)
- A Gemma pequena (e2b) pode ignorar parte do tempero. Ainda não testado com a Gemma de verdade.
- O quadro sorteado é só sugestão: o roteiro ainda não guarda qual quadro usou (rodízio fica para depois).
