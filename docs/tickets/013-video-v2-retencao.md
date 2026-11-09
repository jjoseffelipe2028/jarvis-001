# 013 — Vídeos V2: análise de retenção → nota no Obsidian
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001 · Módulo de Vídeos, fase V2 de 7

## Ideia
Depois de postar, o José copia os números do painel da plataforma. O Jarvis diz **o que deu errado**, **como corrigir no próximo vídeo** e **se vale dublar**, e guarda tudo no cofre.

## O que faz
`POST /acoes/video/relatorio` 🔒
```json
{"titulo":"Receita de bolo","plataforma":"facebook","duracaoSeg":190,
 "proporcao3sPara1min":55.5,"mediaAssistidaSeg":80,"ctrPct":4.2,"quedasSeg":[2,55]}
```
→ grava `decisions/videos/2026-11-09-164210-retencao.md` e devolve previsão, dublagem (regra do V1) e achados.

| Alerta | Quando | Ajuste sugerido |
|---|---|---|
| 🚨 Enterro | média ≤ 20 s (vídeo de 1 min ou mais) | conferir a origem do tráfego e refazer o gancho |
| Abaixo de 3 min no Facebook | Facebook e duração < 180 s | roteiro bruto de 4–5 min, final ≥ 3:05 |
| Saída antes de 1:00 | 3s→1min ≤ 50% | quebra a cada 15 s, pico de curiosidade aos 0:55 |
| Média baixa | vídeo ≥ 3 min e média < 1m14s | cortar pontos de fuga |
| Queda no gancho | queda antes de 0:03 | "fator uau" no primeiro segundo |
| Queda antes do anúncio | queda entre 0:50 e 0:59 | promessa ou pergunta aos 0:55 |

**Previsão:** forte · promissor, com ajustes · abaixo da meta · fraco (enterro) · sem previsão (vídeo < 1 min).

## Segurança
- Pasta só do `.env` (`JARVIS_PASTA_DECISOES`). Sem pasta → 503 (falha fechada).
- Nome da nota **gerado pelo servidor** (data + hora). O título nunca vira nome de arquivo (`../` testado).
- Só cria nota nova (`CREATE_NEW`, sem seguir atalho): nunca sobrescreve. Mesmo segundo → sufixo `-2`.
- Título "desarmado": a regra do daily virou `TextoSeguro`, compartilhada pelos dois.
- Plataforma só da lista fechada. Quedas: no máximo 20, dentro da duração do vídeo.
- Log sem título e sem caminho.

## Arquivos
- Novos: `acoes/TextoSeguro.java` · `video/AnalisadorDeRetencao.java` · `RelatorioService.java` · `RelatorioRequest.java` · `Analise.java` · `Achado.java`
- Alterados: `DailyService` (usa `TextoSeguro`) · `VideoController` (rota nova) · `application.properties` · READMEs
- Testes: `AnalisadorDeRetencaoTest` (13) · `RelatorioServiceTest` (6) · `RelatorioControllerTest` (10) · +1 no `VideoControllerTest`

## Evidências
- Java: **212** testes, 0 falhas (+30). Os testes do daily continuam passando depois da troca para `TextoSeguro`.
- Commit: _(preencher)_

## ⚙️ Para ligar no PC (o José faz)
Acrescentar no `.env` (a 001 não lê o `.env`):
```
JARVIS_PASTA_DECISOES="C:/Users/jjose/OneDrive/Documentos/Obsidian Vault/decisions/videos"
```
A pasta `decisions/videos` já foi criada no cofre.

## Fica para depois
- Ler o CSV exportado do painel (hoje os números são digitados).
- Meta de CTR: o curso ainda não deu número.
- Chamada por voz pelo Python.
