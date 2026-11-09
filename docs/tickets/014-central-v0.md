# 014 — Central v0: a tela do Jarvis + ícone na Área de Trabalho
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001 · Fase 5, parte 1

## Ideia
Dois cliques num ícone e abre a **Central do Jarvis**: uma janela própria (parece um programa), com o núcleo 001 + 002 e os 6 módulos (Vídeos, Cyber, Jogos, Estudos, Código, Memória). 100% local e offline.

## O que faz
| Peça | Arquivo |
|---|---|
| Página da Central (servida pela API em `http://127.0.0.1:8080/`) | `java/bracos/src/main/resources/static/index.html` · `central.css` · `central.js` |
| Hora ao vivo + "API online/desligada" (via `/acoes/hora`, pública, 1x por minuto) | `central.js` |
| Lançador: liga a API se estiver desligada, espera até 2 min e abre em **modo app** (Edge `--app`) | `Jarvis-Central.bat` |
| Ícone do Jarvis | `assets/jarvis.ico` · `assets/jarvis-icone.png` |
| Atalho "Jarvis 001" na Área de Trabalho | criado no PC do José (não vai para o Git) |

## Segurança
- **Offline:** nada vem da internet (nem fonte, nem biblioteca). Teste varre os 3 arquivos atrás de endereço externo.
- **CSP trancada:** só carrega arquivos do próprio Jarvis; nenhum script embutido; sem `unsafe-inline`/`unsafe-eval`.
- **O navegador nunca recebe o token:** a Central só usa a rota pública `/acoes/hora`, sem cookies (`credentials: "omit"`) e sem `localStorage`.
- **Resposta da API nunca vira código:** só `textContent` (teste proíbe `innerHTML`, `eval` etc.).
- Os porteiros de `/acoes/**` não mudaram. A página é estática e não tem segredo.
- O `.bat` não lê nem mostra o token (quem carrega o `.env` continua sendo o `iniciar.sh`).

## Evidências
- Java: **221** testes, 0 falhas (+9: `CentralPaginaTest`).
- Teste real (18:43): API desligada → 2 cliques no ícone → API ligou minimizada → Central abriu com **API ● online**, relógio e data. Print do José.
- Commit: `a054afc`

## Fica para depois
- **v1:** botões dos módulos com ações reais + cartão "🌐 isso vai para a nuvem" com Aceitar/Recusar para 001/002.
- **v2:** escritório pixel no espaço reservado.
- App instalado (JavaFX/Tauri) reaproveitando esta mesma página.
- Desligar a API: fechar a janela "Jarvis API".
