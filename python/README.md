# 🐍 Python — Cérebro e Voz do Jarvis

Tudo roda **local**: nenhuma conversa ou áudio sai do PC. 🔒

| Arquivo | O que faz | Status |
|---|---|---|
| `chat.py` | Cérebro: conversa por texto com o Gemma (Ollama), com personalidade e histórico. Só conversa, não executa nada. | ✅ |
| `ouvido_teste.py` | Ouvidos: grava o microfone (Enter → fala → Enter) e transforma em texto com o faster-whisper. | 🔄 em teste |
| `requirements.txt` | Lista de bibliotecas (`pip install -r requirements.txt`). | ✅ |

## Rodar
```bash
source .venv/Scripts/activate
python chat.py
```

## Próximos passos
- [ ] Ligar os ouvidos no `chat.py`
- [ ] Voz (texto → fala) local
- [ ] Usar a GPU (RTX 4050)
