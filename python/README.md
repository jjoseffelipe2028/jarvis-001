# 🐍 Python — Cérebro e Voz do Jarvis

Tudo roda **local**: nenhuma conversa ou áudio sai do PC. 🔒

| Arquivo | O que faz | Status |
|---|---|---|
| `chat.py` | Cérebro: conversa com o Gemma (Ollama), com personalidade e histórico. **Digite, ou Enter vazio para falar.** Só conversa, não executa nada. | ✅ |
| `ouvido.py` | Ouvidos: grava o microfone e transforma em texto com o faster-whisper (offline). Teste sozinho: `python ouvido.py`. | ✅ |
| `cortes.py` | 🎬 Robô de cortes (Ticket 025): `python cortes.py cacar live.mp4` acha os momentos fortes; `python cortes.py cortar live.mp4 1 3 --arroba @amigo` corta em pé (9:16) com legenda e o @. Usa `cacador.py` + `cortador.py`. Precisa do FFmpeg para cortar. Conferir tudo: `python cortes.py autoteste`. **V6** (`textos.py`): `python cortes.py textos <corte> --arroba @amigo` (a Gemma sugere título, gancho, legenda e hashtags) e `python cortes.py gancho <corte>` (gancho na tela). **Pacote** (Ticket 028, `pacote.py`): `python cortes.py pacote live.mp4 --arroba @amigo` faz tudo de uma vez (caça, corta os 8 mais fortes, textos, gancho) e monta um painel no cofre para aprovar um por um. | ✅ |
| `historinhas.py` | 🍉 Historinhas com bonecos de fruta (Ticket 027): `python historinhas.py roteiro "tema"` (a Gemma escreve 9 cenas; o código põe o personagem sempre igual) e `python historinhas.py montar ep01 --musica trilha.mp3` (junta as cenas do app do Gemini em pé, com legenda e música). | ✅ |
| `proposta.py` | 🤝 Verificador de propostas de marca (Ticket 030): `python proposta.py --remetente contato@marca.com --marca "Marca"`, cole o e-mail e termine com `FIM`. Quem analisa é a API Java (regras fixas, nunca abre link). | ✅ |
| `incidentes.py` | 🗂️ Caderno de Incidentes (Ticket 031): `python incidentes.py` faz a ronda da Sentinela e cria uma nota por alerta, com perguntas de SOC e gabarito dobrado. `python incidentes.py novo "título"` anota um caso seu. | ✅ |
| `backup321.py` | 💾 Guarda do Backup 3-2-1 (Ticket 032): `python backup321.py` confere nuvem, GitHub e cópia offline. Só lê. | ✅ |
| `conferir.py` | 🧰 Conferidor da instalação (Ticket 033): `python python/conferir.py` diz o que falta para o Jarvis rodar. Só biblioteca padrão. | ✅ |
| `duelo.py` | ⚔️ Duelo Hacker × Sentinela (Ticket 035): `python duelo.py` liga uma cópia de teste da API (porta 8091, token falso, pastas temporárias), dispara 13 ataques e mostra o placar com as técnicas do MITRE. | ✅ |
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
