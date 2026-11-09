# ☕ Java — Braços do Jarvis

API em **Spring Boot** que executa as ações do Jarvis com segurança.
O Python (cérebro) só **pede**; esta API decide se a ação existe na lista permitida (allowlist) e executa.

## Requisitos

- **Java 21**
- Maven não precisa instalar: o `./mvnw` (Maven wrapper) baixa e usa a versão certa sozinho.

## Como rodar

```bash
cd java/bracos
./mvnw spring-boot:run
```

A API sobe em `http://127.0.0.1:8080` (só o próprio PC acessa).

## Como testar

```bash
cd java/bracos
./mvnw test
```

## Ações disponíveis

| Método | Endereço | O que faz | Exemplo de resposta |
|---|---|---|---|
| `GET` | `/acoes/hora` | Data e hora de agora (horário de Brasília) | `{"hora":"23:45","data":"01/11/2026"}` |
| `POST` 🔒 | `/acoes/daily` | Acrescenta `- HH:mm texto` no daily de hoje do Obsidian | `{"arquivo":"2026-11-08.md","resultado":"ok"}` |
| `GET` 🔒 | `/acoes/pendencias` | Tarefas abertas (`- [ ]`) dos dailys dos últimos 7 dias | `{"pendencias":[{"dia":"2026-11-08","texto":"estudar Git"}],"total":1,"ignorados":0}` |
| `GET` 🔒 | `/acoes/git-status` | Tem algo sem commit no repositório? (só números) | `{"branch":"main","limpo":false,"alterados":2,"novos":1,"aFrente":0,"atras":0}` |
| `GET` | `/acoes/prova?desafio=<hex>` | Aperto de mão: devolve HMAC(token, desafio) para o Python saber que é a API verdadeira | `{"prova":"74442c56..."}` |

Testar na mão (com a API rodando):

```bash
curl http://127.0.0.1:8080/acoes/hora
```

As rotas 🔒 precisam do token (`X-Jarvis-Token`) e das pastas no `.env` (`JARVIS_PASTA_DAILY`, `JARVIS_PASTA_GIT`). Só `/acoes/hora` e `/acoes/prova` (aperto de mão) são abertas.

## Segurança 🔒

**Já tem:**
- Só escuta em `127.0.0.1` (`application.properties`): nada aberto para a rede.
- Só existem as ações escritas à mão nos controllers (allowlist).
- Texto gravado no Obsidian é "desarmado": sem quebra de linha, invisíveis ou Markdown/HTML ativo.
- Pedido **assinado** (HMAC + hora + nonce de uso único): o cliente Python nunca manda o token. O token direto só para testes com curl (fica no log).
- Token secreto em **todo** `/acoes/**` (fechado por padrão, até no `GET`), comparado em tempo constante (contra CSRF). Só `/acoes/hora` e `/acoes/prova` são públicas. Sem token configurado: 503.
- Leitura do Obsidian (`pendencias`): só os 7 últimos dailys, nomes gerados pelo servidor, sem seguir atalho, com limites de tamanho.
- Programa externo (`git-status`): comando **fixo** e sem shell (anti injeção de comando), `core.fsmonitor` desligado, variáveis `GIT_*` apagadas, 5 s e 64 KB no máximo, resposta só com números.
- Filtro de `Host`: só `localhost` e `127.0.0.1` (contra DNS rebinding).
- Validação de toda entrada com `@Valid`; erro vira JSON curto, sem stack trace nem caminho.
- Escrita só na pasta permitida (anti Path Traversal); nome do arquivo gerado pelo servidor.
- Log de auditoria: `cliente= acao= resultado=`, limpo contra log injection (`LogSeguro`).
- Testes-guarda: toda rota que altera dados **ou** GET fora da lista pública sem token dá 401 · nenhum log usa dado cru.
- GitHub: testes a cada push (Actions), **Dependabot** (bibliotecas) e **CodeQL** (análise de segurança do código).

**Planejado:** Spring Security.

## Estrutura

```
bracos/
├── pom.xml                                  dependências (Spring Boot 4.1.2, Java 21)
└── src/
    ├── main/
    │   ├── java/.../bracos/
    │   │   ├── BracosApplication.java       liga a API
    │   │   ├── acoes/AcoesController.java   /acoes/hora
    │   │   ├── acoes/PastaSegura.java       regras da pasta permitida (anti Path Traversal)
    │   │   ├── acoes/daily/                 /acoes/daily (Request, Service, Controller)
    │   │   ├── acoes/pendencias/            /acoes/pendencias (Service, Controller)
    │   │   ├── acoes/git/                   /acoes/git-status (Service, Controller)
    │   │   ├── seguranca/                   porteiros: Host e token
    │   │   ├── erros/                       erros viram JSON curto
    │   │   └── auditoria/                   Cliente e LogSeguro
    │   └── resources/application.properties endereço, porta, token e pasta (do .env)
    └── test/java/.../bracos/                158 testes (./mvnw test)
```

Revisões deste código: [`docs/reviews/`](../docs/reviews/).
