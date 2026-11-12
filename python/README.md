# 🐍 Python — Cérebro e Voz do Jarvis

Tudo roda **local**: nenhuma conversa ou áudio sai do PC. 🔒

| Arquivo | O que faz | Status |
|---|---|---|
| `chat.py` | Cérebro: conversa com o Gemma (Ollama), com personalidade e histórico. **Digite, ou Enter vazio para falar.** Só conversa, não executa nada. | ✅ |
| `ouvido.py` | Ouvidos: grava o microfone e transforma em texto com o faster-whisper (offline). Teste sozinho: `python ouvido.py`. | ✅ |
| `cortes.py` | 🎬 Robô de cortes (Ticket 025): `python cortes.py cacar live.mp4` acha os momentos fortes; `python cortes.py cortar live.mp4 1 3 --arroba @amigo` corta em pé (9:16) com legenda e o @. Usa `cacador.py` + `cortador.py`. Precisa do FFmpeg para cortar. Conferir tudo: `python cortes.py autoteste`. | 🟡 |
| `requirements.txt` | Lista de bibliotecas (`pip install -r requirements.txt`). | ✅ |

## Rodar
```bash
source .venv/Scripts/activate
python chat.py
```

## Próximos passos
- [x] Ligar os ouvidos no `chat.py`
- [ ] Voz (texto → fala) local
- [ ] Usar a GPU (RTX 4050)
