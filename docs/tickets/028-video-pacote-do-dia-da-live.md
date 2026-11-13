# 028 — Vídeos: pacote do dia da live (um comando só)
**Status:** ✅ construído e ensaiado (12/11) · pedido do José: "bora arrochar" · ✅ no GitHub (12/11)
**Módulo de Vídeos, junta o V5 e o V6**

## Ideia
No dia da live de 24h (19/11) ninguém tem tempo de rodar 4 comandos por corte. Um comando faz a fila inteira:
1. usa a caçada do vídeo (se ainda não caçou, caça);
2. corta os **N momentos mais fortes** (padrão 8, máximo 20), em pé, com legenda e o @;
3. pede à **Gemma** os títulos, o gancho, a legenda e as hashtags de cada corte;
4. grava o **gancho na tela** (vídeo novo, o corte fica igual);
5. monta um **painel no cofre** (`decisions/videos/...-pacote.md`) com um bloco por corte e 3 caixinhas:
   assisti inteiro · sem música da rádio do GTA · aprovado para postar.

**Nada é postado sozinho.** Quem aprova e posta é o José.

## Como usar
```
python cortes.py pacote live.mp4 --arroba @amigo
python cortes.py pacote live.mp4 --arroba @amigo --top 12
```

## Segurança
- Cada corte passa pelo **mesmo porteiro** do V5 (duração de 13 s a 3 min, dentro do vídeo, @ válido).
- @ inválido ou `--top` fora de 1–20 → recusa **antes** de qualquer corte.
- Um corte que falha **não derruba os outros**: o motivo aparece no painel e no terminal.
- Gemma fora do ar ou respondendo torto → o corte fica pronto, sem textos e sem gancho, com o aviso.
- Textos da Gemma já chegam conferidos pelo V6 (sem link, sem @, crédito posto pelo código).
  No painel, nome de arquivo, título e gancho são desarmados; a legenda vai em bloco de código sem crase.
- A nota do painel é criada com `"x"` (nunca sobrescreve); os vídeos usam o `-n` do FFmpeg.

## Arquivos
- `python/pacote.py` — `montar(...)` (a fila) e `painel_markdown(...)` (a nota).
- `python/cortes.py` — comando `pacote`.
- `python/tests/test_pacote.py` — 11 testes com robô, Gemma e gancho falsos.

## Evidências (12/11)
- **156 testes Python OK** (145 + 11).
- Ensaio real em `ensaio-gta.mp4`, `--top 3`: **3 de 3 cortes em 1 min** (corte + legenda + textos + gancho),
  painel no cofre com títulos, gancho, legenda com o crédito e hashtags.
- Conta para o dia 19: ~20 s por corte → 8 cortes em ~3 min, depois da caçada (~7 min para 24h).

## Limites conhecidos
- No painel, emoji composto (ex.: 🏃‍♂️) pode aparecer separado. É só na nota; o vídeo não muda.
- Rodar o pacote 2 vezes no mesmo vídeo gera os cortes de novo (com outro horário no nome). Não apaga nada.
