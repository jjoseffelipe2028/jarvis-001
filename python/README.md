# 🐍 Python — Cérebro e Voz do Jarvis

Tudo roda **local**: nenhuma conversa ou áudio sai do PC. 🔒

| Arquivo | O que faz | Status |
|---|---|---|
| `chat.py` | Cérebro: conversa com o Gemma (Ollama), com personalidade e histórico. **Digite, ou Enter vazio para falar.** Só conversa, não executa nada. | ✅ |
| `ouvido.py` | Ouvidos: grava o microfone e transforma em texto com o faster-whisper (offline). Teste sozinho: `python ouvido.py`. | ✅ |
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
