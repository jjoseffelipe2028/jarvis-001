# 010 — Melhorias da revisão OWASP (A03 e A09)
**Status:** ✅ concluído (2026-11-09)
**Criado:** 2026-11-09 pela 001 com o José · resolve os 🟡 1, 2 e 3 de [`docs/seguranca-owasp.md`](../seguranca-owasp.md)

## O que mudou
| # | Problema | Solução | Arquivo |
|---|---|---|---|
| 1 | Dependabot não vigiava as bibliotecas **Python** | Novo bloco `pip` para `/python` | `.github/dependabot.yml` |
| 2 | gitleaks só rodava quando alguém lembrava | Robô novo a cada push: baixa o gitleaks **oficial**, confere o **sha256** e varre o histórico inteiro. Segredo achado = robô vermelho | `.github/workflows/gitleaks.yml` |
| 3 | Log só no console: fechou a API, sumiu | Log em arquivo `java/bracos/logs/bracos.log`, troca a cada 10 MB, guarda 14 dias, fora do Git | `application.properties`, `iniciar.sh`, `.gitignore` |

**sha256** em 1 linha: a impressão digital de um arquivo. Se mudar 1 byte, ela muda toda.

## Decisão de design
O log em arquivo só liga quando a API sobe pelo `iniciar.sh` (`JARVIS_LOG`). Na primeira versão, os **testes** também gravavam no arquivo, misturando ataques falsos de teste com o log real. Num log de **auditoria** isso atrapalha, então foi separado.

## Evidências
- Java: **158** testes, 0 falhas. Depois da correção, os testes não criam mais a pasta `logs/`.
- Teste real (cópia da API na porta 18080, token fictício, sem tocar no `.env`): `hora` 200 · `pendencias` sem token 401 · a recusa apareceu **no arquivo** ✅.
- sha256 do gitleaks Linux conferido no PC antes de pôr no robô: `OK`.
- Robô gitleaks no GitHub: ✅ verde no 1º push · Dependabot `pip` rodou ✅
- Commit: `b6b7619`

## Fica para depois
- 🟢 Prender as actions por SHA.
- 🟢 Desligar o atalho `X-Jarvis-Token` quando o `curl` não for mais preciso.
