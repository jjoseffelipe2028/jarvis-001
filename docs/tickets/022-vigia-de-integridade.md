# 022 — Sentinela v1: vigia de integridade (FIM) 🔏
**Status:** ✅ commit aprovado pelo José (10/11) · ✅ teste real pelo José (10/11, 22:22): Central, Daily, Portas, Travas, Golpe? e as 4 frases no chat
**OK:** José, 10/11 de madrugada ("arrocha o máximo... termina hoje o Jarvis")
**Criado:** 2026-11-10 pela 001 · Ideia de 03/11 ([[jarvis-ideias-futuras]]: "Vigia de Integridade")

## Ideia
FIM = *File Integrity Monitoring*. Quando a API liga, o Jarvis tira uma "foto" (impressão digital **SHA-256**) das **travas** do projeto. Depois, "Jarvis, alguém mexeu nas travas?" mostra o que mudou desde então.

Pega dois perigos: vírus mexendo nos arquivos e **IA enganada** (prompt injection) tentando desligar as próprias regras.

## As travas vigiadas (lista fixa)
1. `CLAUDE.md` (regras da IA)
2. `.claude/settings.json` (permissões e hooks)
3. `.claude/hooks/guarda.mjs` (a trava que barra comandos perigosos)
4. `java/bracos/src/main/resources/application.properties` (`server.address=127.0.0.1`)
5. `.gitignore` (mantém o `.env` fora do Git)

## Como funciona
- `IntegridadeService`: foto na hora em que a API liga; a cada pedido, calcula de novo.
- Estados: `igual`, `alterado`, `sumiu`, `apareceu` e `estranho` (atalho/symlink, pasta no lugar do arquivo ou arquivo acima de 1 MB).
- Rotas (só leitura): `GET /acoes/sentinela/integridade` (voz, vira evento) e `GET /central/acoes/sentinela/integridade` (botão 🔏 Travas + skill "Sentinela: travas").
- Python: "confere a integridade", "alguém mexeu nas travas?", "as travas estão iguais?" → ação `integridade`. A voz fala só o nome curto (ex.: `guarda.mjs`).
- Pasta do projeto: `JARVIS_PASTA_PROJETO` no `.env` ou, vazio, duas pastas acima de `java/bracos`. Só vale se tiver `CLAUDE.md` **e** `.git`. Senão → 503.

## 🔒 Travas
- Lista **fixa** no código: o pedido não escolhe arquivo (sem Path Traversal).
- Não segue atalho (`NOFOLLOW_LINKS`); não lê arquivo acima de 1 MB.
- **Não escreve nada.** Na resposta: só nome curto, estado e 12 letras do hash.

## Limite da v1 (honesto)
- A foto fica **na memória**: reiniciou a API, a foto é nova. Mudança feita com a API desligada não aparece.
- Se o próprio José editar o `CLAUDE.md`, aparece "alterado" até reiniciar a API (é o esperado).
- v2: salvar a foto num arquivo com o OK do José e conferir no "bom dia, Jarvis".

## Evidências
- Java: `IntegridadeServiceTest` (7): tudo igual; alterado/sumiu/apareceu; 1 espaço já muda o hash; pasta no lugar do arquivo e arquivo gigante = estranho; atalho não é seguido (pulado no Windows sem admin, roda no CI); sem projeto → 503; acha o projeto real. Mais `SentinelaControllerTest` e `CentralSentinelaControllerTest` (token/crachá, evento).
- Python: `test_intencoes` (+4) e `test_bracos` (+1).
- Teste real (José): _(preencher)_ → reiniciar a API, 🔏 Travas na Central = "5 travas iguais"; depois perguntar no chat "alguém mexeu nas travas?".
- Commit: _(preencher)_

## 🎤 Para a entrevista
"Fiz um FIM simples: SHA-256 das travas do projeto quando a API liga, conferido sob demanda. Detecta se alguém, até uma IA enganada, mexeu nas minhas regras de segurança."
