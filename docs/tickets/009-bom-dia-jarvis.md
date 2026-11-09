# 009 — "Bom dia, Jarvis" (Fase 4, parte 3: rotina)
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001

## O que faz
Você diz (ou digita) **"bom dia, Jarvis"**, "boa noite", "resumo do dia" ou "como tá meu dia?" e ele responde, por exemplo:
> "Bom dia, José! São 08:10. Você tem 16 pendências. A primeira: Cyber EBAC — 30 min. No Jarvis tem 3 arquivos sem commit. E a Cyber? Reserve 30 minutos para ela hoje."

- Junta 3 braços: **hora** (cumprimento certo), **pendências** (quantas + a primeira), **git-status**.
- No fim, **sempre** cobra a Cyber (regra do projeto: a Cyber nunca fica 2 dias parada).
- Cada braço pode falhar sozinho: o que der certo é falado.
- **Só lê.** O teste garante que a rotina nunca chama o `registrar_daily`.
- Só vale a **frase inteira**: "bom dia, me explica o que é API" continua sendo conversa com o Gemma.

## Escopo
NOVO `python/rotinas.py` + `python/tests/test_rotinas.py` (10 testes) · `intencoes.py` (regra `_BOM_DIA`) · `chat.py` (ramo `bom_dia`).

## Evidências
- Python **60** testes, 0 falhas.
- Revisão `jarvis-hacker` (2026-11-09): **✅ aprova**; corrigidos: alarme de impostor/401 agora **interrompe** o resumo ("Atenção, José: ...") e número infinito não derruba. Aceito: um "bom dia" da TV faz o Jarvis ler o resumo (só leitura).
- **Teste real** (API numa porta separada, token fictício): "Boa noite, José! São 03:04. Você tem 25 pendências. A primeira: ... No Jarvis tem 28 arquivos sem commit. E a Cyber? ..." ✅ · com token errado: "Atenção, José: Quem respondeu na porta da API não provou ser o Jarvis." ✅
- Teste manual: _(José: `python chat.py` → "bom dia, Jarvis")_
- Commit: `19e2ff0` (2026-11-09, aprovado pelo José após teste real por voz)

## 🎤 Pergunta para o José
> Se a API Java estiver desligada, o que o "bom dia" responde, e por que isso é melhor do que dar erro?

**Resposta do José:** _(explicada pela 001 em 09/11: ele avisa que a API está desligada e ainda cobra a Cyber; melhor do que travar, porque fala o que dá)_
