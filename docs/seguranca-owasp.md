# Jarvis 001 × OWASP Top 10 (2025)

Revisão feita em 2026-11-09 por José + 001. Cada item diz **o que é o ataque**, **como o Jarvis se defende** (com o arquivo) e **o que ainda falta**.

**OWASP Top 10** em 1 linha: a lista dos 10 riscos mais comuns em aplicações web, mantida pela OWASP (fundação aberta de segurança).

| # | Risco | Como o Jarvis se defende | Nota |
|---|---|---|---|
| A01 | **Controle de acesso quebrado** (alguém faz o que não devia) | Token fechado por padrão: sem token configurado, recusa tudo · escrita pela Central exige crachá + `Origin` + Confirmar (Ticket 020) · só 2 rotas públicas, comparadas por igualdade exata (`TokenInterceptor`) · ações só por allowlist · `PastaSegura` impede sair da pasta permitida (Path Traversal) | ✅ |
| A02 | **Configuração insegura** | API e Ollama só em `127.0.0.1` (testado pela rede: recusado) · `HostInterceptor` bloqueia DNS rebinding · sem Actuator · erro inesperado responde só "erro interno" (`ErrosHandler`) · cabeçalhos de segurança em toda resposta: `X-Frame-Options`, `frame-ancestors`, `nosniff`, `no-store` (`CabecalhosSegurancaFilter`, Ticket 019) | ✅ |
| A03 | **Cadeia de suprimentos** (biblioteca ou ferramenta contaminada) | Só instalo de fonte oficial (dono, estrelas, licença) · Dependabot no Maven e no GitHub Actions · versões Python fixas (`==`) · checksum conferido no gitleaks | ✅ Dependabot também no Python e gitleaks a cada push (Ticket 010) · 🟢 actions presas por tag, não por SHA |
| A04 | **Falhas de criptografia** | HMAC-SHA256 com subchave derivada · comparação em tempo constante (`MessageDigest.isEqual` / `hmac.compare_digest`) · nonce de `secrets` · o token nunca viaja (Ticket 007) | ✅ |
| A05 | **Injeção** | Git e netstat sem shell e com argumentos fixos (`GitStatusService`, `PortasService`) · `LogSeguro` limpa quebra de linha no log · a IA não decide ação: quem decide é `intencoes.py`, por regras fixas (prompt injection) · mensagem colada no detector de golpe nunca vira ação nem volta na resposta (Ticket 023) | ✅ |
| A06 | **Design inseguro** | Cada ticket tem modelo de ameaça + revisão do `jarvis-hacker` · "bom dia" só lê · escrita vinda da voz pede "sim" · Python só **pede**, Java executa | ✅ |
| A07 | **Falhas de autenticação** | Pedido assinado com hora (±60 s) e nonce de uso único: replay recusado · aperto de mão contra API impostora (Ticket 006) | 🟡 o atalho `X-Jarvis-Token` (para `curl`) segue aberto; cada uso vai para o log |
| A08 | **Integridade de software e dados** | Assinatura cobre método, rota, hora, nonce **e o corpo** (mudou 1 byte → recusado) · vigia de integridade: SHA-256 das travas do projeto (`IntegridadeService`, Ticket 022) · CodeQL em todo push · gitleaks: 42 commits, 0 segredos | ✅ |
| A09 | **Log e alerta** | Todo pedido e toda recusa vão para o log, já limpos (`LogSeguro`) · alarme de impostor falado | ✅ log em arquivo com 14 dias de histórico (Ticket 010) · 🟢 sem alerta automático |
| A10 | **Condições excepcionais** (o sistema se perde num erro) | Falha **fechada** em tudo: sem token, recusa; sem corpo guardado, 401 · limites: corpo de 64 KB (413), git com 5 s e 64 KB, 300 provas por segundo (503), detector de golpe com 4000 caracteres e 10 links, testado contra ReDoS · cada braço do "bom dia" falha sozinho (inclusive a Sentinela, Ticket 024) | ✅ |

## O que falta (tickets futuros)
1. ~~Dependabot para `pip`~~ ✅ Ticket 010
2. ~~gitleaks no GitHub Actions~~ ✅ Ticket 010
3. ~~Log em arquivo com rotação~~ ✅ Ticket 010
4. 🟢 Prender as actions por SHA (risco baixo: são actions oficiais do GitHub).
5. 🟢 Desligar o atalho `X-Jarvis-Token` quando o `curl` não for mais preciso.

## Provas
- **158** testes Java + **60** Python, rodando no GitHub Actions a cada push.
- Teste real de ponta a ponta: pedido assinado, impostor barrado, 0 envios do token.
- gitleaks v8.30.1: 42 commits, 0 vazamentos.
- Portas: 8080 e 11434 recusadas pelo IP de rede.
