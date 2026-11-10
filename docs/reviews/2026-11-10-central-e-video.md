# Revisão de segurança: Central (Tickets 014 a 018) + Vídeos (012 e 013)
**Data:** 2026-11-10 · **Quem:** 001 (Claude) · **Commit revisado:** `23f32c4`
**Escopo:** o que entrou depois da última revisão (02/11):
- `central/*` (chave, crachá, porteiro, quadro de eventos);
- `static/*` (index.html, central.js, escritorio.js);
- `acoes/video/*`;
- `CorpoGuardadoFilter` e `WebConfig`.

## Resumo
**0 crítico · 0 alto · 1 médio-baixo · 1 baixo · 2 informativos.** Nada exige correção urgente: as defesas principais estão no lugar e têm teste.

## ✅ O que está certo (conferido no código)
| Defesa | Onde |
|---|---|
| Chave de uso único com 256 bits (`SecureRandom`). Na memória fica só o **hash**, comparado em tempo constante e trocado a cada uso. | `ChaveDaCentral` |
| O arquivo da chave não segue atalho (`NOFOLLOW_LINKS` + `isSymbolicLink`). | `ChaveDaCentral` |
| Crachá: cookie `HttpOnly` + `SameSite=Strict` + `path=/central`, válido 12 h, no máximo 5. | `CentralController`, `SessoesDaCentral` |
| Porteiro da Central confere o cabeçalho próprio (força preflight de CORS), o `Origin` exato, o `Sec-Fetch-Site` e o crachá. | `CentralInterceptor` |
| Host conferido (anti DNS rebinding) em `/acoes/**` **e** `/central/**`. | `WebConfig` |
| A chave sai do endereço na hora (`history.replaceState`) e só vale no formato exato de 64 hex. | `central.js` |
| Página: CSP `default-src 'self'`, sem script embutido, só `textContent`, sem storage e sem rede própria no escritório. | `CentralPaginaTest` |
| Quadro de eventos: só na memória, no máximo 30, nomes fixos, sem log por consulta. | `QuadroDeEventos`, `EventosInterceptor` |
| Voz: só `localService === true` e só frases fixas. | `escritorio.js` |
| Relatório de vídeo: pasta só da config, nome gerado pelo servidor, `CREATE_NEW` + `NOFOLLOW_LINKS`, título desarmado, plataforma em lista fechada. | `RelatorioService`, `RelatorioRequest` |

## Achados

### 1. 🟡 Médio-baixo: faltam cabeçalhos de segurança nas respostas HTTP
- **O que é:** a API não manda `X-Frame-Options`/`frame-ancestors`, `X-Content-Type-Options: nosniff` nem `Referrer-Policy` como **cabeçalho**. A CSP da página vem por `<meta>`, e a CSP por `<meta>` **ignora** `frame-ancestors`.
- **Risco:** um site de fora pode colocar a Central num `<iframe>` (clickjacking).
  - O impacto é baixo: o crachá é `SameSite=Strict` e não vai no iframe de outro site, então o iframe fica sem botões funcionando.
  - Mesmo assim, é uma falha de configuração (**OWASP A02:2025, Security Misconfiguration**).
- **Correção sugerida (ticket pequeno):** um filtro que põe os cabeçalhos em toda resposta, mais 1 teste:
  - `X-Frame-Options: DENY`
  - `Content-Security-Policy: frame-ancestors 'none'`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: no-referrer`
- Isso também prepara o terreno para o **Spring Security**, que já faz isso por padrão.

### 2. 🟢 Baixo: `/central/sessao` sem limite de tamanho do corpo
- **O que é:** o limite de 64 KB (`CorpoGuardadoFilter`) só vale para `/acoes/**`. O `POST /central/sessao` lê o JSON inteiro.
- **Risco:** só um programa **no próprio PC** consegue chegar lá (a API escuta em `127.0.0.1` e o porteiro exige o cabeçalho próprio). No pior caso, ele gasta memória da API, e esse programa já poderia fazer coisa pior.
- **Correção sugerida:** aplicar o mesmo limite a `/central/**` (1 linha no `shouldNotFilter` + 1 teste).

### 3. ℹ️ Informativo: a chave da Central fica legível para programas do usuário
- Enquanto a API roda, o arquivo `.central-chave` guarda uma chave válida ainda não usada, e o `.bat` a passa na linha de comando do Edge por alguns segundos.
- Um programa rodando como o José consegue ler esse arquivo, mas ele já leria o `.env` e o cofre do mesmo jeito. Não há ganho de privilégio.
- **Decisão:** aceitar (mesmo nível de confiança do `.env`). Registrado aqui para não esquecer.

### 4. ℹ️ Informativo: arquivos estáticos fora do filtro de Host
- `/`, `index.html` e os `.js` não passam pelo `HostInterceptor`.
- São código público (o mesmo do GitHub), sem dado nem segredo, e todas as rotas com dado passam pelo filtro.
- **Decisão:** aceitar.

## Próximo passo sugerido
Um ticket com o **achado 1 + achado 2**: pequeno, com teste, e bom para contar em entrevista ("achei na minha revisão, corrigi e provei com teste").
