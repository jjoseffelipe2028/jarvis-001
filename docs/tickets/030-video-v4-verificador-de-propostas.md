# 030 — Vídeos V4: verificador de propostas de marca
**Status:** ✅ construído e testado (12/11, lote pedido pelo José) · Java escrito pela 001 · ⏳ commit com o OK
**Módulo de Vídeos, fase V4 (cyber)**

## Ideia
"Chegou um e-mail de patrocínio: é de verdade ou querem roubar o canal?"
O verificador lê o **texto**, o **remetente** e a **marca** que a proposta diz ser, e responde com nível, motivos e passos.

## Como usar
```
python proposta.py --remetente contato@marca.com --marca "Nome da Marca"
```
Cole o texto do e-mail, escreva `FIM` numa linha sozinha e aperte Enter.

## Regras (fixas, sem IA)
- Texto: as mesmas regras do detector de golpe (Tickets 023 e 029).
- Remetente:
  - e-mail gratuito (Gmail, Outlook...) dizendo ser marca → suspeito;
  - endereço com o nome da marca **e algo a mais** (`marca-parcerias.com`) → imitação comum;
  - endereço que não bate com a marca → suspeito;
  - truques de endereço (punycode, final `.xyz`, imitar YouTube) → acusa;
  - formato estranho → conta ponto.
- A resposta traz sempre **5 passos** para conferir antes de fechar qualquer parceria.

## Segurança
- `POST /acoes/sentinela/proposta`: exige o pedido assinado, como toda rota. Vira evento no escritório.
- Só lê: nunca abre link, nunca manda e-mail, nada sai do PC.
- Resposta só com **textos fixos**: o texto, o remetente e a marca nunca voltam (sem XSS).
- Nada do pedido vai para o log (só nível e número de links).
- Limites: texto 4000, remetente 254, marca 60 → fora disso, 400.

## Arquivos
- Java: `VerificadorDePropostas`, `PropostaRequest`, rota no `SentinelaController`, evento no `EventosInterceptor`.
- Python: `bracos.proposta(...)` e o comando `proposta.py`.

## Evidências
- `VerificadorDePropostasTest` (11 testes, marcas inventadas) + 2 testes da rota + 9 testes Python.
- Teste real pela API: golpe de patrocínio → 🚨 com 6 motivos · proposta normal do domínio da marca → ✅.
