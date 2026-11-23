# 088 — Strix e WhiteRabbitNeo (só pesquisa e recomendação)
**Status:** 🔎 pesquisa (16/11) · ⏳ **decisão do José** · nada instalado · Seção 5, item 9

## Strix — "hackers de IA" que testam a sua aplicação
| | |
|---|---|
| **O que é** | Agentes de IA que fazem pentest em app web: reconhecimento, exploração e prova de conceito. Têm proxy HTTP (Caido), navegador, shell e Python dentro de um container. |
| **Quem mantém** | Org `usestrix` no GitHub (muito popular); copyright da **OmniSecure Inc.** Também vende uma versão em nuvem (Strix Cloud). |
| **Licença** | **Apache 2.0** (aberta). |
| **O que instala** | O pacote `strix-agent` (PyPI) + **Docker obrigatório** (baixa uma imagem de sandbox na 1ª vez). O README sugere `curl ... \| bash` — **não usar**: rodar script baixado direto no shell. Preferir `pipx install strix-agent`. |
| **Manda dado para fora?** | **Sim, por padrão.** Ele precisa de uma IA (`STRIX_LLM` + `LLM_API_KEY`): os modelos recomendados são de **nuvem**, então o que ele vê do seu app vai para esse provedor. Dá para apontar para um modelo local (`LLM_API_BASE`, ex.: Ollama), mas a Gemma `e2b` é pequena demais para agente de pentest. Avisa versão nova (consulta a internet). A versão Cloud sobe o código só se você mandar. |
| **Riscos** | Ataca **de verdade** (exploração ativa): só pode mirar o que é seu. Docker + agente com shell = mais superfície no PC. Custo de API se usar modelo de nuvem. |

**Como seria seguro no Jarvis:** só contra a **cópia de teste** do duelo (`127.0.0.1:8091`, token falso, pastas temporárias),
nunca contra a API de verdade nem com o arquivo de segredos por perto. O código do Jarvis já é público no GitHub, então
mandar o código para um modelo de nuvem não vaza nada novo — mas **respostas da API com dados do cofre** vazariam, por isso só a cópia de teste.

**Recomendação da 001:** 🟡 **Opcional, depois do 1.0.** O Jarvis já tem o duelo (13 ataques) e agora o redteam da Gemma.
O Strix seria uma "segunda opinião" automática. Se o José quiser: 1 rodada, contra a cópia de teste, com Docker, e o relatório vira nota no cofre.

## WhiteRabbitNeo — modelo de IA "sem censura" para cyber
| | |
|---|---|
| **O que é** | Família de modelos treinados para segurança ofensiva e defensiva (responde sobre exploits que outros modelos recusam). A versão 3 (7B) é baseada no Qwen2.5-Coder-7B. |
| **Quem mantém** | Projeto WhiteRabbitNeo, patrocinado pela **Kindo** (kindo.ai). |
| **Licença** | **Depende da versão.** V3-7B: Apache 2.0 **+ restrições extras de uso** ("Extended Version"). Versões antigas: licença DeepSeek, Llama 2 ou Llama 3.1. Ler a licença da versão exata antes de usar. |
| **O que instala** | Um modelo no Ollama (`WhiteRabbitNeo/WhiteRabbitNeo-V3-7B`, ~4–5 GB). Cabe na RTX 4050. |
| **Manda dado para fora?** | **Não**, rodando no Ollama local. (A versão web/Kindo é outra história: não usar.) |
| **Riscos** | Sem filtro: gera comando ofensivo sem pensar. **Nunca** ligar no `chat.py` nem em ação do Jarvis. Cópias "repack" de terceiros no Hugging Face podem vir alteradas: só baixar do perfil oficial. Disco e GPU. |

**Recomendação da 001:** 🟡 **Opcional, só para estudo, separado do Jarvis.** Usos bons: gerar **novos ataques** para o
`redteam_gemma.py` (Ticket 085) e explicar CVEs nos estudos da EBAC. Se instalar: só pelo Ollama, perfil oficial, e
fica fora de qualquer rota do Jarvis.

## Decisão (do José)
- [ ] Strix: não / depois do 1.0 / 1 rodada na cópia de teste com modelo ______
- [ ] WhiteRabbitNeo: não / sim, só estudo (Ollama oficial)

## Fontes
- https://github.com/usestrix/strix · https://github.com/usestrix/strix/blob/main/LICENSE · https://pypi.org/project/strix-agent/1.1.0/
- https://huggingface.co/WhiteRabbitNeo/WhiteRabbitNeo-2.5-Qwen-2.5-Coder-7B · https://registry.ollama.ai/WhiteRabbitNeo/WhiteRabbitNeo-V3-7B
- https://www.securityweek.com/whiterabbitneo-high-powered-potential-of-uncensored-ai-pentesting-for-attackers-and-defenders/
