# Registro da dupla — Ticket 072: perguntar à dupla pela Central

- **Plano:** `docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md`

## Arquivos alterados
(nada ainda: só plano)

## Testes
(nada ainda: só plano)


## 002 — revisão do plano, rodada 1 (20:03) → AJUSTES

### 1. Veredito: ⚠️ Aprovo com ajustes

*(Nota: Não conferi os arquivos do ticket 060, `chamar_002.py` nem `escritorio.js` porque não estão neste ramo atual; analisei com base em [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md) e [2026-11-15-pergunta-a-dupla-pela-central.md](docs/reviews/2026-11-15-pergunta-a-dupla-pela-central.md).)*

---

### 2. Achados

1. **[Segurança] Leitura de pasta do repositório via HTTP (resposta à pergunta da 001)**
   - **onde**: [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L21-L24) e [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L38-L39)
   - **o que acontece**: Sim, a parte C abre uma superfície que a parte B não abre: a B só **escreve** recados numa caixa isolada, enquanto a C **lê** arquivos do repositório Git e devolve na API. Se `JARVIS_PASTA_REVIEWS` apontar por engano para outra pasta com arquivos `.md` ou se um arquivo tiver dados sensíveis na primeira linha, o extrator de "título" vaza esse trecho no `GET /central/acoes/dupla` (*Information Disclosure*: quando a API entrega mais informação interna do que deveria).
   - **como corrigir**: Validar que `JARVIS_PASTA_REVIEWS` termina obrigatoriamente em `docs/reviews`, exigir que a 1ª linha comece com `# Registro da dupla — ` (cortando em 80 caracteres) e retornar o veredito apenas como valor fixo (`enum`).

2. **[Passo que vai travar] A resposta da pergunta vai para `docs/planos/`, mas o painel C lê `docs/reviews/`**
   - **onde**: [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L19-L22)
   - **o que acontece**: Na parte B, a 001 responde à pergunta em `docs/planos/AAAA-MM-DD-slug.md`, mas o painel "Última palavra da dupla" (parte C) lê apenas `docs/reviews/` buscando `APROVO / AJUSTES / NAO_APROVO`. Quando o José fizer uma pergunta aberta (ex.: "qual estrutura de dados usar?"), ela não terá veredito de aprovação ou não será gravada em `docs/reviews/`, nunca aparecendo no painel C.
   - **como corrigir**: Definir no plano que o modo `pergunta` grava a conclusão da dupla em `docs/reviews/AAAA-MM-DD-slug.md` e incluir o estado `RESPONDIDO` (e `EM_ANALISE`) além dos 3 vereditos.

