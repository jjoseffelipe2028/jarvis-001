# 019 — Cabeçalhos de segurança + limite de corpo na Central
**Status:** ✅ commit aprovado pelo José (10/11) · ✅ teste real pelo José (10/11, 22:22): Central, Daily, Portas, Travas, Golpe? e as 4 frases no chat
**OK do plano:** José, 10/11 de madrugada ("Sim, arrocha")
**Criado:** 2026-11-10 pela 001 · Origem: [revisão de 10/11](../reviews/2026-11-10-central-e-video.md), achados 1 e 2

## Por quê
A revisão de segurança achou 2 pontos (nenhum grave):
1. A API não manda **cabeçalhos de segurança**, então a Central pode ser colocada num `<iframe>` de outro site (clickjacking). A CSP por `<meta>` não cobre isso. **OWASP A02, configuração insegura.**
2. `POST /central/sessao` lê o pedido sem limite de tamanho (o limite de 64 KB só vale em `/acoes/**`).

Tratamento de risco: **Reduzir** (aula 01 de Cyber 😉).

## Plano curto
1. **Novo `CabecalhosSegurancaFilter`** (um filtro só, em toda resposta):
   - `X-Frame-Options: DENY`
   - `Content-Security-Policy: frame-ancestors 'none'`
   - `X-Content-Type-Options: nosniff`
   - `Referrer-Policy: no-referrer`
   - `Cache-Control: no-store` só em `/acoes/**` e `/central/acoes/**` (resposta com dado pessoal não fica em cache).
2. **`CorpoGuardadoFilter`** passa a valer também em `/central/**`, com o mesmo limite de 64 KB (resposta 413).
3. Atualizar `docs/seguranca-owasp.md` (A02) e o README (tabela ameaça → defesa → prova).

## Testes
- `CabecalhosSegurancaFilterTest`:
  - os 4 cabeçalhos aparecem em `/`, `/index.html`, numa ação e até numa resposta 401/403;
  - `no-store` só nas rotas com dado.
- `CorpoGuardadoFilterTest`: `/central/sessao` com 65 KB → 413.
- Nenhum teste antigo pode quebrar (270 hoje).
- Teste real (José): abrir a Central pelo ícone e ver tudo funcionando igual. Bônus: na aba Rede do navegador (F12), ver os cabeçalhos.

## Evidências
- Java: **283** testes, 0 falhas (+13):
  - `CabecalhosSegurancaFilterTest`: 6 rotas recebem as 4 travas, 4 rotas com dado ganham `no-store`, página estática sem `no-store`;
  - `CentralControllerTest` +2: `/central/sessao` com 64 KB → **413** antes de ler o JSON; um **401** também sai com `X-Frame-Options`, CSP e `no-store`.
- Arquivos: `CabecalhosSegurancaFilter.java` (novo) e `CorpoGuardadoFilter.java` (vale também em `/central/**`).
- Teste na cópia da API (001, porta 8091): `curl -D -` em `/index.html` mostrou `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `nosniff` e `no-referrer` ✅.
- Teste real (José): _(preencher)_ → fechar a "Jarvis API", abrir pelo ícone, usar Pendências/Git e a voz: tudo igual. Bônus: F12 → Rede → clicar num pedido → ver `X-Frame-Options: DENY`.
- Commit: _(preencher)_

## Tamanho
Pequeno: 1 filtro novo, 1 linha no filtro antigo e 2 testes. Sem mudar o Python.

## 🎤 Para a entrevista
"Fiz uma revisão de segurança no meu projeto, achei falta de cabeçalhos (clickjacking, OWASP A02), corrigi com um filtro e provei com teste."
