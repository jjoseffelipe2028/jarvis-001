# 081 — Guardião do contrato Java ↔ Python
**Status:** ✅ construído e testado (15/11) · ⏳ commit com o OK do José · Seção 5 (Cyber e segurança), item 2

## O problema
O Python lê as respostas da API com `.get("campo", padrão)`. Se alguém renomear um campo no Java
(ex.: `toques` → `quantidade`), **nada quebra**: o Python lê o padrão (`0`) e fala "a isca está quieta"
mesmo com a isca tocada. É uma **falha silenciosa**, e na segurança ela é a pior: o alarme some sem ninguém ver.
Hoje os testes de cada lado usam respostas **inventadas por eles mesmos**, então os dois passam mesmo quando discordam.

## A ideia: um contrato só, conferido pelos dois lados (*contract testing*)
Arquivo único **`docs/contratos/bracos-python.json`**: a "fonte da verdade" do que o Python manda e lê.
Para cada rota que o `bracos.py` usa (hora, prova, daily, pendencias, git-status, portas, integridade, isca, alarme, golpe, proposta):
```json
{"rota": "/acoes/sentinela/isca", "metodo": "GET", "metodoPython": "isca",
 "pedido": {},
 "resposta": ["ligada", "toques", "ultimoToque", "arquivo"]}
```
e, para quem manda corpo: `"pedido": {"texto": {"obrigatorio": true, "max": 4000}}`.

## Os dois guardas
- **Java — `ContratoPythonTest`** (JUnit, sem subir a API, por reflexão):
  1. a rota existe num `@RestController` com aquele método HTTP (`@GetMapping`/`@PostMapping`);
  2. o corpo do pedido (record `*Request`) tem **exatamente** os campos do contrato, com o mesmo `@Size(max)` e `@NotBlank` nos obrigatórios;
  3. a resposta (record) tem **todos** os campos que o Python lê. Rotas que devolvem `Map` (hora, daily, prova): conferidas com uma chamada `MockMvc`.
- **Python — `test_contrato.py`** (unittest):
  1. cada rota do contrato tem o método no `Bracos` e ele chama **a mesma rota e o mesmo método HTTP**;
  2. o corpo que o Python manda tem só campos do contrato e corta no mesmo `max` (ex.: golpe corta em 4000);
  3. a frase falada (`responder`/`bom_dia`) usa os campos do contrato: com uma resposta montada **só** com eles,
     nenhuma frase cai no "formato estranho" ou no `?`.
  4. nenhuma rota nova no `bracos.py` sem estar no contrato (o teste lista as rotas chamadas).

## Segurança
- Só testes e um JSON: **não muda a API nem abre nada**. Nada de dado real no contrato (só nomes de campos).
- Ganho de segurança: renomear campo de alerta (isca, alarme, portas, integridade) passa a **quebrar o build**
  em vez de silenciar um aviso. Também trava o tamanho máximo dos textos (o Python não pode mandar mais do que o Java aceita).

## Pronto quando
- Os dois testes passam; mudança de prova: renomear `toques` só no Java (ou só no contrato) **quebra** o teste — mostro isso (prova por mutação) e desfaço.
- `./mvnw test` e `unittest` verdes; o CI do GitHub roda os dois sem mudança (o JSON fica dentro do repositório).

## Evidências (15/11)
- `ContratoPythonTest` (12): 11 rotas conferidas (rota + método, corpo com `@Valid`/`@NotBlank`/`@Size` iguais ao contrato,
  campos e tipos da resposta, até dentro das listas) + 1 chamada real (`MockMvc` standalone) nas 3 rotas que devolvem `Map`.
- `test_contrato.py` (10): ação fora do contrato quebra · rota/método · corte no `max` · obrigatório vazio nem chama a API ·
  um **espião** (dict que anota cada `.get`) prova que frases, bom dia, alarme, Sentinela e proposta só leem campos do contrato.
- **Prova por mutação** (feita e desfeita): `toques` → `quantidade` só no Python → 2 falhas ("a isca foi tocada" sumiu da frase);
  só no contrato → Java falha: `GET /acoes/sentinela/isca → quantidade: o Python lê este campo, mas o Java não manda`.
- `./mvnw test`: **470 testes, BUILD SUCCESS**. Python: **332 testes OK**.
