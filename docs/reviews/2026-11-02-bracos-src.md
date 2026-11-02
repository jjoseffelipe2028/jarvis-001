# 🧑‍⚖️ Banca dos 5 — `java/bracos/src`

- **Data:** 02/11/2026
- **Revisor:** 001 (Claude Code), skill `/banca`
- **Arquivos lidos:** `AcoesController.java`, `BracosApplication.java`, `application.properties`, `BracosApplicationTests.java`, `pom.xml`
- **Resultado:** ❌ **NÃO PASSA** (2 personas reprovaram)

## 1. Resumo

| Persona | Veredito | Principal achado |
|---|---|---|
| 🕵️ Hacker | ✅ APROVA | Só escuta em `127.0.0.1`, não tem segredo no código, e o único endpoint só lê, sem entrada |
| 🙋 Usuário Caótico | ✅ APROVA | Não tem entrada para quebrar; endereço errado devolve 404 padrão |
| 📈 Pico de Acesso | ✅ APROVA | Nada pesado; só um detalhe pequeno de eficiência |
| 🧓 Sênior Chato | ❌ REPROVA | `/acoes/hora` não tem teste JUnit e nenhuma ação gera log de auditoria |
| ⏳ Eu daqui a 6 meses | ❌ REPROVA | `java/README.md` diz "em breve", mas a API já existe; o README raiz fala de outras fases |

## 2. Achados por persona

### 🕵️ Hacker — ✅
- ✅ `application.properties:5`: `server.address=127.0.0.1`. A trava de rede está certa.
- ✅ `AcoesController.java:30`: só existe `GET /hora`. Ele não escreve arquivo e não recebe parâmetro, então ainda não precisa de token nem de `@Valid`.
- ✅ `pom.xml`: só tem starters oficiais do Spring.
- 🟢 **Para o futuro:** o `pom.xml` (linhas 32-43) ainda não tem `spring-boot-starter-validation`. O CLAUDE.md exige essa dependência, e ela tem que entrar antes do primeiro endpoint que recebe dados.
- 🟢 **Para o futuro:** a API não confere o cabeçalho `Host` dos pedidos, o que abre espaço para *DNS rebinding* (uma página maliciosa faz o nome do site dela apontar para `127.0.0.1` e lê a sua API). Para a hora, o risco é zero; para endpoints que leem arquivos, vai importar. Quem resolve é o token secreto que já está planejado.

### 🙋 Usuário Caótico — ✅
- Não tem nenhuma entrada do usuário para testar.
- Um endereço que não existe devolve o erro 404 padrão do Spring, que é claro e não trava.
- Chamadas ao mesmo tempo são seguras: `ZonedDateTime` e `DateTimeFormatter` são imutáveis (não mudam depois de criados).

### 📈 Pico de Acesso — ✅
- 🟢 `AcoesController.java:34-35`: o `DateTimeFormatter.ofPattern(...)` é recriado a cada chamada. O certo é guardar os dois como `static final`, como já é feito com o `FUSO`.

### 🧓 Sênior Chato — ❌
- 🔴 `BracosApplicationTests.java:10`: o único teste é `contextLoads()`, que só confere se o Spring liga. **Por que é problema:** se alguém mudar o formato da resposta, o Python que lê esse JSON quebra e nenhum teste avisa.
- 🟡 `AcoesController.java:31-38`: nenhuma linha de log de auditoria (quem pediu, qual ação, resultado). **Por que é problema:** quando houver ações que escrevem arquivo, não vai dar para provar o que a IA pediu nem quando.
- 🟢 Linha 31: um `record HoraResposta(String hora, String data)` deixaria o formato da resposta mais claro que o `Map`.

### ⏳ Eu daqui a 6 meses — ❌
- 🟡 `java/README.md`: diz "Fase 3 — em breve.", mas a API já roda. Falta explicar como rodar e listar as ações.
- 🟡 `README.md` (raiz): diz "Fase 2 de 7" e tem uma lista de fases diferente do roteiro do CLAUDE.md v2.
- 🟢 `pom.xml:14-27`: sobraram tags vazias do gerador do projeto (`<name/>`, `<licenses>`, `<scm>`).
- 🟢 `AcoesController.java:3-4`: o comentário "TAREFA DO JOSÉ: redigitar" continua lá.
- ✅ Os comentários do controller são bons para iniciante, e a pasta `acoes/` separa bem as responsabilidades.

## 3. O que consertar, por prioridade

1. 🔴 Criar `AcoesControllerTest` com `MockMvc` (um simulador de pedidos HTTP que roda sem ligar o servidor) para chamar `GET /acoes/hora` e conferir os formatos `HH:mm` e `dd/MM/yyyy`.
2. 🟡 Adicionar log de auditoria (SLF4J) no controller.
3. 🟡 Atualizar o `java/README.md` e alinhar as fases do README raiz com o CLAUDE.md.
4. 🟢 Guardar os formatadores como `static final`.
5. 🟢 Adicionar o `spring-boot-starter-validation` antes do próximo endpoint que recebe dados.
6. 🟢 Limpar as tags vazias do `pom.xml` e resolver o comentário "redigitar".

## 4. Pergunta para o José 🎤
> Por que o `GET /acoes/hora` não precisa de token secreto, mas o futuro `daily` (que escreve no Obsidian) precisa? Que ataque o token impede?

**Resposta do José:** _(preencher)_
