# 063 — Super agentes como salas no escritório 🏢
**Status:** ✅ construído e commitado no ramo (15/11), pelo combinado do José de seguir sem OK por item · ⏳ olhar no navegador · seção 2 da lista mestra

## Ideia
O escritório pixel tem 3 mesas (001, Jarvis, 002). Os super agentes ganham uma **ala de salas** logo abaixo da cena:
🛡️ Defensor · 🗼 Sentinela · ✂️ Produtor de cortes · 🎙️ Apresentador · 🎮 Estúdio 001 · 📬 Caixa da 001.
Quando chega um evento real daquele agente, **a luz da sala acende** (verde = ok, vermelho = recusado/falhou) por 8 s,
e fica escrito "última vez 18:42 ✅". **O escritório cresce**: um agente novo = uma linha na lista `SALAS`.

## Como funciona
- Só tela (`escritorio.js`, `index.html`, `central.css`). **Nenhuma rota nova, nenhum Java novo.**
- Usa o mesmo quadro de eventos que o escritório já ouvia (Ticket 017). A função `reagir()` agora também chama `acenderSala()`.

## Segurança 🔒
- Lista fixa: um nome de ação que não está em `SALAS` não cria sala nem acende nada.
- Texto só com `textContent`. Nada guardado no navegador (a regra do `CentralPaginaTest` continua valendo).
- **Teste-guarda novo** (`CentralPaginaTest.salasDoEscritorio_soUsamAcoesDoQuadro`): toda ação usada por uma sala precisa
  existir no `EventosInterceptor.ROTAS`. Se alguém criar uma sala para uma ação que a API não anota, o teste quebra
  (a sala ficaria apagada para sempre, e ninguém perceberia).

## Evidências (15/11)
- **Java: 480 testes, 0 falhas, 0 erros** (+1, o teste-guarda).
- `escritorio.js` sem erro de sintaxe (conferido com o Node). gitleaks limpo.
- ⏳ Falta: ver as salas acendendo no navegador (clicar no 🛡️ e ver o Defensor acender).

## Fora deste ticket
- Desenhar as salas dentro do canvas pixel (com bonecos próprios). Fica para quando o José quiser mais arte.
