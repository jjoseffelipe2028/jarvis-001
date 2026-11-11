# 024 — "Bom dia, Jarvis" com a Sentinela 🌅🛡️
**Status:** ✅ commit aprovado pelo José (10/11) · ✅ teste real pelo José (10/11, 22:22): Central, Daily, Portas, Travas, Golpe? e as 4 frases no chat
**OK:** José, 10/11 de madrugada ("pode arrochar mais 2 h")
**Criado:** 2026-11-10 pela 001 · Continua o Ticket 009 (bom dia) e os Tickets 021/022 (Sentinela)

## Ideia
O resumo de "bom dia" passa a dizer, numa frase só, como estão as **portas** e as **travas**. Com alerta, a Sentinela fala **logo depois do cumprimento** (segurança primeiro). Sem alerta, fala no fim, antes da cobrança da Cyber.

## Como funciona
- `rotinas._bom_dia` chama também `bracos.portas()` e `bracos.integridade()` (os dois **só leem**).
- Tudo certo → "Sentinela: portas sem alerta e travas iguais."
- Com problema → "Sentinela em alerta: a API do Jarvis está aberta para a rede; 1 alerta nas portas; 1 trava mudou. Peça os detalhes antes de tudo."
- Cada braço continua falhando sozinho: Sentinela fora do ar (ex.: fora do Windows) → o resumo segue sem ela. Resposta torta → a parte torta é pulada.
- Alarme de segurança (impostor, assinatura recusada) continua interrompendo o resumo (regra do Ticket 009).

## 🔒 Travas
- O bom dia **só lê**: o teste confere que só hora, pendências, git, portas e integridade são chamados (nunca o daily, nunca o detector de golpe).
- A frase usa só números e textos fixos: nenhum nome de arquivo ou nota da API é falado no resumo.

## Evidências
- Python: `test_rotinas` +3 (tranquila antes da Cyber, alerta logo depois do bom dia, fora do ar ou torta não derruba) e o teste "só lê" atualizado. **85 testes Python** OK.
- Teste ao vivo (001, cópia da API na 8091): "bom dia, Jarvis" → "Boa noite, José! São 02:49. Nenhuma pendência nos últimos 7 dias. **Sentinela: portas sem alerta e travas iguais.** E a Cyber? Reserve 30 minutos para ela hoje."
- Teste real (José): _(preencher)_
- Commit: _(preencher)_

## Custo
O `netstat` pode levar até 8 s no pior caso (limite do Ticket 021). No normal, o bom dia fica cerca de 1 s mais lento.

## 🎤 Para a entrevista
"Integrei a Sentinela na rotina de bom dia: se algo de segurança mudou, é a primeira coisa que o Jarvis fala, e cada parte falha sozinha sem derrubar o resumo."
