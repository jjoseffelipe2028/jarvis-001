# 025 — Vídeos V5: robô de cortes + caçador de momentos
**Status:** ✅ pronto (12/11) · Python (001) + porteiro Java (José: blocos 1–2 digitados, bloco 3 colado a pedido dele) + testes Java (001, a pedido do José, para acelerar) · ensaio real ok · ⏳ commit com o OK do José
**Criado:** 2026-11-11 pela 001 · Plano aprovado pelo José no chat (4 partes) · Módulo de Vídeos, fase V5

## Ideia
Live longa (24h de GTA do amigo do José, com autorização) → cortes curtos **em pé (9:16)**, com **legenda** e o **@ do amigo**.
Ninguém assiste 24h: o **caçador de momentos** acha onde o som explode (grito, risada, tiroteio) e sugere os trechos.
**Quem escolhe é o José.** O robô só sugere e corta o que foi escolhido.

## Fluxo
1. `python cortes.py cacar live.mp4` → lê o volume da live inteira, acha os 20 maiores picos e grava uma nota no cofre (`decisions/videos/...-momentos.md`).
2. O José abre a nota e escolhe os números.
3. `python cortes.py cortar live.mp4 3 7 12 --arroba @amigo` → para cada um: o porteiro confere, o Whisper legenda e o FFmpeg corta em pé com a legenda e o @.
4. Corte manual também: `--trecho 1:02:10-1:02:40`.

## Regras do porteiro (limites)
| Regra | Valor |
|---|---|
| Duração mínima do corte | **13 s** (mudança do José, 11/11) |
| Duração máxima | 3 min (180 s) |
| Cortes por pedido | no máximo 20 |
| Fim depois do começo, e dentro do vídeo | sim |
| @ do amigo | só letras, números, `_` e `.` (até 30) |
| Espaço livre antes de cortar | pelo menos 2 GB |

