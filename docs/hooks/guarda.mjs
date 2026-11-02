// guarda.mjs — Hook PreToolUse do Jarvis 001
// Escrito pela 001 (02/11/2026), revisado e instalado pelo José.
// Olha TODO comando antes de rodar. Se for perigoso, sai com código 2 = BLOQUEADO.
import { readFileSync } from 'node:fs';

// Lista de comandos proibidos (padrão + motivo)
const REGRAS = [
  { padrao: /\.env\b/i,                          motivo: 'acesso ao .env (senhas)' },
  { padrao: /\bgit\s+add\s+(-A|--all|\.)(\s|$)/, motivo: 'git add . ou -A (adicione arquivo por arquivo)' },
  { padrao: /\bgit\s+push\b.*\s(--force|-f)\b/,  motivo: 'git push --force (apaga histórico)' },
  { padrao: /\brm\s+-[a-z]*(rf|fr)/i,            motivo: 'rm -rf (apaga tudo sem perguntar)' },
  { padrao: /Remove-Item\b.*-Recurse/i,          motivo: 'Remove-Item -Recurse (rm -rf do PowerShell)' },
];

// O Claude Code manda o comando em JSON pela "entrada padrão" (stdin)
let comando = '';
try {
  const entrada = JSON.parse(readFileSync(0, 'utf8'));
  comando = String(entrada?.tool_input?.command ?? '');
} catch {
  process.exit(0); // entrada estranha: não bloqueia; o modo manual continua decidindo
}

for (const { padrao, motivo } of REGRAS) {
  if (padrao.test(comando)) {
    console.error(`🔒 Bloqueado pela guarda do Jarvis: ${motivo}. Peça ao José.`);
    process.exit(2); // código 2 = o Claude Code NÃO roda o comando
  }
}
process.exit(0); // código 0 = liberado (o modo manual ainda pergunta)
