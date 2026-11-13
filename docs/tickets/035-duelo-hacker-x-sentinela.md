# 035 — Duelo: Hacker × Sentinela (Red × Blue)
**Status:** ✅ construído, testado e rodado de verdade (12/11) · ⏳ commit com o OK

## Ideia
O "nosso próprio hacker" ataca uma **cópia de teste** da API e a defesa tem que barrar cada ataque. No fim, o placar:
`O hacker tentou 13 ataques. A defesa pegou 13.` Ataque que escapa vira ticket 🔴.

```
python duelo.py
```

## Os 13 ataques (cada um com a técnica do MITRE)
| Ataque | Técnica | Defesa esperada |
|---|---|---|
| Entrar sem token | T1190 | 401 |
| Adivinhar o token (5 chutes) | T1110 | 401 em todos |
| Usar o token-isca | T1552 | 401 + alarme da isca |
| Repetir pedido copiado (replay) | T1550 | 2º pedido 401 |
| Trocar o corpo depois de assinado | T1565.002 | 401, nada gravado |
| Site de fora (Host falso) | T1557 | 403 |
| Fugir da pasta pelo endereço (`../`) | T1083 | não devolve arquivo |
| JSON gigante no daily | T1499 | recusa |
| Injetar linha falsa no daily | T1565.001 | linha não entra |
| Travar o detector de golpe (ReDoS) | T1499 | responde em menos de 2 s |
| Código na resposta (XSS) | T1059.007 | resposta sem o código |
| DELETE sem token | T1190 | recusa |
| Convencer o verificador (prompt injection) | ATLAS AML.T0051 | continua "alto" |

## Segurança (regras do duelo)
- Só ataca `127.0.0.1:8091`, uma cópia que **o próprio duelo liga e desliga**. Se já tiver algo nessa porta, ele não ataca.
- A cópia usa token **falso sorteado na hora**, isca falsa e **pastas temporárias**. Ela não lê o `.env`:
  as variáveis `JARVIS_` de verdade são tiradas do ambiente antes de ligar (tem teste).
- Nunca toca na API de verdade (8080), no cofre nem em outra máquina. Os tokens do duelo não aparecem na nota.

## 🐛 Achado do primeiro duelo de verdade
- **12 de 13.** O ataque de ReDoS passou: o detector de golpe levava **2,6 s** para um texto de 4000 letras `a.a.a...`.
- O conserto do Ticket 029 tinha tirado o erro 500, mas o regex ainda "voltava atrás" milhares de vezes.
- **Conserto:** o regex só separa os pedaços do texto em uma passada (sem voltar atrás) e o código decide se o pedaço é link.
- **Prova:** teste de tempo (500 ms) com 6 textos de ataque + segundo duelo: **13 de 13**.
- Lição: o duelo achou em 1 minuto o que os testes de unidade não acharam.

## Evidências
- `test_duelo.py`: 6 testes. Uma API falsa com 13 "furos": cada furo é percebido pelo duelo.
- Notas reais em `incidentes/`: `2026-11-12-215502-duelo.md` (12 de 13) e `2026-11-12-215658-duelo.md` (13 de 13).

## Depois
- Rodar sozinho de madrugada e falar o placar no "bom dia" (outro ticket).
