# 087 — Spring Security (só plano e explicação)
**Status:** 📝 plano · ⏳ **decisão do José** (dependência nova só com OK) · Seção 5, item 8

## O que é
`spring-boot-starter-security` é o módulo **oficial** de segurança do Spring (mantido pelo time Spring, VMware/Broadcom;
licença Apache 2.0; vem do Maven Central, a mesma fonte das outras dependências). Não manda dado para fora.
É o padrão de mercado para autenticação e autorização em Java, e aparece em quase toda vaga de back-end.

## O que o Jarvis já faz "na mão" (e o Spring Security faria)
| Hoje (código nosso) | No Spring Security |
|---|---|
| `TokenInterceptor` (assinatura HMAC + token) | um `OncePerRequestFilter` de autenticação dentro da `SecurityFilterChain` |
| `HostInterceptor` (anti DNS rebinding) | filtro próprio na cadeia (o Spring não tem pronto) |
| `CentralInterceptor` (crachá em cookie, Origin, Sec-Fetch-Site) | `SecurityFilterChain` só para `/central/**` + proteção CSRF nativa |
| `CabecalhosSegurancaFilter` | `http.headers(...)` (X-Frame-Options, CSP `frame-ancestors`, nosniff, Referrer-Policy) |
| Rotas públicas `/acoes/hora` e `/acoes/prova` | `authorizeHttpRequests(a -> a.requestMatchers(...).permitAll().anyRequest().authenticated())` |
| `AlarmeDeTentativas` (Ticket 080) | continua igual (é um filtro comum) — ou ouve o evento `AuthenticationFailure...` |

## Riscos de ligar (por que NÃO é só adicionar a dependência)
1. **Ao entrar no `pom.xml`, ele tranca TUDO** e cria um login com senha gerada no console: o Python e a Central quebram na hora.
2. **CSRF ligado por padrão em POST**: o Python (sem cookie) leva 403 até configurar `csrf` só para a Central.
3. Duas camadas fazendo a mesma coisa (interceptor + filtro) por um tempo = confusão de quem negou. A migração tem que
   **trocar**, não **somar**.
4. Os 470+ testes usam `@WebMvcTest` com os interceptores; vários vão precisar de `@WithMockUser`/configuração nova.

## Plano sugerido (se o José aprovar) — 4 tickets pequenos, ramo próprio
1. **Esqueleto:** dependência + `SecurityFilterChain` que libera tudo (`permitAll`) e só põe os cabeçalhos. Todos os testes verdes.
   O teste-guarda de arquitetura (082) continua valendo.
2. **API dos braços:** a assinatura HMAC vira um filtro de autenticação; `/acoes/hora` e `/acoes/prova` públicas; o resto autenticado.
   Sai o `TokenInterceptor`. Os testes de 401/503/isca continuam iguais (prova de que nada mudou para fora).
3. **Central:** cadeia própria para `/central/**`, crachá como autenticação, CSRF nativo com token no cabeçalho. Sai o `CentralInterceptor`.
4. **Limpeza + revisão:** `/banca` e o `jarvis-hacker` revisando, README e diagrama.

## Recomendação da 001
**Depois do Jarvis 1.0**, como projeto de estudo no seu ramo, porque:
- o Jarvis **já está protegido** (e testado) com o código atual; trocar agora é risco sem ganho de segurança;
- para a entrevista, vale mais você **explicar o que o seu interceptor faz e qual peça do Spring Security faria o mesmo**
  (a tabela acima) do que ter a dependência sem saber explicar.

## Pergunta para o José
Por que adicionar o Spring Security "só no pom.xml" quebraria o Python na hora? (Dica: o que ele faz por padrão com toda rota?)
