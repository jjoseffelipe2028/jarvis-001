# 033 — Conferidor da instalação ("o Jarvis roda no meu PC?")
**Status:** ✅ construído e testado (12/11, lote pedido pelo José) · ⏳ commit com o OK

## Ideia
Para quem acabou de clonar o projeto (ex.: um recrutador). Um comando diz o que está pronto e o que falta,
com o passo para resolver:
```
python python/conferir.py
```
Confere: Python 3.10+, Java 21+, git, ambiente `.venv`, arquivo `.env` (chaves principais e opcionais),
Ollama ligado com o modelo Gemma, e o FFmpeg (opcional).

Junto veio o **`.env.example`**: o modelo do `.env`, só com os nomes das chaves e as explicações.

## Segurança
- Só usa a biblioteca padrão do Python (roda antes de instalar qualquer coisa).
- Só **lê**: não instala, não baixa, não muda nada.
- Do `.env` olha só os **nomes** das chaves; o valor nunca sai da função (tem teste para isso).
- A única conversa de rede é com o próprio PC (`127.0.0.1:11434`, o Ollama).
- `.env.example` não tem segredo nenhum (valores vazios). O `.env` de verdade continua fora do Git.

## Evidências
- `test_conferir.py`: 8 testes.
- Real em 12/11: tudo ✅, com aviso das 2 chaves opcionais novas (`JARVIS_PASTA_INCIDENTES`, `JARVIS_BACKUP_OFFLINE`).
