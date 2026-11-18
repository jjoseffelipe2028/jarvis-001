# 080 — Alarme de tentativas
**Status:** ✅ construído e testado (15/11) · ⏳ commit com o OK do José · Seção 5 (Cyber e segurança), item 1

## Ideia
Um pedido negado (401/403) é normal: token digitado errado, Central com crachá vencido.
**5 negados em 1 minuto** já não é normal: parece alguém (ou um script/malware) tentando adivinhar o token.
Nesse caso o Jarvis **avisa por voz** e **registra** — sem bloquear nada e sem dado sensível.
Técnica do MITRE que isso ajuda a perceber: **T1110 (Brute Force)**.

## Como funciona
- **Java** — classe nova `seguranca/AlarmeDeTentativas` (`@Component`):
  - Guarda só a **hora** de cada negado numa fila (janela deslizante de 60 s, teto de 100 itens).
  - Ao chegar em **5 dentro de 60 s** → dispara: conta `disparos`, guarda a hora, escreve **1 linha** no log:
    `alarme=tentativas negados=5 janela=60s`. Depois disso, zera a fila (não dispara a cada negado seguinte).
  - **Mudança na construção:** em vez de pôr `alarme.negado()` em cada porteiro, o alarme virou um **filtro**
    (`OncePerRequestFilter`) que olha a **resposta** de todo pedido: 401 ou 403 = negado. Vantagem: pega todos os
    porteiros (token, Host, Central, `/central/sessao`) **e qualquer porteiro novo**, sem ninguém lembrar de ligar.
  - Rota nova **GET `/acoes/sentinela/alarme`** (com token, entra na allowlist e no `EventosInterceptor`):
    `{"disparos": 2, "ultimoDisparo": "15/11 14:03", "negadosNoMinuto": 1}`.
- **Python**:
  - `bracos.alarme()` chama a rota.
  - `chat.py`: antes de cada resposta, confere o alarme; se `disparos` subiu desde a última vez → **fala**
    "Atenção: houve 5 tentativas negadas em um minuto na API." (frase fixa).
  - "Bom dia" (`rotinas._sentinela`): se houve disparo, entra como alerta (logo depois da isca).

## Segurança
- **Nada sensível guardado nem logado:** sem token, sem IP, sem corpo, sem rota digitada pelo atacante — só horas e contagens.
- **Não bloqueia ninguém:** só avisa (bloquear por tentativas poderia ser usado para travar o próprio José — negação de serviço).
- **Memória limitada:** fila com teto; contador com teto (9.999), como a isca. Some ao reiniciar (o log em arquivo guarda o histórico).
- Relógio injetável (`Clock`) para o teste não depender de esperar 1 minuto.
- Thread-safe (`synchronized`): dois pedidos negados ao mesmo tempo não perdem contagem.

## Testes
- `AlarmeDeTentativasTest` (unidade, relógio falso): 4 negados → não dispara; 5 em 60 s → dispara 1 vez;
  5 espalhados em 2 min → não dispara; 10 seguidos → 2 disparos; log sem nada além de `alarme=tentativas`.
- Ponta a ponta (`MockMvc`): 5 pedidos com token errado → `GET /acoes/sentinela/alarme` mostra `disparos=1`; sem token → 401.
- Python (pytest): `bracos.alarme()`, aviso falado só quando `disparos` sobe, bom dia com o alerta.
- `./mvnw test` e `pytest` verdes.

## Evidências (15/11)
- `AlarmeDeTentativasTest` (7): 4 → não dispara · 5 em 40 s → 1 disparo e zera · 5 em 100 s → não · janela de 60 s exata ·
  10 → 2 disparos · linha fixa no log · 4 threads × 25 negados → 20 disparos (nada perdido).
- `AlarmeDePontaAPontaTest` (4): 5 tokens errados → `disparos=1` e o chute **não** aparece no log · 5 `Host: evil.com` → conta ·
  pedidos certos não contam · ver o alarme sem token → 401.
- `./mvnw test`: **458 testes, BUILD SUCCESS**. Python (`unittest`): **322 testes OK** (9 novos do alarme + 1 do `bracos.alarme()`).
