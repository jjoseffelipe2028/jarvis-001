# 023 — Sentinela: detector de golpe 🎣
**Status:** ✅ commit aprovado pelo José (10/11) · ⏳ teste real depois de reiniciar a API
**OK:** José, 10/11 de madrugada ("pode arrochar mais 2 h")
**Criado:** 2026-11-10 pela 001 · Roteiro: "Módulos: Sentinela (só avisa)" · Ideia do José de 01/11 ("Sentinela comandante cyber")

## Ideia
José cola uma mensagem suspeita (WhatsApp, SMS, e-mail) e o Jarvis diz se ela tem **cara de golpe brasileiro**: falso parente, PIX com pressa, banco falso, prêmio, pedido de código. Ele **nunca abre o link**: só lê o texto. E **só avisa**: quem decide é o José.

## Como funciona
1. **Java:** `DetectorDeGolpe` é uma função pura com **regras fixas escritas à mão** (sem IA): mesma mensagem, mesma resposta, fácil de testar e de explicar.
2. O texto é comparado sem acento e em minúsculas. Cada sinal soma pontos e um **motivo fixo**:
   - falso parente ("troquei de número") **+3** · pede senha/código/CVV **+3**;
   - PIX, boleto, taxa **+2** · pressa ("urgente", "será bloqueada") **+2** · prêmio ("você ganhou") **+2**;
   - pede segredo **+1** · nome de órgão (Receita, Detran, Correios) **+1**.
3. Cada link no texto é olhado **como texto** (até 10 links):
   - "@" no endereço **+3** (o site de verdade é o que vem depois do @);
   - IP no lugar do nome **+3** · punycode (`xn--`) ou letra estrangeira (ex.: "а" russo) **+3**;
   - imita marca (nubank, itau, caixa, gov…) sem ser o domínio oficial **+3**;
   - encurtador (bit.ly, cutt.ly…) **+2** · final suspeito (.xyz, .top…) **+1** · `http://` sem cadeado **+1**.
4. Nível: **0–1 baixo**, **2–3 suspeito**, **4+ alto** (dois sinais fortes juntos = o desenho clássico do golpe). Cada nível tem um conselho fixo.
5. Rotas (não alteram nada no PC):
   - `POST /acoes/sentinela/golpe` (voz/terminal, pedido assinado);
   - `POST /central/acoes/sentinela/golpe` (botão 🎣 Golpe? + skill "Detector de golpe" no escritório).
6. **Python:** "isso é golpe: <mensagem>", "verifica essa mensagem: ...", "é golpe? ..." → ação `golpe`. "Golpe do pix é comum?" continua **conversa**.

## 🔒 Travas
- **Nunca abre link** e não tem cliente de rede: nada sai do PC.
- A resposta tem **só textos fixos** do detector (motivos e conselho): o texto colado nunca volta, então não vira XSS nem "fala" de golpista.
- O texto colado **não vai para o log**: só o nível e quantos links (`acao=golpe nivel=alto links=1`).
- Limite de 4000 caracteres (400) e de 64 KB no corpo (413). No máximo 10 links analisados.
- Regex testado contra **ReDoS** (texto feito para travar o regex): 5 ataques de 4 a 10 mil caracteres, todos em menos de 0,5 s.
- No Python, o pedido de golpe é conferido **antes** da lista proibida: a mensagem colada ("faz um PIX e apaga a conversa") é do golpista, não um pedido do José, e nunca vira ação.
- Na Central: crachá + cabeçalho próprio + **`Origin` obrigatório** (é POST); o nível é conferido contra a lista antes de mostrar.

## Evidências
- Java: `DetectorDeGolpeTest` (12: golpes reais, mensagens normais, site oficial não é imitação, texto colado nunca volta, motivo não repete, ReDoS), `SentinelaControllerTest` +2, `CentralSentinelaControllerTest` +2, `CentralPaginaTest` +1. **339 testes Java**, 0 falhas.
- Python: `test_intencoes` +6, `test_bracos` +2. **85 testes Python** OK.
- Teste ao vivo (001, cópia da API na 8091, token falso):
  - falso parente + PIX → **alto**; SMS "NUBANK" com `nubank-seguranca.xyz` → **alto**; prêmio + `bit.ly` → **alto**;
  - mensagem normal → **baixo**; "golpe do pix é comum?" → conversa;
  - botão 🎣 na Central: golpe do FGTS com `caixa-fgts.top` → 🚨 com 5 motivos;
  - ataques: sem token 401, token falso 401, Host falso 403, sem crachá 401, sem `Origin` 403, site de fora 403, 4001 letras 400, 70 KB 413;
  - log: **0** linhas com o texto colado.
- Teste real (José): _(preencher)_
- Commit: _(preencher)_

## Limites (honestos)
- Regras fixas pegam os golpes **comuns**. Golpe novo, bem escrito e sem link pode passar como "baixo": por isso o conselho do "baixo" ainda diz "nunca passe senha ou código".
- Mensagem normal com "boleto" ou "urgente" pode dar "suspeito". Errar para o lado da cautela é de propósito.
- No terminal, cole a mensagem **numa linha só** (cada Enter vira uma pergunta nova). Mensagem longa: use o botão 🎣 da Central.

## 🎤 Para a entrevista
"Fiz um detector de golpe brasileiro com regras explicáveis: ele pontua sinais como falso parente, PIX com pressa e link que imita banco, nunca abre o link, não guarda o texto no log e foi testado contra ReDoS."
