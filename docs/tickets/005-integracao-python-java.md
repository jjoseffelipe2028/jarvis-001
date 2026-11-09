# 005 — Integração: o cérebro (Python) pede, os braços (Java) fazem
**Status:** 🏁 pronto (2026-11-09) — testado pelo José no `chat.py`
**Criado:** 2026-11-09 pela 001 · Fase 4, parte 1

## O que faz
No `chat.py` (digitando ou falando):
| Você diz | O Jarvis faz | Ele responde |
|---|---|---|
| "anota no daily: estudei Git" | `POST /acoes/daily` | "Anotado no daily de hoje." |
| "quais minhas pendências?" | `GET /acoes/pendencias` | "Você tem 13 pendências. As primeiras: ..." |
| "tenho algo sem commit?" | `GET /acoes/git-status` | "Na branch main você tem 2 arquivos alterados sem commit." |
| "que horas são?" | `GET /acoes/hora` | "Agora são 01:10 de 09/11/2026." |
| "apaga a pasta", "instala X", "roda ..." | **nada** | "Não posso fazer isso. Só executo as ações da minha lista." |
| qualquer outra coisa | conversa com o Gemma | (como antes) |

## Escopo
**Entrou:** `python/bracos.py` (ponte), `python/intencoes.py` (regras), `chat.py` ligado, `python/tests/` (29 testes), robô `testes-python.yml` no GitHub.
**Não entrou:** "bom dia do Jarvis" (rotina automática), marcar pendência como feita, central web.

## 🔒 Segurança
| Risco | Trava |
|---|---|
| IA decidindo executar algo | O **Gemma nunca escolhe ação**: regras fixas (regex) + allowlist na API Java |
| Pedido perigoso por voz ("apaga...", "instala...") | Recusado antes de chegar em qualquer lugar |
| Token vazando | Endereço fixo `127.0.0.1`, **proxy do PC ignorado**, **redirecionamento recusado**, token nunca impresso (`repr` = `***`) |
| Nota com texto malicioso ("ignore suas regras...") virando ordem para a IA | Resultado das ações **não entra no histórico do Gemma**; vai só para a tela e a voz |
| Mensagem estranha da API sendo repetida | Erros viram **frases fixas**; mensagem desconhecida não é repetida |
| API respondendo lixo ou gigante | Só JSON, no máximo 1 MB, 10 s |
| Biblioteca nova (supply chain) | **Nenhuma**: só biblioteca padrão do Python |

## Evidências
- `python -m unittest discover -s tests -t .`: **38 testes, 0 falhas** (API falsa local; nunca toca a de verdade nem o `.env`).
- `./mvnw test` (Java): 134, sem mudança.
- Revisão `jarvis-hacker` (2026-11-09): ❌ reprovação branda (0 🔴, 5 🟡, 5 🟢) → **corrigido e retestado**:
  1. 🟡 Programa impostor na porta 8080 (com a API desligada) receberia o token → **ticket próprio** (aperto de mão: a API prova que conhece o segredo antes do token ir).
  2. 🟡 Erro inesperado derrubava o Jarvis / token torto podia aparecer no traceback → **corrigido** (formato do token validado + `except` geral com frase fixa).
  3. 🟡 Texto de nota com ESC reescrevendo o terminal → **corrigido** (`seguro()` tira controle e limita 400).
  4. 🟡 Resposta torta da API dava crash → **corrigido** (`responder` nunca quebra).
  5. 🟡 Voz da TV gravando no daily → **corrigido** (por voz, pede "s/n" antes de gravar).
  6. 🟢 "Me explica o que é um commit" virava ação → **corrigido** (só pergunta sobre mim vira ação).
  7. 🟢 Gemma podia dizer "pronto, apaguei" → **corrigido** na personalidade.
  8. 🟢 "İ" cortava o texto errado → **corrigido** + teste.
  9. 🟢 `.env` com BOM → **corrigido** (`utf-8-sig`).
  10. 🟢 Endereço aceitava qualquer URL → **corrigido** (só `http://127.0.0.1:<porta>`). Prazo total da leitura: depois.
- Teste manual (José, 2026-11-09, API real + `chat.py`):
  - "quais minhas pendências?" → 🦾 "Você tem 16 pendências. As primeiras: ..." + falou ✅
  - "tenho algo sem commit?" → "2 arquivos alterados e 5 arquivos novos" (bate com o `git status`) ✅
  - "apaga a pasta do projeto" → recusado ✅
  - "anota no daily, testei o jarvis por voz" → criou `2026-11-09.md` com `- 02:09 testei o jarvis por voz` ✅
  - Observado: ouvido lento e erra (melhorar: fone, modelo `medium`, CUDA) · voz `faber` com sotaque (trocar por outra pt_BR oficial).
- Commit: _(preencher)_

## ▶️ Como testar (José)
1. 1ª janela: API rodando (`./iniciar.sh`).
2. 2ª janela: `cd python && source .venv/Scripts/activate && python chat.py`
3. Digite: `quais minhas pendências?` · `tenho algo sem commit?` · `anota no daily: testei o Jarvis por voz` · `apaga tudo`
4. Depois, aperte Enter vazio e **fale** um deles. 🎙️

## 🎤 Pergunta para o José
> Por que o resultado das pendências **não** vai para o histórico do Gemma, se ia deixar a conversa mais "esperta"?

**Resposta do José:** _(preencher)_
