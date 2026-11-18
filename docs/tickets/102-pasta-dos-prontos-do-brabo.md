# Ticket 102 — pasta só com os vídeos prontos do Brabo

**Status:** ✅ feito (18/11/2026) · **Origem:** pedido do José ("uma pasta na área de trabalho que os vídeos vão quando são feitos")

## Problema
A pasta dos cortes mistura tudo: cortes crus, .srt, roteiros .json, pedidos, revisões e fotos (QUADROS-*).
Achar o vídeo final do Brabo no meio disso é difícil.

## O que mudou
- `brabo.py`: novo `entregar(video)`. Quando um vídeo do Brabo fica pronto, uma **cópia** dele e do `.post.txt`
  vai para `Brabo GTA prontos/`, ao lado dos cortes. O original continua onde estava.
- Nunca sobrescreve: nome repetido ganha `-2`, `-3`...
- Se a cópia falhar, só avisa (o vídeo já está pronto na pasta normal).
- Só o Brabo (modo `gta`); o Mestre Parafuso não entra.
- Atalho local "Cortes do Brabo (GTA)" na Área de Trabalho do José, apontando para a pasta. Fica fora do repositório
  (é do PC dele, com caminho pessoal).

## Testes
- `tests/test_entregar.py` (+2): copia vídeo e post sem mexer no original; nunca sobrescreve.
- Python: 435 testes OK.

## Pergunta de entrevista
Por que copiar em vez de mover o arquivo? (Dica: o resto do Jarvis ainda procura o vídeo no lugar original.)
