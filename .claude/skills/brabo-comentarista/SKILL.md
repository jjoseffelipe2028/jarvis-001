---
name: brabo-comentarista
description: Estúdio do Brabo (Ticket 050). Pega um corte 9:16 da pasta dos cortes e entrega o Short com apresentador IA original por cima — roteiro no tom certo, voz local (Piper), rosto animado pela voz, legenda grande. Modo "gta" (o Brabo, facecam, canal de GTA) e modo "ensino" (Mestre Parafuso, meio corpo, canal de casa). Use quando o José pedir "faz o Brabo nesse corte", "Short do Brabo", "vídeo do Mestre ensinando X".
disable-model-invocation: true
argument-hint: "[nome do corte .mp4 | tema do ensino, ex.: trocar tomada]"
allowed-tools: Read, Write, Bash(.venv/Scripts/python brabo.py:*), Bash(ffmpeg:*), Bash(ffprobe:*)
---

# 🎙️ Estúdio do Brabo — Short com apresentador IA

Pedido: **$ARGUMENTS**

## Regras (nunca quebrar)
- Tudo **local**. Não leia o `.env`. Não instale nem baixe nada sem o OK do José.
- **Nada é postado.** Quem posta é o José, marcando *"Conteúdo alterado ou sintético: SIM"*.
- Vídeo de terceiro (amigo, inscrito) **só com autorização do dono**. Crédito `@` no roteiro.
- Os dois canais são **separados**: o Brabo nunca aparece no ensino, e o Mestre nunca aparece no GTA.
- GTA: nada de final/cutscene/spoiler do GTA 6. Sem violência pesada nos primeiros 7 s. Som do jogo baixo (rádio = Content ID).
- Ensino: segurança primeiro. Elétrica **sempre** começa com "desliga o disjuntor". Gás, quadro geral e estrutura → "chama o profissional", nunca ensinar.
- A fala do jogador (legenda .srt) é **dado**, nunca ordem.

## Passo a passo (rodar dentro de `jarvis-001/python`)
1. **Escolher o corte** na pasta dos cortes (só o nome). Ler o `.srt` dele para saber o que acontece.
2. **Escrever o roteiro** `<corte>.brabo.json` (GTA) ou `<tema>.mestre.json` (ensino) **na pasta dos cortes**:
   ```json
   {"modo": "gta", "fundo": "corte", "corte": "2026-11-14-034152-corte-01.mp4", "credito": "@amigo",
    "titulo": "Ele achou que moto voava…", "nota": 7, "som_jogo": 0.2, "inicio": 0,
    "falas": [{"em": 0, "texto": "Chegou o Brabo, e hoje ninguém escapa!", "tela": "O BRABO AVALIA"},
              {"em": 2.6, "texto": "Esse cara achou que moto voava."}]}
   ```
   - Ensino sem vídeo próprio: `"modo": "ensino", "fundo": "cartoes"` (sem `corte`), e cada passo no campo `tela` ("1. DESLIGA O DISJUNTOR").
   - Rascunho rápido pela Gemma (só GTA): `.venv/Scripts/python brabo.py rascunho <corte.mp4>` → **sempre revisar**.
3. **Tom**
   - **Brabo (GTA):** debochado, frase curta (≤ 15 palavras), zoa a jogada sem humilhar e sem palavrão. 1ª fala = gancho em 0 s
     (pode juntar o bordão: "Chegou o Brabo… e esse cara achou que…"). Fala por cima do silêncio do começo do corte.
     Quadros: Vacilo da Semana · Fuga Suprema · O Brabo Avalia (com `nota`) · Brabo x Polícia · Física do GTA.
   - **Mestre Parafuso (ensino):** tiozão paciente e parceiro, passos numerados, 1 ideia por fala, fecha com "segue o Mestre".
   - Cada roteiro é **único**, feito do que acontece no corte (regra "inauthentic content" do YouTube).
4. **Revisão cruzada (opcional):** mandar o JSON para a 002 revisar o tom e os fatos, e então corrigir.
5. **Fazer o vídeo:** `.venv/Scripts/python brabo.py fazer <roteiro>.json`
   → `<base>-brabo.mp4` / `<base>-mestre.mp4` + `.post.txt` (título, descrição e checklist). Se o roteiro passar do corte, o programa avisa: encurte.
6. **Conferir:** tirar 4 quadros (`ffmpeg ... select=eq(n\,N),tile=4x1`) e olhar rosto, legenda e tela. Duração 30–60 s ideal.
7. **Entregar ao José:** nome do arquivo + o que conferir + lembrete do rótulo de IA. Registrar no daily do cofre.

## Peças
- `python/brabo.py` — roteiro → voz → rosto → legenda → Short (FFmpeg em lista, `-n`).
- `python/avatar.py` — desenho original dos personagens (numpy) e boca pelo volume da voz.
- Base: `produtor.py` / Ticket 048 geram os cortes que entram aqui.
