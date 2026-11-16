# 061 — Botões dos super agentes na Central 🛡️🎬
**Status:** 🔨 construído com o OK do José (15/11) · ⏳ teste no navegador e commit com o OK · seção 1 da lista mestra

## Ideia
Na tela inicial da Central, dois botões grandes:
- **🛡️ Ronda do defensor**: roda o `defensor.py` (Ticket 044) e mostra os 5 vigias com ✅ ⚠️ 🚨 na tela.
- **🎬 Produzir cortes**: atalho para o **Estúdio de cortes** que já existe (Ticket 048, página Vídeos). Esse **não tem Java novo**,
  porque a API já chama o `produtor.py` por lista fixa.

No chat, "faz a ronda" deixa de responder "roda no terminal" e passa a fazer a ronda de verdade (mesma rota do botão).

## Como funciona (ronda)
1. Botão → `POST /central/acoes/ronda` (sem corpo: não há nada para o José digitar, então não precisa de Revisar).
2. `RondaService` roda **um comando fixo**: `<python da venv> defensor.py`, na pasta `python/`.
   Reaproveita o `Processos.rodar` do Estúdio: sem shell, entrada vazia, tempo máximo de **90 s**, saída limitada.
3. O Java lê a saída linha por linha e só aceita o formato `<ícone> <vigia>: <frase>`,
   com o vigia de uma **lista fechada** (Sentinela, Processos, Rede, Backup, Duelo). Linha fora do formato é ignorada.
4. Resposta: `{"geral":"atencao","frase":"Defensor: ...","vigias":[{"vigia":"Rede","nivel":"atencao","frase":"..."}]}`.
5. O `defensor.py` sai com código 1 quando há ALERTA: o Java trata isso como resultado (🚨), não como erro.

## Segurança 🔒
- **Comando fixo**, nenhum pedaço vem da tela. Nada de `/acoes/{qualquer}`: é uma rota escrita à mão (allowlist).
- **Uma ronda por vez** (`AtomicBoolean`): clicar 10 vezes não abre 10 processos → segunda chamada recebe 429.
- Saída limpa antes de ir para a tela: sem caractere de controle, sem caminho do PC (`C:\Users\...` vira `…`), frase com no máximo 300 letras.
- A ronda **só lê** (Ticket 044). A única escrita é a nota de incidente que o próprio `defensor.py` já faz (nunca sobrescreve).
- Porteiros da Central de sempre (Host, Origin obrigatório no POST, crachá). Log: `acao=ronda resultado=ok geral=atencao`, sem as frases.
- A tela mostra tudo com `textContent` (nunca HTML).

## Arquivos
- Java: `acoes/ronda/RondaService` (novo) · `central/CentralRondaController` (novo) · `RoteadorDoChat`/`CentralChatController` (ronda vira ação) · `WebConfig` + `EventosInterceptor` (o escritório vê a ronda).
- Tela: `index.html` (2 botões no Início) · `paginas.js` ou `central.js` (chamar a ronda, mostrar os vigias; 🎬 abre a página Vídeos no estúdio de cortes) · `escritorio.js` (fala da ronda).
- Testes: `RondaServiceTest` (executor falso: leitura certa, código 1 = alerta, linha estranha ignorada, caminho escondido, tempo esgotado, uma por vez), `CentralRondaControllerTest` (200, sem crachá/Origin, 429, log sem frases), `RoteadorDoChatTest` e `CentralChatControllerTest` (ronda vira ação).

## Fora deste ticket
- "Produzir cortes" de ponta a ponta sem escolher vídeo (precisa decidir qual vídeo: fica para depois).

## Pronto quando
`./mvnw test` verde, ronda clicada no navegador com a API de verdade mostrando os 5 vigias, gitleaks limpo, commit com OK.

## Evidências (15/11)
- **Java: 474 testes, 0 falhas, 0 erros** (+10): `RondaServiceTest` (6), `CentralRondaControllerTest` (3), `CentralChatControllerTest` (+1: "faz a ronda" vira ação).
- **Contrato com o Python conferido de verdade:** rodei o `main()` do `defensor.py` com vigias de mentira (um ok, um atenção, um quebrado, um alerta).
  A saída sai no formato `ícone vigia: frase` que o Java aceita; o vigia quebrado vira ❔ e o cabeçalho 🛡️ é ignorado.
- 🎬 Produzir cortes: atalho que rola até o ✂️ Estúdio de cortes (Ticket 048) e põe o foco na escolha do vídeo. Nenhuma rota nova.
- gitleaks: nenhum segredo nos arquivos mudados.
- ⏳ Falta: clicar no 🛡️ com a API de verdade (José).
