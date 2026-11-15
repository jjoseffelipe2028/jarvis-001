# 064 — Sala de Treino Cyber: "Simular ataque" 🥋
**Status:** ✅ construído com o OK do José ("Pode construir o 064", 15/11) e commitado no ramo · ⏳ teste no navegador · seção 1/2 da lista mestra

## Ideia
Na página Cyber, uma "Sala de Treino" com botões **Simular ataque**:
1. 🌐 **CSRF**: um site malicioso tenta mandar um POST para a Central (Origin `https://site-malicioso.example`).
2. 🔁 **DNS rebinding**: o pedido chega com `Host: evil.com` (o truque de apontar um domínio para 127.0.0.1).
3. 🎫 **Sem crachá**: alguém chama `/central/acoes/...` sem o cookie da Central.
4. 🕵️ **Token falso**: chama `/acoes/...` com um `X-Jarvis-Token` errado.

A tela mostra, passo a passo: "ataque montado → porteiro X recusou com 403 → por quê", e o escritório acende 🚨.

## Como funciona (sem ataque de verdade pela rede)
- `POST /central/acoes/treino` com `{"ataque":"csrf"}`. O ataque vem de um **enum fechado** (4 nomes); nada mais vem da tela.
- O Java **não manda pedido nenhum pela rede**. Ele monta um pedido de mentira em memória (um `HttpServletRequestWrapper`
  com o Origin/Host/cookie trocados) e passa pelos **mesmos porteiros de verdade** (`HostInterceptor`, `CentralInterceptor`,
  `TokenInterceptor`), chamando só o `preHandle`. O controller de destino **nunca roda**, então nada é escrito, nem se um porteiro falhar.
- Resposta: `{"ataque":"csrf","barrado":true,"porteiro":"CentralInterceptor","status":403,"explicacao":"..."}`.
  Se um porteiro deixar passar, a tela mostra **🚨 FALHA NA DEFESA** e o log grava `resultado=defesa-falhou`.

## Segurança 🔒
- Lista fechada de ataques; rota atrás dos porteiros normais (crachá + Origin da própria Central).
- Nenhuma conexão de rede, nenhum processo novo, nenhum arquivo escrito.
- Os porteiros são reaproveitados, não copiados: se alguém afrouxar um porteiro, o treino mostra na hora.
- Teste-guarda: os 4 ataques têm que dar `barrado=true` no `./mvnw test`. Vira um "duelo" que roda a cada build.

## Arquivos
- Java: `acoes/treino/AtaqueSimulado` (enum), `TreinoService`, `central/CentralTreinoController` · `WebConfig`/`EventosInterceptor`.
- Tela: página Cyber (4 botões + passos) · `escritorio.js` (sala 🥋 Treino).
- Testes: `TreinoServiceTest` (4 ataques barrados + "porteiro desligado = falha detectada"), `CentralTreinoControllerTest` (enum inválido 400, sem crachá 401).

## Evidências (15/11)
- **Java: 490 testes, 0 falhas** (+7). `TreinoServiceTest` (5): os 4 ataques barrados pelo porteiro certo
  (CSRF → `CentralInterceptor`, rebinding → `HostInterceptor`, sem crachá → `CentralInterceptor`, token falso → `TokenInterceptor`);
  **controle**: um pedido legítimo passa pelos mesmos porteiros (prova de que o "barrado" não é defeito do pedido falso);
  **porteiro afrouxado** é detectado ("defesa falhou"); o pedido real não é mexido; nomes fora da lista recusados.
  `CentralTreinoControllerTest` (2): ponta a ponta com os porteiros reais; `sqli` → 400; sem crachá → 401.
- O log marca `aviso=simulacao_comeca` antes dos "negado" do treino, para a auditoria não confundir treino com ataque real.
- Escritório: sala 🥋 Sala de Treino. gitleaks limpo.
- ⏳ Falta: clicar nos 4 botões na página Cyber com a API de verdade (José).
