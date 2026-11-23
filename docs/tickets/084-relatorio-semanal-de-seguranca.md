# 084 — Relatório semanal de segurança
**Status:** ✅ construído e testado (15/11) · commit local · Seção 5, item 5

## Ideia
Uma nota por semana no cofre, juntando tudo que vigia o Jarvis. É o "relatório de SOC" do José: o que aconteceu,
o que ficou pendente e o que ele fez a respeito.

```bash
python python/relatorio_semanal.py   # → <JARVIS_PASTA_INCIDENTES>/AAAA-MM-DD-relatorio-seguranca.md
```

## O que entra
- **Ronda do defensor** (Ticket 044), agora com 2 vigias a mais:
  - Sentinela também lê o **alarme de tentativas** (Ticket 080);
  - vigia novo **Inicialização** (Ticket 083), só números.
- **Gitleaks no histórico do Git** (`gitleaks git <raiz> --redact`): algum segredo foi commitado?
- **A semana em números:** rondas, notas de incidente e duelos dos últimos 7 dias (pelo nome das notas).
- Lista **"O que eu fiz a respeito"** com uma caixinha para cada vigia em atenção/alerta + 2 perguntas de entrevista.

## Segurança 🔒
- Só lê. Nada é fechado, bloqueado ou apagado.
- Gitleaks com **comando fixo, sem shell** e `--redact`; o relatório leva só a **quantidade e o tipo** do achado
  (ex.: `2× generic-api-key`), nunca o valor, a linha ou o arquivo.
- O executável só é aceito se for um arquivo chamado `gitleaks(.exe)` (`JARVIS_GITLEAKS` ou `~/ferramentas/gitleaks/`).
- Nota com nome gerado pelo código, criada com `open(..., "x")`: **nunca sobrescreve** (2ª no mesmo dia vira `-2`).
- Texto vindo dos vigias é limpo de `|`, `` ` ``, `<`, `>` antes de entrar na tabela Markdown.
- Rodar toda semana: **o José decide** (lembrete manual ou tarefa agendada que ele mesmo crie). O Jarvis não cria tarefa
  no Windows (seria mudar configuração do sistema).

## Evidências (15/11)
- `test_relatorio_semanal.py`: **10 testes** (comando fixo/sem shell/`--redact`, só tipo do achado, gitleaks ausente,
  falha do gitleaks, nota com tabela/números/pendências, nunca sobrescreve, vigia quebrado não derruba).
- Rodada real só do gitleaks neste repositório: `ok — nenhum segredo no histórico do Git`.
- Python inteiro: ver commit. ⏳ José: rodar a 1ª vez (`python python/relatorio_semanal.py`) e ver a nota no cofre.
