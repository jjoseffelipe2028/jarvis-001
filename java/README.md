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

Testar na mão (com a API rodando):

```bash
curl http://127.0.0.1:8080/acoes/hora
```

## Segurança 🔒

**Já tem:**
- Só escuta em `127.0.0.1` (`application.properties`): nada aberto para a rede.
- Só existem as ações escritas em `AcoesController` (allowlist).
- Log de auditoria em toda ação: `acao=<nome> resultado=<ok|erro>`.

**Planejado (antes das ações que recebem dados ou escrevem arquivo):**
- Validar toda entrada com `@Valid` (`spring-boot-starter-validation`).
- Token secreto no cabeçalho, lido do `.env` (contra CSRF e DNS rebinding).
- Ler/escrever só em pastas permitidas (anti Path Traversal).

## Estrutura

```
bracos/
├── pom.xml                                  dependências (Spring Boot 4.1.2, Java 21)
└── src/
    ├── main/
    │   ├── java/.../bracos/
    │   │   ├── BracosApplication.java       liga a API
    │   │   └── acoes/AcoesController.java   as ações (allowlist)
    │   └── resources/application.properties endereço e porta
    └── test/java/.../bracos/
        ├── BracosApplicationTests.java      a API liga?
        └── acoes/AcoesControllerTest.java   /acoes/hora devolve o formato certo?
```

Revisões deste código: [`docs/reviews/`](../docs/reviews/).
