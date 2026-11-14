# 051 — Conector "Estúdio 001" (MCP local do Estúdio do Brabo)
**Status:** ✅ construído e testado (14/11) · pedido do José 14/11 · código pela 001 · ✅ commitado (este commit)

## Ideia
Em vez de digitar `brabo.py fazer ...`, o Claude chama o Estúdio direto por um **conector MCP nosso**, que roda no PC.
Ferramentas: `listar` · `ler` · `salvar` (roteiro) · `rascunho` (Gemma, GTA) · `fazer` (Short) · `ver_personagem`.

## Como ligar
- `.mcp.json` na raiz do jarvis-001 aponta para `python/estudio_mcp.py`. Abrindo o Claude Code no jarvis-001, ele pergunta se aprova o conector `estudio-001`: aprovar.
- Teste sozinho: `cd python && .venv/Scripts/python estudio_mcp.py --teste` (lista a pasta dos cortes).

## Segurança
- **stdio**: o Claude abre o processo; **nenhuma porta de rede** é aberta.
- Só a pasta dos cortes (`JARVIS_CORTES_SAIDA`), só **nome** de arquivo (sem `..`, `\`, `:`, atalho).
- Nunca sobrescreve. O roteiro passa pela mesma conferência do `brabo.py` (sem link, sem palavrão, tempos, tamanho).
- **Não existe ferramenta de postar** (um teste garante isso). Quem posta é o José, com o rótulo "sintético: SIM".
- Instalado: `mcp==2.3.0` do PyPI oficial (com OK do José). `requirements.txt` atualizado.

## Evidências
- `tests/test_estudio_mcp.py`: 8 testes OK. Suíte Python inteira: 294 testes OK.
- Teste real pelo stdio (JSON-RPC): `tools/list` → 6 ferramentas; `listar` devolve os cortes; `ler("../x.mestre.json")` → erro "Passe só o NOME".
