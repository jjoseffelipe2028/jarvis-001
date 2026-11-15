# 052 — Vídeo do Mestre / Brabo pela Central
**Status:** ✅ construído e testado com falsos (14/11) · pedido do José 14/11 · código pela 001 · ✅ teste real pela Central (15/11) · ⏳ commit com o OK

## Ideia
O José pede o vídeo **na Central** e recebe o vídeo **na Central**, sem abrir o Claude Code:
`personagem + tema → PEDIDO (arquivo) → 001 escreve o roteiro → 002 revisa → 001 fecha → brabo.py grava → vídeo na tela`.

## Como usar
1. Abrir a Central (`Jarvis-Central.bat`). Se a API for velha, o próprio .bat reinicia.
2. Painel **🎙️ VÍDEO DO MESTRE / BRABO** (página Início):
   - **Mestre Parafuso:** escreve o tema (ex.: "como trocar a resistência do chuveiro").
   - **Brabo:** escolhe o corte da lista e o @ do dono (com autorização).
3. **Revisar → Confirmar.** Os passos aparecem; quando fica pronto, o vídeo toca ali, com botão **⬇️ Baixar** e o texto do post.

## Segurança
- O tema vai num **arquivo** (`PEDIDO-…md`): nenhum texto do José vira argumento de comando.
- A dupla só **lê** (sem terminal, internet, MCP nem segredos) e devolve texto. O **Java** grava o roteiro e **força**
  `modo`, `fundo`, `corte` e `credito`; o `brabo.py` confere o resto (tamanho, link, palavrão, tempos).
- Se o `brabo.py` recusar o roteiro, a 001 tem **1 chance** de corrigir com o aviso; depois para e mostra o motivo.
- Brabo: corte só da **lista** (nome exato) e @ do dono obrigatório.
- O vídeo só sai da pasta dos cortes e só com nome `…-brabo.mp4` / `…-mestre.mp4` (`GET /central/acoes/brabo/video/{nome}`),
  atrás do crachá. A página baixa pelo `pedirArquivo()` da `central.js` (o `paginas.js` continua sem `fetch`).
  CSP: só `media-src 'self' blob:` a mais.
- Um pedido por vez. O log não guarda o tema. **Nada é postado.**

## Arquivos
- Java: `BraboService`, `BraboRequest`, `CentralBraboController`; rota registrada no `EventosInterceptor`/`WebConfig`.
- Tela: `index.html`, `paginas.js`, `central.js` (`pedirArquivo`), `central.css`, `escritorio.js`.

## Evidências
- `BraboServiceTest` (7): Mestre com dupla (tema só no arquivo, campos forçados), Brabo sem dupla (corte e crédito forçados,
  comando fixo), correção em 1 tentativa, falha mostrando o aviso, entradas ruins, nomes seguros, apelido.
- `CentralBraboControllerTest` (3): crachá, Origin, limite do tema, vídeo só com crachá e nome conhecido, log sem o tema.
- **Java: 444 testes, 0 falhas.**
- ⏳ Falta o teste real pela Central (a API rodando era a versão antiga).
- **1º teste real (14/11, 15:01):** parou em "001 escrevendo" (`IOException`): o ícone abre a API sem `claude`/`agy` no PATH
  do Windows. Correção: `application.properties` aponta o caminho completo (`~/.local/bin/claude.exe`,
  `%LOCALAPPDATA%/agy/bin/agy.exe`) e a tela agora mostra o motivo do erro (caminho trocado por `<pasta>`). Java 444, 0 falhas.
- **Teste real pela Central (15/11, 00:52 → 00:54, ~2 min):** Brabo, `vacilo no gta`, corte-02, com a dupla: 001 escreveu,
  002 revisou, 001 fechou, `brabo.py` gravou `…-vacilo-no-gta-brabo.mp4` e o vídeo tocou na tela. Nada postado.
