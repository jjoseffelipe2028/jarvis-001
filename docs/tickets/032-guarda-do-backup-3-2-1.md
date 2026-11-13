# 032 — Guarda do Backup 3-2-1 (defesa contra ransomware)
**Status:** ✅ construído e testado (12/11, lote pedido pelo José) · ⏳ commit com o OK

## Ideia
A regra: **3 cópias · 2 mídias · 1 fora de casa**. O guarda confere 3 coisas e dá a nota:
1. **Nuvem:** o cofre do Obsidian está dentro da pasta do OneDrive?
2. **GitHub:** o repositório está em dia (nada sem commit, nada sem push)?
3. **Offline:** existe cópia num pendrive/HD (`JARVIS_BACKUP_OFFLINE`) com menos de 7 dias?

## Como usar
```
python backup321.py
```
Saída de exemplo: `Backup 3-2-1: 2 de 3. Falta: falta a cópia offline (...)`.

## Segurança
- Só **lê**: não copia, não apaga, não usa a internet (sem `fetch`).
- `git` com comando fixo, em lista, sem shell, com tempo limite e sem pedir senha.
- **Na dúvida, não dá o ponto** (falha fechada): pasta não configurada ou git com erro = ❌.
- A cópia offline é olhada sem seguir atalho e com limite de 5000 arquivos (não trava em HD gigante).

## Evidências
- `test_backup321.py`: 9 testes (git falso, pastas temporárias).
- Real em 12/11: `1 de 3` (nuvem ✅ · 18 arquivos sem commit ❌ · sem cópia offline ❌). Estava certo.

## Depois
- Falar a frase no "bom dia" (precisa de um braço Java novo: fica para outro ticket).
