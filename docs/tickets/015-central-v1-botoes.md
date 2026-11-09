# 015 — Central v1: botões de verdade + cartão de aceite da nuvem
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001 · Fase 5, parte 2

## Ideia
Os botões da Central passam a **fazer ações**: 📋 Pendências e 🌿 Git. E o "🌐 Pedir ao 001" mostra um **cartão de aceite**: nada sai do PC sem o José clicar em Aceitar.

## O problema de segurança (e a solução)
A página roda no navegador. Se o navegador guardasse o **token**, qualquer site aberto em outra aba poderia tentar usá-lo. Solução em 4 peças:

| Peça | O que faz |
|---|---|
| **Chave de uso único** (`ChaveDaCentral`) | A API, ao ligar, cria uma chave aleatória (256 bits) em `java/bracos/.central-chave` (fora do Git). Na memória fica só o hash. |
| **Lançador** (`Jarvis-Central.bat`) | Lê a chave e abre a Central com `#k=<chave>`. O que vem depois do `#` não vai pela rede nem para o log. |
| **Crachá** (`SessoesDaCentral`) | A página troca a chave por um cookie **HttpOnly + SameSite=Strict + Path=/central**, válido por 12 h, máx. 5. A chave é **trocada na hora**: copiar do histórico não adianta. O JS apaga a chave do endereço. |
| **Porteiro** (`CentralInterceptor`) | Em `/central/**`: Host só local (anti DNS rebinding) + cabeçalho `X-Jarvis-Central: 1` (site de fora cai no preflight de CORS, que a API não libera) + Origin própria + `Sec-Fetch-Site: same-origin` + crachá válido nas ações. |

O token **continua longe do navegador**. Os porteiros de `/acoes/**` não mudaram.

## Rotas novas
| Rota | O quê |
|---|---|
| `POST /central/sessao` | `{"chave":"..."}` → crachá. Qualquer erro → 401 sem dizer o motivo. |
| `GET /central/acoes/pendencias` | Mesma ação do braço, só leitura. |
| `GET /central/acoes/git-status` | Mesma ação do braço, só leitura. |

Nada que **escreve** ou **executa** entra na Central sem ticket próprio.

## 🌐 Pedir ao 001
Escreve o pedido → **Preparar** → cartão "Isso vai para a nuvem (Claude)" → **Aceitar** copia o texto para colar no Claude Code; **Recusar** descarta. **Nada é enviado sozinho**: o envio direto fica para a Fase 7.

## Evidências
- Java: **238** testes, 0 falhas (+17): `ChaveDaCentralTest` (4), `SessoesDaCentralTest` (4), `CentralControllerTest` (9, com ataques: chave repetida, chave errada, site de fora, `Sec-Fetch-Site: cross-site`, Host falso, botão sem crachá, crachá sem cabeçalho). `CentralPaginaTest` agora também exige que o JS não toque em `document.cookie` e apague a chave do endereço.
- Nenhum teste grava chave na pasta do projeto (a chave só liga pelo `iniciar.sh`).
- Teste real (19:01, José): ícone → "🔓 conectada" → Pendências (25) → Git (limpo=false, a v1 ainda sem commit) → cartão "Pedir ao 001" → Aceitar copiou, nada enviado. Log: `sessao ok`, `pendencias ok`, `git-status ok`.
- Commit: `c9aa3ab`

## Fica para depois
- Mais botões (relatório de vídeo, daily) — cada um com ticket.
- Envio direto ao 001/002 (Fase 7), sempre com o cartão de aceite.
- v2: escritório pixel.
