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
