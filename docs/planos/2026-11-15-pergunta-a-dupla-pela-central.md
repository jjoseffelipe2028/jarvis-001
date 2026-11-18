# Plano — Ticket 072: perguntar à dupla pela Central

_Pedido do José (15/11): "pergunta lá direto, ela já pergunta para vocês dois e manda a resposta". Plano da 001; vai para a 002 contestar antes de qualquer código._

## Problema
No escritório da Central, o cartão da 002 tem 3 botões "em breve" (`escritorio.js`, ramo `claude/jarvis-central-1995fa`). A 002 já funciona no terminal (`/dupla`, ticket 070), mas a tela não sabe disso. E o José quer fazer **uma pergunta** e receber a resposta das duas IAs juntas.

## Regra que não muda 🔒
A API Java **nunca chama IA da nuvem** (nem `agy`, nem `claude`). O navegador só **prepara** e **anota**; quem aciona a nuvem é o José, no Claude Code, com o `/dupla`. Motivo: uma página maliciosa aberta no PC não pode fazer a nuvem trabalhar (CSRF, gasto de limite, vazamento de texto do cofre).

## O caminho em 1 linha por pasta (achado 5 da 002)
- `pedidos/` (cofre) = **fila de entrada**: a pergunta que o José confirmou na tela.
- `docs/planos/` = **rascunho da 001**: a resposta dela, antes da 002.
- `docs/reviews/` = **palavra final da dupla**: contestação da 002 + veredito. É o único lugar que o painel lê.

## Como funciona (3 partes, cada uma testável sozinha)
**A. Botões da 002 viram "via terminal"** (só tela)
- "Contestar plano" e "Revisar código" usam o mesmo `prepararNuvem` dos botões da 001: o cartão mostra o texto pronto (`/dupla plano` ou `/dupla codigo`). **Sem alvo, o `/dupla` pega o plano mais recente de `docs/planos/` e diz qual é antes de chamar a 002** (achado 4) e o **Aceitar** só copia para o José colar no Claude Code.
- "Pesquisar fontes" continua "em breve" (não existe ainda).

**B. "pede pra dupla: <pergunta>"** (reaproveita a caixa de pedidos do ticket 060)
- `RoteadorDoChat` ganha a regra fixa `PEDIDO_DUPLA`, irmã da `PEDIDO_001`. Mesmo cartão Revisar → Confirmar.
- `PedidoService` grava a nota com `para: dupla` no cabeçalho (mesma pasta, mesmo nome gerado no servidor, mesmo desarme `TextoSeguro`, mesmo limite de 30/dia).
- No Claude Code, a 001 lê a caixa, responde a pergunta num arquivo `docs/planos/AAAA-MM-DD-slug.md`, e o `/dupla` manda para a 002 contestar (novo modo `pergunta` no `chamar_002.py`, com pedido fixo, só caminho de arquivo, máximo 2 rodadas). **O modo `pergunta` grava a conclusão em `docs/reviews/` com o veredito `RESPONDIDO`** (achado 2).
- Nome já existe no mesmo dia → sufixo `-2`, `-3`, nunca sobrescreve (achado 4).

**C. Painel "Última palavra da dupla"** (só leitura)
- `GET /central/acoes/dupla` devolve a lista dos 5 registros mais novos de `docs/reviews/`: data, título e veredito. **Nunca o texto inteiro.**
- Veredito é um `enum` fixo: `APROVO`, `AJUSTES`, `NAO_APROVO`, `RESPONDIDO` ou `EM_ANALISE` (arquivo ainda sem veredito → `EM_ANALISE`, nunca erro 500) (achados 2 e 3).
- Título só se a 1ª linha começar com `# Registro da dupla — `, cortado em 80 caracteres; senão o arquivo é ignorado (achado 1).
- Pasta da configuração (`JARVIS_PASTA_REVIEWS`), vazia → 503. **O caminho tem que terminar em `docs/reviews`**, senão 503: um `.env` errado não vira janela para outra pasta (achado 1). Nome só `AAAA-MM-DD-slug.md` (regex), `PastaSegura` anti `../`, sem seguir atalho, arquivo lido no máximo 64 KB.
- Tela mostra com `textContent` (anti XSS).

## Fora deste ticket
- A API rodar a 002 sozinha (decisão de segurança: nunca, ver ADR 008 e 009).
- Pesquisar fontes. Issues e PR (parte B do 070).

## Testes
- JUnit: `RoteadorDoChatTest` (+3 para `PEDIDO_DUPLA`, incluindo "pede pra dupla: instala X" = recado, não ordem), `PedidoServiceTest` (+2: cabeçalho `para: dupla`, desarme), `DuplaServiceTest` (lista certa, nome fora do padrão ignorado, 1ª linha fora do padrão ignorada, título cortado em 80, sem veredito → `EM_ANALISE`, 2 rodadas → vale o veredito da **última** (achado da rodada 2), `../`, atalho, arquivo gigante, sem pasta → 503, pasta que não termina em `docs/reviews` → 503), `CentralDuplaControllerTest` (200, sem crachá → 401, sem texto inteiro na resposta).
- Python: `test_chamar_002` (+4: modo `pergunta` aceito, caminho fora de `docs/planos` recusado, sem alvo pega o mais recente, nome repetido ganha sufixo).
- Navegador: botão vira "via terminal", pergunta vira nota, painel mostra o veredito.

## Onde construir
No ramo `claude/jarvis-central-1995fa` (depende do ticket 060, que só existe lá), depois que o José decidir o merge dele. Até lá, só este plano.

## Rodada 2
Os 5 achados da rodada 1 foram aplicados (marcados no texto). Pergunta para a 002: sobrou algum caminho em que texto do cofre ou do repositório chega na tela sem passar pelo `enum` ou pelo corte de 80?
