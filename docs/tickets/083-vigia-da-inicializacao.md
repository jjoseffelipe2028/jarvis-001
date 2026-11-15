# 083 — Vigia da inicialização do Windows (só leitura)
**Status:** ✅ construído e testado (15/11) · commit local · Seção 5, item 4

## Ideia
Malware quer voltar a cada reinício. O jeito mais comum é se pôr na lista do que liga com o Windows
(MITRE ATT&CK **T1547.001**: Registry Run Keys / Startup Folder). O vigia lista esses itens e avisa o que é **novo**
ou o que **mudou de programa** desde a última vez (truque comum: manter o nome "OneDrive" e trocar o `.exe`).

## Como usar
```bash
python python/vigia_inicio.py             # mostra e marca 🆕 novos, ⚠️ mudou, ➖ saiu
python python/vigia_inicio.py --lembrar   # guarda os de agora como conhecidos
```

## O que lê (lista FIXA)
- Registro: `HKCU` e `HKLM` → `...\CurrentVersion\Run` e `RunOnce`, e o `Run` de 32 bits (`WOW6432Node`).
- Pastas "Inicializar" do usuário e de todos: **só os nomes** dos arquivos.

## Segurança 🔒
- **SÓ LÊ:** registro aberto com `KEY_READ`; nada de `SetValue`/`DeleteValue`/`KEY_WRITE`; não roda o programa listado;
  sem `subprocess`, sem shell. Um teste lê o próprio código e falha se aparecer qualquer um desses.
- **Nunca desativa nada.** Quem decide é o José (Gerenciador de Tarefas → Aplicativos de inicialização).
- `.inicio-conhecido.json` (fora do Git) guarda só **nome + hash** do comando, nunca o caminho (que mostra o usuário do Windows).
- Nome com caractere de controle ou truque de direção (U+202E, que faz `exe.txt` parecer `txt.exe`) é limpo antes de aparecer.
- `resumo()` devolve **só números** para o relatório semanal (Ticket 084).

## Evidências (15/11)
- `test_vigia_inicio.py`: **15 testes** (novo, mudou de programa, saiu, arquivo torto, nada de caminho no arquivo, RLO, só leitura).
- Rodada real neste PC (sem `--lembrar`, nada gravado): "11 item(ns) ligam com o Windows. Ainda não tem lista de conhecidos".
- ⏳ José: rodar, conferir os 11 itens e rodar `--lembrar` (decisão dele de quais são de confiança).
