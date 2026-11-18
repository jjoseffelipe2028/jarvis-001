# 086 — Quiz de Cyber por voz
**Status:** ✅ construído e testado (16/11) · commit local · Seção 5, item 7

## Ideia
Treino para a entrevista usando o **próprio Jarvis** como matéria: 5 perguntas sorteadas de um banco de 15
(allowlist, CSRF, timing attack, Path Traversal, honeytoken, T1110, T1547.001, prompt injection, gitleaks,
log injection, DNS rebinding, contrato Java ↔ Python...). O Jarvis lê a pergunta e as 3 opções; o José responde
A/B/C (digitando ou falando) e ouve o **porquê** — a frase que ele usaria na entrevista.

```bash
python python/quiz_cyber.py             # digitando (fala, se a boca estiver instalada)
python python/quiz_cyber.py --voz       # respondendo pelo microfone
python python/quiz_cyber.py --anotar    # anota "Quiz de Cyber: 4/5" no daily (API, pedido assinado)
```

## Segurança 🔒
- Perguntas e explicações **fixas no código**: nada de IA inventando gabarito (sem alucinação), nada da internet.
- O texto ouvido só é comparado com A/B/C (ou com o texto de uma opção): **nunca vira comando**; texto gigante é cortado.
- Escrever no daily só com `--anotar` (ação de escrita sempre de propósito), pelo `Bracos` (assinado, allowlist).

## Evidências
- `test_quiz_cyber.py`: **9 testes** (banco bem formado e gabarito variado, "letra B"/"a resposta é cê"/"bê",
  resposta estranha → não entendi, placar e porquê, sorteio sem repetir, `--anotar` só quando pedido, API desligada).
- ⏳ José: rodar com `--voz` e ver se o ouvido entende "A/B/C" bem (o "cê" às vezes vira "se": já aceito).
