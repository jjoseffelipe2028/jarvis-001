# 012 — Vídeos V1: porteiro do "Laboratório Brasil"
**Status:** ✅ aprovado pelo José (2026-11-09)
**Criado:** 2026-11-09 pela 001 · Módulo de Vídeos, fase V1 de 7

## Ideia
O Brasil é o laboratório. Só o vídeo que **provar retenção** na página brasileira ganha dublagem para outros idiomas. Assim ninguém gasta tempo dublando vídeo fraco.

## O que faz
`POST /acoes/video/avaliar` 🔒
```json
{"duracaoSeg":190,"proporcao3sPara1min":55.5,"mediaAssistidaSeg":80}
→ {"libera":true,"motivos":["proporção 3s→1min acima de 50%","média assistida de 1m14s ou mais"]}
```

| Regra | Resultado |
|---|---|
| Proporção 3s→1min **acima de 50%** | libera |
| Média assistida **≥ 74 s** (1m14s) | libera |
| Média **≤ 20 s** (alerta de enterro) | bloqueia sempre |
| Vídeo **< 60 s** | bloqueia: a métrica 3s→1min não existe nele |
| Média maior que a duração | 400 (número digitado errado) |

## Segurança
- Rota em `/acoes/**`: já nasce atrás dos porteiros (Host + assinatura). Provado pelo `RotasPostContratoTest`.
- Regra pura: não lê arquivo, não chama rede, não executa nada.
- Entrada com limites (`@Valid`): negativo, > 100%, NaN, texto, > 10 h → 400.
- Log só com texto fixo (`resultado=libera/bloqueia`), sem os números.

## Arquivos
- `acoes/video/PortaoDoLaboratorio.java` (a regra) · `VideoController.java` · `AvaliacaoRequest.java` · `Avaliacao.java`
- Testes: `PortaoDoLaboratorioTest` (12, limites exatos) · `VideoControllerTest` (12, ponta a ponta)
- `RotasPostContratoTest`: registra o serviço novo

## Evidências
- Java: **182** testes, 0 falhas (+24).
- Commit: `65b0c3e`

## Fica para depois
- O Python ainda não chama esta rota por voz (entra junto com o V2).
- Os números vêm do painel da plataforma, digitados à mão. Ler CSV = V2.

## 🎤 Pergunta para o José
> Um vídeo tem proporção de **90%**, mas média de **18 s**. Por que o porteiro bloqueia, se a proporção está ótima?
