# 020 — Skill "Daily do dia" pela Central
**Status:** ✅ commit aprovado pelo José (10/11) · ✅ teste real pelo José (10/11, 22:22): Central, Daily, Portas, Travas, Golpe? e as 4 frases no chat · **Depois do 019**
**OK do plano:** José, 10/11 de madrugada ("arrocha o máximo... termina hoje o Jarvis")
**Criado:** 2026-11-10 pela 001 · Fase 5 · Tira o "em breve" da skill do Jarvis no escritório

## Ideia
Hoje o daily só é salvo pela **voz/terminal**. Com este ticket, a Central ganha um campo: o José escreve a linha, confirma, e o Jarvis do escritório anda até o rack e diz "Daily salvo 📝".

## Por que é diferente dos outros botões
Até agora a Central **só lê** (pendências e Git). O daily **escreve** no cofre. Isso pede mais cuidado: primeira ação de escrita pela Central.

## Plano curto
1. **API:** `POST /central/acoes/daily` (atrás do crachá), reaproveitando o `DailyService` de hoje:
   - mesmo pedido `DailyRequest` (texto obrigatório, máx. 2000);
   - mesmo "desarme" do texto e a mesma pasta da config;
   - log sem o texto: `cliente=central acao=daily resultado=ok`;
   - o quadro de eventos (017) também anota, então o escritório reage e o Jarvis fala (018).
2. **Central:** a skill "Daily do dia" abre um campo de texto + botão **Salvar**, com confirmação:
   - "Vai escrever no daily de hoje: <texto>. Confirmar?";
   - máximo de 2000 caracteres, com contador;
   - nada é salvo sem o clique em Confirmar.
3. Proteção extra: a rota de escrita exige **POST** + cabeçalho próprio + crachá + `Origin` exato. Sem `Origin` → recusa (na leitura hoje é opcional; na escrita fica obrigatório).

## Testes
- `CentralControllerTest`:
  - sem crachá → 401;
  - `Origin` de fora → 403;
  - **sem `Origin` → 403** (só na escrita);
  - texto vazio ou com 2001 caracteres → 400;
  - ok → chama o `DailyService` e o log não tem o texto.
- `CentralPaginaTest`: a skill usa `textContent` e a confirmação existe.
- Teste no navegador (001): cancelar não salva; confirmar salva e o Jarvis reage.
- Teste real (José): salvar uma linha pela Central e ver no Obsidian.

## Tamanho
Médio: 1 rota nova, o campo + a confirmação na Central e os testes.

## Fica para depois
- "Porteiro de vídeo" e "Relatório de retenção" pela Central (formulários maiores, um ticket cada).

## Evidências
- Arquivos:
  - `CentralDailyController.java` (novo): `POST /central/acoes/daily`, reaproveita o `DailyService`;
  - `CentralInterceptor.java`: na escrita, **sem `Origin` → 403** (`escrita_sem_origem`);
  - `EventosInterceptor.java` + `WebConfig.java`: o daily pela Central vira evento (cliente `central`);
  - `central.js`: `enviar()` (o único POST além do crachá) e o fluxo **Revisar → Confirmar / Cancelar**;
  - `index.html` / `central.css`: botão 📝 Daily, campo com contador 0/2000 e cartão verde;
  - `escritorio.js`: a skill "Daily do dia" agora está **no ar** (só abre o campo) e o escritório confere o quadro na hora depois de salvar.
- Testes: `CentralDailyControllerTest` (9): caminho feliz sem texto no log e com evento; sem crachá 401; **sem Origin 403**; leitura sem Origin continua ok; site de fora 403; sem cabeçalho 401; vazio/2001 → 400; campo extra de pasta ignorado; pasta sem config → 503. `CentralPaginaTest` +1: só 2 POSTs na Central inteira.
- Teste no navegador (001, cópia da API na porta 8091 com token falso e pasta temporária):
  - **Cancelar** → "nada foi escrito no cofre" e a pasta continuou vazia ✅;
  - **Confirmar** → `- 02:16 teste 020: linha salva pela Central` no `2026-11-10.md` ✅;
  - o escritório narrou "Jarvis fez o daily pela Central ✔" em segundos; console sem erro;
  - ataque: sem crachá **401**, Origin de fora **403**; o texto "hack" não entrou ✅.
- Teste real (José): _(preencher)_ → reiniciar a API, abrir pelo ícone, 📝 Daily → escrever → Revisar → Confirmar → ver a linha no Obsidian.
- Commit: _(preencher)_