3. **[Teste faltando] Arquivo em `docs/reviews/` ainda sem veredito quebra ou some na listagem**
   - **onde**: [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L30-L31) e [2026-11-15-pergunta-a-dupla-pela-central.md](docs/reviews/2026-11-15-pergunta-a-dupla-pela-central.md#L1-L9)
   - **o que acontece**: Como no próprio registro de hoje ([2026-11-15-pergunta-a-dupla-pela-central.md](docs/reviews/2026-11-15-pergunta-a-dupla-pela-central.md#L1-L9)), o arquivo nasce em `docs/reviews/` antes de existir um veredito. O plano não prevê teste no `DuplaServiceTest` para arquivo válido porém ainda sem veredito no texto, o que pode causar erro 500 ou `NullPointerException` quando a Central consultar a rota durante uma revisão.
   - **como corrigir**: Incluir no `DuplaServiceTest` o caso de arquivo `.md` sem veredito preenchido, devolvendo `EM_ANALISE` com status HTTP 200.

4. **[Passo que vai travar] Comando `/dupla` sem alvo e colisão de nome no mesmo dia**
   - **onde**: [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L13-L19)
   - **o que acontece**: O botão copia `/dupla plano` fixo sem nome de arquivo (se houver 2 planos no dia, qual ele contesta?) e duas perguntas feitas no mesmo dia com temas parecidos podem gerar o mesmo `AAAA-MM-DD-slug.md`, sobrescrevendo a anterior.
   - **como corrigir**: Definir que o `/dupla` sem argumento pega o arquivo mais recente (mostrando qual é) e que a criação do arquivo adiciona sufixo (`-2`, `-3`) se o nome já existir.

5. **[Entrevista] Três pastas e dois fluxos diferentes para conversar com a dupla**
   - **onde**: [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L12-L24)
   - **o que acontece**: Os botões da parte A copiam texto para o teclado, a pergunta da parte B salva nota na pasta de pedidos, a 001 responde em `docs/planos/` e o painel C lê `docs/reviews/`. Se um entrevistador perguntar "como o pedido sai da tela e chega na resposta?", ter 3 pastas no caminho parece complexo demais sem uma explicação clara do papel de cada uma.
   - **como corrigir**: Adicionar no plano um resumo de 1 linha por pasta (`pedidos/` = fila de entrada, `docs/planos/` = rascunho da 001, `docs/reviews/` = veredito final da dupla).

---

### 3. Pergunta para o José
Se numa entrevista o recrutador perguntar **"Por que a sua API Java não chama o comando da IA direto pelo `ProcessBuilder` quando você clica no botão da tela, em vez de só anotar o pedido?"**, como você explicaria o risco que isso evita?


## 002 — revisão do plano, rodada 2 (20:04) → APROVO

### 1. Veredito: ✅ Aprovo

Os 5 pontos da rodada 1 foram resolvidos em [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md).

**Resposta à pergunta da 001 ([2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L46-L47)):**
Não sobrou nenhum caminho. Os três campos que saem em `GET /central/acoes/dupla` estão travados:
- **data**: vem do nome do arquivo validado por regex (`AAAA-MM-DD-slug.md`);
- **título**: exige o prefixo `# Registro da dupla — ` na 1ª linha, corta em 80 caracteres e entra na tela via `textContent` (evita **XSS**, que é quando um texto vira código executável no navegador);
- **veredito**: sai apenas como valor do `enum` fixo.

---

### 2. Achados

Apenas 1 cuidado de implementação para não travar na hora de codar (não bloqueia o plano):

1. **[Passo que pode travar no código] Arquivo de review com 2 rodadas precisa ler o último veredito, não o primeiro**
   - **onde**: [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md#L28-L29) e [2026-11-15-pergunta-a-dupla-pela-central.md](docs/reviews/2026-11-15-pergunta-a-dupla-pela-central.md#L12)
   - **o que acontece**: Um arquivo em `docs/reviews/` acumula a rodada 1 (ex.: `→ AJUSTES`) em cima e a rodada 2 (ex.: `→ APROVO`) embaixo. Se o `DuplaService` pegar a primeira linha de veredito que encontrar no arquivo, o painel da Central vai mostrar `AJUSTES` para sempre mesmo depois de aprovado na rodada 2.
   - **como corrigir**: Na implementação do `DuplaService` (e no `DuplaServiceTest`), ler o veredito da **última** rodada registrada no arquivo.

---

### 3. Pergunta para o José

Se numa entrevista te perguntarem **"Como você garantiu que a rota `GET /central/acoes/dupla` nunca vaze o conteúdo das suas notas privadas se alguém configurar a pasta errada no `.env`?"**, quais são as travas do seu código que você citaria?

## Construção — lado Python (main, 15/11)
_A tela e o Java (partes A, B-Java e C) estão sendo construídos pela conversa jarvis-central no ramo `claude/jarvis-central-1995fa`. Aqui é só o lado do `/dupla`._

## Arquivos alterados
.claude/skills/dupla/scripts/chamar_002.py
.claude/skills/dupla/scripts/novo_registro.py
.claude/skills/dupla/tests/test_chamar_002.py
.claude/skills/dupla/SKILL.md

## Testes
- `python -m unittest discover -s .claude/skills/dupla/tests` → **8 passaram, 0 falharam**.
- Manual: `chamar_002.py pergunta docs/planos/x.md` (só um caminho) → recusado com "Passe os dois caminhos (plano e registro) ou nenhum."


## 002 — revisão do codigo, rodada 1 (20:12) → AJUSTES

### 1. Veredito: ⚠️ Aprovo com ajustes

*(Nota: Conferi os 4 arquivos do lado Python listados no registro. Não conferi a tela nem o Java — partes A, B-Java e C do plano — porque estão sendo construídos no ramo `claude/jarvis-central-1995fa`.)*

---

### 2. Achados

1. **[Segurança — Command Injection] `novo_registro.py` recebe título livre no comando e está auto-liberado no `allowed-tools`**
   - **onde**: [SKILL.md](.claude/skills/dupla/SKILL.md#L6), [SKILL.md](.claude/skills/dupla/SKILL.md#L27) e [novo_registro.py](.claude/skills/dupla/scripts/novo_registro.py#L46-L53)
   - **o que acontece**: No Git Bash, texto entre aspas duplas (`"Título"`) ainda executa crases (`` `...` ``) e `$(...)` antes mesmo de o Python iniciar (*Command Injection*: quando um texto vira comando executado no sistema). Como `Bash(python .claude/skills/dupla/scripts/novo_registro.py:*)` entrou no `allowed-tools`, se uma pergunta da caixa de pedidos contiver `$(...)` ou crase e for passada como `"Título"` no terminal, ela roda sem pedir permissão ao José.
   - **como corrigir**: Fazer o [novo_registro.py](.claude/skills/dupla/scripts/novo_registro.py#L46-L53) aceitar apenas o `<slug>` na linha de comando (gerando o título inicial a partir do próprio slug) e deixar o título completo para a edição do arquivo (`Write`/`Edit`, que pede permissão).

2. **[Bug] `veredito()` procura `(❌, ⚠️, ✅)` nos primeiros 400 caracteres inteiros em vez de olhar a linha do veredito**
   - **onde**: [chamar_002.py](.claude/skills/dupla/scripts/chamar_002.py#L94-L99)
   - **o que acontece**: Se a 002 responder `### 1. Veredito: ✅ Aprovo` e, logo abaixo (dentro dos primeiros 400 caracteres), comentar `"Os itens que tinham ⚠️ ou ❌ na rodada 1 foram corrigidos"`, o `for` testa `❌` e `⚠️` primeiro na fatia inteira e grava `NAO_APROVO` ou `AJUSTES` em vez de `APROVO` / `RESPONDIDO`.
   - **como corrigir**: Buscar o emoji apenas na linha que contém `Veredito` (ou pegar o emoji que aparece primeiro no texto com `texto[:400].find(marca)`), adicionando esse caso em [test_chamar_002.py](.claude/skills/dupla/tests/test_chamar_002.py#L40-L44).

3. **[Bug] `mais_recente()` não confere se o arquivo par existe em `docs/reviews/`**
   - **onde**: [chamar_002.py](.claude/skills/dupla/scripts/chamar_002.py#L74-L81)
   - **o que acontece**: A docstring de `mais_recente()` diz que o registro precisa existir com o mesmo nome em `docs/reviews/`, mas a linha 77 filtra apenas `docs/planos/*.md`. Se houver um rascunho mais novo em `docs/planos/` ainda sem par em `docs/reviews/`, rodar `chamar_002.py plano` sem caminhos escolhe esse rascunho e falha em seguida com `"Arquivo não existe"`.
   - **como corrigir**: Incluir `(raiz / "docs" / "reviews" / p.name).is_file()` no filtro da linha 77 de [chamar_002.py](.claude/skills/dupla/scripts/chamar_002.py#L77) e testar esse cenário em [test_chamar_002.py](.claude/skills/dupla/tests/test_chamar_002.py#L53-L63).

4. **[Passo que pode travar] `SKILL.md` instrui a usar `<slug>.md` fixo mesmo quando `novo_registro.py` gera sufixo `-2`**
   - **onde**: [SKILL.md](.claude/skills/dupla/SKILL.md#L25-L35) e [SKILL.md](.claude/skills/dupla/SKILL.md#L56-L57)
   - **o que acontece**: Se o `<slug>` já existir no dia, `novo_registro.py` cria `<slug>-2.md`, mas os passos seguintes do `SKILL.md` mandam preencher e passar `docs/planos/<slug>.md` para o `chamar_002.py`, fazendo a 001 editar ou enviar o arquivo antigo.
   - **como corrigir**: Ajustar o [SKILL.md](.claude/skills/dupla/SKILL.md#L29-L35) para usar nos passos seguintes o caminho exato devolvido por `novo_registro.py` (`Criados: docs/planos/<nome>.md ...`).

---

### 3. Pergunta para o José

No [novo_registro.py](.claude/skills/dupla/scripts/novo_registro.py#L36-L39), os arquivos são abertos com `open("x")` em vez de `open("w")`. Se um entrevistador te perguntar **qual é a diferença entre `"x"` e `"w"` e que problema o `"x"` evita quando dois pedidos chegam no mesmo dia**, como você explicaria?

## Correção da 001 (rodada 1 do código): 4 achados, todos aceitos
1. **Aceito.** `novo_registro.py` recebe só o slug; o título sai do slug (só letras, números e espaço). Texto livre nunca passa pelo comando.
2. **Aceito.** `veredito()` lê a linha que tem "Veredito"; sem ela, vale a marca que aparece primeiro. Teste novo com comentário "⚠️ ou ❌" abaixo de um ✅.
3. **Aceito.** `mais_recente()` só considera plano que tem registro com o mesmo nome. Teste com rascunho mais novo sem registro.
4. **Aceito.** SKILL.md manda usar o caminho que o script imprimiu (pode ter `-2`).

## Testes (depois da correção)
- `python -m unittest discover -s .claude/skills/dupla/tests` → **9 passaram, 0 falharam**.
- Manual: `novo_registro.py 2026-11-15-x titulo` (2 argumentos) → recusado ("só o slug").


## 002 — revisão do codigo, rodada 2 (20:14) → APROVO

### 1. Veredito: ✅ Aprovo

*(Nota: Conferi os 4 arquivos do lado Python do `/dupla`: [chamar_002.py](.claude/skills/dupla/scripts/chamar_002.py), [novo_registro.py](.claude/skills/dupla/scripts/novo_registro.py), [test_chamar_002.py](.claude/skills/dupla/tests/test_chamar_002.py) e [SKILL.md](.claude/skills/dupla/SKILL.md). Não conferi a tela nem o Java — partes A, B-Java e C de [2026-11-15-pergunta-a-dupla-pela-central.md](docs/planos/2026-11-15-pergunta-a-dupla-pela-central.md) — porque estão sendo construídos no ramo `claude/jarvis-central-1995fa`.)*

---

### 2. Achados

Nenhum achado pendente. Os 4 pontos da rodada 1 foram corrigidos e cobertos por testes em [test_chamar_002.py](.claude/skills/dupla/tests/test_chamar_002.py):
- [novo_registro.py](.claude/skills/dupla/scripts/novo_registro.py#L54-L55) aceita somente o slug validado por regex (`SLUG_OK`), eliminando texto livre na linha de comando.
- [chamar_002.py](.claude/skills/dupla/scripts/chamar_002.py#L98-L109) lê a marca na linha que contém `Veredito` (ou a primeira marca no topo se não houver essa linha) e converte `APROVO` em `RESPONDIDO` no modo `pergunta`.
- [chamar_002.py](.claude/skills/dupla/scripts/chamar_002.py#L77-L78) exige que o par exista tanto em `docs/planos/` quanto em `docs/reviews/` ao buscar o plano mais recente.
- [SKILL.md](.claude/skills/dupla/SKILL.md#L29-L30) instrui a usar o caminho impresso por `novo_registro.py`.

---

### 3. Pergunta para o José

Em [novo_registro.py](.claude/skills/dupla/scripts/novo_registro.py#L54-L55), nós tiramos o título livre da linha de comando e deixamos apenas o `slug` validado por regex. Se numa entrevista te perguntarem **por que passar uma pergunta do usuário entre aspas no terminal (`python script.py "pergunta"`) ainda é perigoso no Git Bash mesmo quando o Python não usa `shell=True`**, como você explicaria?