## Segurança
- **Original nunca é alterado**: só lido. Os cortes vão para outra pasta.
- Pastas só do `.env` (`JARVIS_CORTES_ENTRADA`, `JARVIS_CORTES_SAIDA`, `JARVIS_PASTA_DECISOES`). Sem pasta → para (falha fechada).
- O arquivo é **só o nome**, dentro da pasta de entrada: `../`, caminho absoluto e atalho (link) são recusados. Só extensões de vídeo da lista.
- **Nome do corte gerado pelo robô** (data + hora + número). FFmpeg com `-n`: nunca sobrescreve.
- FFmpeg chamado com **lista de argumentos, sem shell**: nome malicioso (`a & del *.mp4`) não vira comando.
- Legenda num arquivo `.ass` de nome fixo, numa pasta temporária. O texto do Whisper é "desarmado" (`{`, `}` e `\` viram texto comum): não vira comando de legenda.
- Tudo local: FFmpeg, Whisper e a análise. Nada da live sai do PC.
- Aviso em toda nota: conferir se tem **música da rádio do GTA** no trecho (o robô não detecta).

## Arquivos
- Novos (001): `python/cacador.py` · `python/cortador.py` · `python/cortes.py` (comando) · `python/tests/test_cacador.py` · `python/tests/test_cortador.py`
- **Do José (Java, ele digita):** `video/PedidoDeCorte.java` (record) · `video/PorteiroDeCortes.java` (`liberar(pedido)` → sim/não + motivo) · `PorteiroDeCortesTest.java` (6 testes: normal passa · fim antes do começo · além do fim · menos de 13 s · mais de 3 min · @ estranho).
  O Python já confere as mesmas regras (defesa em camadas). Quando o porteiro Java existir, o comando passa a pedir a liberação para ele.

## Evidências (11/11, 001)
- Python: **113 testes OK** (85 + 28 novos: 10 do caçador, 18 do cortador). Java: não mexido.
- Bug achado pelo teste e corrigido: a média nas pontas do vídeo inventava um "pico" no segundo 0.
- Ensaio de ponta a ponta com uma "live" falsa de 20 min (3 gritos em 5:00, 11:40 e 16:40): achou **5:01, 11:41 e 16:42**, em 0,5 s (24h ≈ 1 min). Nota gravada; ataques barrados (`../`, @ com `;`, corte de 10 s, momento inexistente, sem FFmpeg, sem pasta no `.env`).
- Corte em pé conferido no motor do FFmpeg (PyAV): 1920×1080, 1080×1920 e 1280×720 → **1080×1920**.
- FFmpeg 9.0.2 instalado pelo José (gyan.dev, com libass). **Corte real** (001, 11/11): 1080×1920, h264 + aac, 25 s e 20 s; hash do original igual antes e depois.
- Legenda real: vídeo de teste com fala da voz do Jarvis (Piper) → o Whisper escreveu a legenda embaixo e o @ ficou no topo. ✅
- Bug 2 achado no corte real e corrigido: o faster-whisper 1.2.1 não abre arquivo com o PyAV 19 → o `.wav` agora é lido pelo `ler_wav` (biblioteca padrão) e entregue pronto. **114 testes.**
- 🧪 **Autoteste** novo: `python cortes.py autoteste` monta uma live falsa de 90 s (voz do Jarvis no segundo 40) numa pasta temporária e confere tudo. Resultado na máquina do José: **6 de 6 ✅ em 17 s** (pico em 0:41, porteiro barrou 10 s, 1080x1920, 25 s com áudio, legenda escrita, hash igual).
- 🏋️ **Teste de carga:** live falsa de 2h (2,6 GB) → caçador leu em **36 s, com 2 MB de memória**, e achou o grito em 1:00:01. Uma live de 24h ≈ **7 min**.
- ⚠️ Com ruído de fundo, o Whisper `small` errava palavras ("entível" em vez de "incrível") → **dica de palavras do GTA** (`DICA_GTA`) acertou "incrível". Mesmo assim: **revisar a legenda antes de postar**.
- 📄 Cada corte agora sai com a **legenda separada (`.srt`)** ao lado (sobe junto no YouTube/TikTok; o V6 vai ler). Só é gravada se o vídeo deu certo; nunca sobrescreve. **116 testes**, autoteste **7 de 7**.
- ⏳ Falta: porteiro Java (José digita) e o teste com um vídeo de verdade do José.

## 🎬 Ensaio real com a configuração do José (11/11, 13:43, 001)
- Sem vídeo do José no PC → a 001 montou `Videos\lives\ensaio-gta.mp4` (10 min, 1920x1080): fala calma em 1:30, "olha a polícia, corre!" em 3:00, tiroteio em 5:20, fala empolgada + grito em 7:45.
- `.env` do José (linhas 7 e 8) lido pelo código: as 3 pastas OK.
- `cacar` → **4 de 4 momentos achados em 3 s**: 7:47 (+25,6 dB) · 3:02 · 1:31 · 5:24 (tiros). Nota no cofre: `decisions/videos/2026-11-11-134346-momentos.md`.
- `cortar 1 2 4 --arroba @amigo_gta` → 3 cortes em 35 s (~12 s cada), 1080x1920, @ no topo, legenda certa ("Não acredito, ele pulou de moto em cima do helicóptero, que isso irmão."). O corte do tiroteio sai com `.srt` vazio (não tem fala). ✅
- Original com o **mesmo hash** antes e depois. Ataques: `../lives/...` recusado; trecho de 10 s barrado. ✅
- Pequeno erro do Whisper: "hola" no lugar de "olha" → revisar a legenda antes de postar.

## ☕ Porteiro Java (12/11)
- `PedidoDeCorte.java` (record) e `PorteiroDeCortes.java` (`liberar` → `Liberacao(libera, motivo)`): mesmas 6 regras do Python.
- `PorteiroDeCortesTest`: **7 testes**, todos os limites (13 s passa / 10 s barra, 180 s passa / 181 barra, termina no fim exato passa, @ estranho barra). **Java: 346 testes, 0 falhas.** Python: 116 OK. gitleaks limpo.
- Ainda não ligado ao comando Python: hoje o `cortes.py` usa o porteiro em Python. Ligar os dois (Python pede ao Java) fica para depois do GTA.

## ✅ Checklist do teste real (o José faz)
1. [ ] `python cortes.py autoteste` → precisa dar **AUTOTESTE OK**.
2. [ ] As 2 linhas no `.env` (`JARVIS_CORTES_ENTRADA` e `JARVIS_CORTES_SAIDA`). As pastas `Videos\lives` e `Videos\cortes` já existem.
3. [ ] Um vídeo seu (ou a live antiga do amigo) em `Videos\lives`, com nome simples (ex.: `teste.mp4`).
4. [ ] `python cortes.py cacar teste.mp4` → abrir a nota de momentos no Obsidian (`decisions/videos`).
5. [ ] Escolher 2 momentos → `python cortes.py cortar teste.mp4 1 2 --arroba @seu_arroba`.
6. [ ] Abrir os cortes em `Videos\cortes` no celular: em pé? @ no topo? legenda certa? som ok?
7. [ ] Tentar um ataque: `python cortes.py cacar ../teste.mp4` → tem que **recusar**.
8. [ ] Tudo ok → o José dá o OK para o commit.

## ⚙️ Para ligar no PC (o José faz)
1. Instalar o FFmpeg (fonte oficial, ffmpeg.org → build Windows da gyan.dev): `winget install --id Gyan.FFmpeg -e`
2. Acrescentar no `.env` (a 001 não lê o `.env`):
```
JARVIS_CORTES_ENTRADA="C:/Users/jjose/Videos/lives"
JARVIS_CORTES_SAIDA="C:/Users/jjose/Videos/cortes"
```
(`JARVIS_PASTA_DECISOES` já existe desde o Ticket 013.)

## Fica para depois
- Comando por voz/chat ("Jarvis, caça momentos") — depois do porteiro Java.
- Risada e grito por IA (hoje é só volume).
- Seguir o rosto/ação no corte em pé (hoje corta o centro).
