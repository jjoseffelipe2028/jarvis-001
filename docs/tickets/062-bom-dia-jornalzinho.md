# 062 — "Bom dia" como jornalzinho na tela 📰
**Status:** ✅ construído e commitado no ramo (15/11), pelo combinado do José de seguir sem OK por item · ⏳ teste no navegador · seção 2 da lista mestra

## Ideia
Hoje o "Bom dia, Jarvis" só existe **falado** (`rotinas.py`). Na Central, ele vira um **jornalzinho**:
um cartão com 4 manchetes, aberto pelo botão **📰 Bom dia** (no bloco 🦸) ou digitando "bom dia" no chat.

```
📰 BOM DIA, JOSÉ · sábado, 15/11 · 08:10
📝 Pendências   26 abertas · 1ª: estudar records do Java
🌿 Git          3 arquivos sem commit
🛡️ Sentinela    ✅ portas conhecidas · travas iguais · isca quieta
⚔️ Duelo        ✅ a defesa pegou 13 de 13        (da última ronda, 07:55)
💾 Backup       ⚠️ 2 de 3, falta Offline          (da última ronda, 07:55)
```

## Como funciona
- **Pendências, Git e Sentinela**: a tela chama as rotas **GET que já existem** (`pendencias`, `git-status`,
  `sentinela/portas`, `integridade`, `isca`). Nenhuma rota nova, nenhum comando novo.
- **Duelo e Backup**: vêm da **última ronda do defensor** (Ticket 061), que já mede os dois.
  Para o jornal não perder isso ao recarregar a página, o `RondaService` guarda o último resultado **em memória**
  e uma rota nova só de leitura devolve: `GET /central/acoes/ronda/ultima` → `{"quando":"07:55", ...}` ou 204 (nenhuma ronda ainda).
  Sem ronda → a manchete diz "faça a ronda 🛡️ para ver" (o jornal **não** roda a ronda sozinho: ela leva 15 s).
- **Chat**: regra fixa `BOM_DIA` ("bom dia", "bom dia, jarvis", "boa tarde/noite", "jornal") → `{"acao":"jornal"}`.
  Só a frase inteira: "bom dia, me explica records" continua indo para a IA.
- Cada manchete falha sozinha (igual ao `rotinas.py`): se o git não responder, as outras aparecem e ele mostra "não consegui ler".

## Segurança 🔒
- Só leitura. A rota nova é `GET`, sem entrada nenhuma, atrás dos porteiros da Central (Host + crachá).
- Guardar em memória (não em arquivo): nada novo no disco, some quando a API fecha.
- O texto das pendências vem do cofre, então é **dado de fora**: vai para a tela só com `textContent` e cortado em 120 letras.
- Log: `acao=ronda-ultima resultado=ok`, sem conteúdo.

## Arquivos
- Java: `RondaService` (guarda o último resultado + hora) · `CentralRondaController` (`GET /ronda/ultima`) · `RoteadorDoChat` (regra `BOM_DIA`).
- Tela: `index.html` (botão 📰 e o cartão) · `paginas.js` (montar o jornal) · `central.css` (estilo do jornal).
- Testes: `RondaServiceTest` (+2: guarda o último, falha não apaga o anterior) · `CentralRondaControllerTest` (+2: 204 sem ronda, 200 depois; sem crachá 401) · `RoteadorDoChatTest` (+2: bom dia vira jornal, "bom dia, me explica X" vai para a IA).

## Fora deste ticket
- Abrir o jornal sozinho na 1ª visita do dia (fica para depois, se o José quiser).

## Pronto quando
`./mvnw test` verde, jornal aberto no navegador com a API de verdade, gitleaks limpo, commit com OK.

## Evidências (15/11)
- **Java: 479 testes, 0 falhas, 0 erros** (+5): `RondaServiceTest` (+2: guarda a última com a hora; ronda que falha não apaga a boa),
  `CentralRondaControllerTest` (+1: 204 → 200, sem crachá 401, ler nunca roda o defensor), `RoteadorDoChatTest` (+2).
- Tela: `paginas.js` sem erro de sintaxe (conferido com o Node). `central.js` agora entende 204 ("ainda não tem nada").
- gitleaks: nenhum segredo nos arquivos mudados.
- ⏳ Falta: abrir o 📰 no navegador com a API de verdade (José).
