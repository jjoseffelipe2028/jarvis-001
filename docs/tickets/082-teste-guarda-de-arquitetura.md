# 082 — Teste-guarda de arquitetura
**Status:** ✅ construído e testado (15/11) · commit local (novo combinado do José: itens de Cyber sem esperar o OK do plano; push/merge só com OK) · Seção 5, item 3

## Ideia
As regras de ouro do `CLAUDE.md` estavam só no papel. Agora elas são **teste**: se alguém (pessoa ou IA) quebrar uma,
o build quebra na hora, antes de virar commit. É o que o mercado chama de *architecture test* (ex.: ArchUnit),
feito aqui sem biblioteca nova (só JUnit e `unittest`).

## As 4 regras
| Regra | Java (`ArquiteturaGuardaTest`) | Python (`tests/test_arquitetura.py`) |
|---|---|---|
| 1. Nada fora de 127.0.0.1 | `server.address=127.0.0.1` em um lugar só; nada de `ServerSocket`/`HttpServer`; `"0.0.0.0"` só no leitor do netstat | nenhum texto `0.0.0.0`/`::` |
| 2. Nada de comando livre | `ProcessBuilder` só em 3 classes revisadas; `Runtime.exec` proibido; nenhum shell (`cmd`, `powershell`, `bash`...) | nada de `shell=True`, `os.system`, `os.popen` |
| 3. Só rotas da allowlist | lista das **33 rotas** congelada; rota nova obriga a acrescentar (revisão consciente); nada de curinga (`*`, qualquer verbo); variável de caminho só em `/central/acoes/brabo/video/{nome}` | — |
| 4. Código não lê o arquivo de segredos | nenhum texto citando o arquivo (só a regra *deny* do Estúdio); `System.getenv` só `PATH` e `SystemRoot` (achar git/netstat) | só `bracos.py`, `cortador.py` e `conferir.py` citam o arquivo |

- O código é lido **sem comentários** (Java: removedor que respeita `"http://..."`; Python: `tokenize`). Comentário pode citar a regra sem quebrá-la.
- Ajuda nova de teste `RotasDoJava` (lista as rotas por reflexão), usada também pelo guardião do contrato (081).

## Evidências (15/11)
- **Prova por mutação** (feita e desfeita):
  - Java: classe falsa com `@GetMapping("/acoes/{qualquer}")` + `Runtime.getRuntime().exec({"cmd","/c",qualquer})` → **3 falhas**:
    rota fora da allowlist · variável de caminho fora da lista · `Runtime.exec é proibido`.
  - Python: `subprocess.run("dir", shell=True)` no fim de um arquivo → **falha** `shell=True: o texto viraria comando`.
- `./mvnw clean test`: **477 testes, BUILD SUCCESS**. Python: **336 testes OK**.

## Atenção para o merge
A conversa **jarvis-central** cria rotas novas (ex.: Ticket 060, caixa de pedidos). No merge, este teste vai **falhar de propósito**
até a rota nova entrar em `ROTAS`. Isso é a regra funcionando: rota nova = alguém olhou.
