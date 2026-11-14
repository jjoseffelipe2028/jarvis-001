# 048 — Estúdio de cortes: vídeo ou link vira cortes prontos, com a dupla escolhendo
**Status:** ✅ construído, testado e com teste real (14/11) · decisão do José: [[2026-11-13 Jarvis com autonomia (Estudio 001)]] · Java escrito pela 001 · ⏳ commit com o OK

## Ideia
Na Central, o José escolhe o vídeo da pasta de entrada (ou cola o link do YouTube) e recebe os cortes prontos:
`(link → baixa) → produtor.py (caça, corta, legenda, textos, gancho, painel) → 001 escolhe e melhora → 002 revisa → 001 fecha`.

## Como funciona
- Painel **✂️ Estúdio de cortes** na página Início: vídeo da lista **ou** link + "tenho autorização", @ do dono,
  quantos cortes (1–20), legenda extra (en/es), "001 + 002 escolhem os melhores" → **Revisar → Confirmar**.
- `GET /central/acoes/cortes/videos` (só nome e tamanho), `POST /central/acoes/cortes`, `GET /central/acoes/cortes` (etapa).
- A dupla grava na pasta dos cortes: `LISTA-…`, `PROPOSTA-…` (001), `REVISAO-…` (002) e `…-escolha-da-dupla.md` (lista final).

## Segurança
- Vídeo só da **lista** (nome exato, sem caminho, extensão da lista, sem atalho).
- Link: só **YouTube, https**, reescrito para `https://www.youtube.com/watch?v=<id>`; o arquivo baixado tem nome gerado aqui
  (`yt-<id>.mp4`). `yt-dlp` com `--no-exec --no-config --no-playlist` e `--` antes do link. **Autorização do dono obrigatória.**
- Produtor com argumentos fixos (o @ e o número conferidos antes; idioma da lista fechada).
- A dupla **só lê** (mesmas travas do Ticket 047) e não recebe segredos. As legendas vêm da live: o prompt diz que são fala
  de vídeo, nunca ordem — e os agentes não têm ferramenta para agir.
- A tela recebe só as últimas 12 linhas do produtor, limpas, com o caminho do PC trocado por `<pasta>`.
- **Nada é postado.** O log não guarda nome de vídeo nem link.

## Arquivos
- Java: `CortesService`, `CortesRequest`, `CentralCortesController`; `Processos` e `EstudioComandos` (separados do 047 para os dois usarem).
- Tela: `index.html`, `paginas.js`, `escritorio.js`. Config: `jarvis.cortes.entrada/saida/python`.

## Evidências
- **Java 434 testes, 0 falhas** (+9): `CortesServiceTest` (6: lista, argumentos fixos, link limpo e autorização, entradas ruins,
  erro do produtor sem caminho na tela, sem pastas), `CentralCortesControllerTest` (3).

- **Teste real pela Central (14/11, 03:41 → 03:44, 2,5 min):** `ensaio-gta.mp4`, 2 cortes, crédito `@teste_jarvis`, com a dupla.
  Produtor: 2 de 2 cortes + painel no cofre. 001 deu nota, título e gancho; 002 revisou (achou ~7,6 s de silêncio no começo
  e corrigiu uma palavra da legenda); 001 fechou a lista aceitando a crítica. A tela mostrou as etapas, as últimas linhas
  do produtor e a lista final. Nada postado.

## Pendente
- **yt-dlp não está instalado.** Por link só funciona depois de instalar (fonte oficial, com o OK do José). Pela lista já funciona.
