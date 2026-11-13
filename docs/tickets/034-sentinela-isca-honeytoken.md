# 034 — Sentinela: isca (honeytoken)
**Status:** ✅ construído e testado (12/11) · Java escrito pela 001 (decisão do José: "termina o projeto, depois eu treino") · ⏳ commit com o OK

## Ideia
Espalhar iscas **falsas**. Ninguém do bem mexe nelas, então **qualquer toque é alarme**:
- **Token-isca:** um token falso, parecido com o de verdade. Um pedido com ele = "alguém leu a isca e tentou entrar".
- **Arquivo-isca:** um arquivo com dados inventados (ex.: `senhas-banco.txt`). Mudou ou sumiu = alarme.

Pega malware, IA enganada por prompt injection e curioso com acesso ao PC. No MITRE Engage isso se chama dissimulação.

## Como ligar (o José faz, no `.env`)
```
JARVIS_ISCA_TOKEN=<um token FALSO, diferente do JARVIS_TOKEN>
JARVIS_ISCA_ARQUIVO=<caminho de um arquivo com dados INVENTADOS>
```
Sem a primeira linha, a isca fica desligada. **Nunca** usar dado de verdade como isca.

## Como funciona
- O porteiro (`TokenInterceptor`) confere o pedido normalmente. Só **depois de recusar** ele pergunta à `Isca`:
  era o token-isca (no cabeçalho ou na assinatura)? Se era: conta o toque e grita no log (`motivo=ISCA_TOCADA`).
- A resposta é o **mesmo 401** de um token errado qualquer: o invasor não percebe a armadilha.
- `GET /acoes/sentinela/isca` (com token de verdade) devolve: ligada, toques, hora do último toque e estado do arquivo.
- No "bom dia", isca tocada é o **primeiro** alerta da Sentinela.

## Segurança
- A isca **nunca abre a porta**. Ela só é consultada no caminho da recusa.
- Comparação por hash em tempo constante. O token-isca não vai para o log nem para a resposta.
- O arquivo-isca é lido sem seguir atalho e com limite de 64 KB; se não der para ler ao ligar, fica sem vigia (não inventa alarme).
- O contador tem teto (9.999) e some quando a API reinicia (o log em arquivo guarda o histórico).

## Evidências
- `IscaTest` (7) + `IscaDePontaAPontaTest` (4): cabeçalho, pedido assinado, resposta idêntica à de token errado, log sem o token.
- Python: `bracos.isca()` e 2 testes novos do bom dia.
- No duelo (Ticket 035): "Usar o token-isca → 401, alarme da isca tocou".
