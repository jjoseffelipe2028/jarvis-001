# 067 — Estúdio de pesquisa (049 da lista) e Unity pelo MCP oficial (050 da lista) 🔎🎮
**Status:** 📝 só plano · ⏳ construir depois, com OK do José · seção 1 da lista mestra
_Os números 049/050 da lista mestra já estão ocupados por outros tickets no repositório; por isso este é o 067._

## A) Estúdio de pesquisa 🔎
**Ideia:** na Central, "pesquisa para mim: <tema>" → Revisar → Confirmar → a dupla pesquisa e entrega uma nota em `knowledge/pesquisas/`.

**Plano (mesmo molde do Estúdio 001, Ticket 047):**
1. Rota `POST /central/acoes/pesquisa` com `{"tema":"..."}` (`@Size(max=300)`, texto desarmado).
2. Comando fixo: a 001 (`claude -p`) recebe o tema num **arquivo** (nunca colado no comando: evita Command Injection),
   **com web liberada só para leitura** e **sem terminal**, sem MCP, sem segredos (`Processos` com `semSegredos=true`).
3. A 002 revisa as fontes (veredito ✅/⚠️/❌, como no `GEMINI.md`).
4. Quem grava a nota é o **Java** (pasta da configuração, nome gerado pelo servidor, `CREATE_NEW`).

**Riscos e decisões que são do José:**
- Web ligada = **prompt injection** vindo das páginas. Mitigação: o agente não tem ferramenta de escrita nem terminal;
  a nota marca "conteúdo de fora, conferir antes de usar".
- Custo: cada pesquisa gasta tokens da assinatura. Sugestão: limite de 3 por dia (429).

## B) Unity pelo MCP oficial 🎮
**Ideia:** a dupla cria jogos 3D na Unity, como já faz no Roblox (`Roblox_Studio` MCP).

**Plano:**
1. **Antes de tudo (OK do José):** confirmar qual é o servidor MCP **oficial da Unity** (site/GitHub da Unity Technologies),
   o que ele instala, se manda dado para fora e quais permissões pede. Nada de MCP de terceiros.
2. Rodar só no projeto `workSpace/estudio-jogos/unity/`, nunca em outra pasta.
3. Começar com um jogo mínimo (cubo que pula + placar) para medir se vale a pena.
4. Botão no Estúdio 001: "motor: Roblox | Unity" (lista fechada).

**Precisa do José:** instalar a Unity/Unity Hub e o pacote MCP (download grande + conta Unity). **Nada é instalado sem OK.**

## Pronto quando (quando for construir)
Cada parte com o próprio ticket de construção, testes e o fluxo de sempre.
