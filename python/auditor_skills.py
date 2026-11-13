"""
Jarvis 001 — Auditor de skills 🔎 (Ticket 037)

Lê um SKILL.md ou um script de terceiros ANTES de instalar e aponta padrões suspeitos,
com a técnica do MITRE ATT&CK de cada um. Muita skill pronta pede acesso total ao terminal:
é assim que entra comando escondido, roubo do .env e conexão para fora do PC.

  python auditor_skills.py caminho/da/SKILL.md
  python auditor_skills.py pasta/da/skill          (olha .md, .py, .sh, .ps1, .js, .json, .yaml, .toml, .bat)

Regras:
- Só LÊ texto. Nunca executa, importa nem baixa nada do arquivo auditado.
- Regras FIXAS (sem IA): a skill não consegue "convencer" o auditor.
- O resultado é um JSON com textos fixos daqui + o número da linha. O trecho suspeito NÃO é copiado
  (assim o próprio relatório não carrega o veneno).
- "Sem achado" NÃO é "seguro": é só o primeiro filtro. Quem decide instalar é o José.
"""

import json
import re
import sys
from pathlib import Path

LIMITE_ARQUIVO = 512 * 1024
MAXIMO_ARQUIVOS = 200
MAXIMO_ACHADOS = 100
EXTENSOES = {".md", ".py", ".sh", ".ps1", ".js", ".ts", ".json", ".yaml", ".yml", ".toml", ".bat", ".cmd", ".lua"}


def _regra(codigo, tatica, tecnica, pontos, explicacao, padrao):
    return {"codigo": codigo, "tatica": tatica, "tecnica": tecnica, "pontos": pontos,
            "explicacao": explicacao, "padrao": re.compile(padrao, re.IGNORECASE)}


REGRAS = [
    _regra("baixa-e-executa", "Execução", "T1059", 4, "Baixa algo da internet e executa direto.",
           r"(curl|wget|iwr|invoke-webrequest)\b[^\n|]{0,200}\|\s*(sh|bash|zsh|python\d?|iex|powershell)\b"),
    _regra("comando-escondido", "Execução", "T1059", 3, "Executa texto como código (eval, exec, Invoke-Expression).",
           r"\b(eval|exec)\s*\(|invoke-expression|\biex\s*\(|powershell[^\n]{0,60}-enc(odedcommand)?\b"),
    _regra("shell-solto", "Execução", "T1059", 2, "Roda comando pelo shell (os.system, shell=True, child_process).",
           r"os\.system\s*\(|shell\s*=\s*true|child_process|subprocess\.getoutput|popen\s*\("),
    _regra("apaga-tudo", "Impacto", "T1485", 4, "Apaga pasta inteira ou formata.",
           r"rm\s+-[a-z]*r[a-z]*f|remove-item[^\n]{0,80}-recurse[^\n]{0,40}-force|\bformat\s+[a-z]:|del\s+/[sq]\b"),
    _regra("persistencia", "Persistência", "T1547", 3, "Tenta ficar no PC depois de fechar (tarefa agendada, inicialização).",
           r"\bschtasks\b|\bcrontab\b|currentversion\\\\?run\b|\\startup\\|\.bashrc|\.zshrc|\$profile\b|launchagents"),
    _regra("le-segredos", "Acesso a credenciais", "T1552", 3, "Mexe em arquivo de segredo (.env, chave SSH, senha do navegador).",
           r"(^|[\s\"'/\\(=])\.env\b|id_rsa|id_ed25519|\.ssh[/\\]|\.aws[/\\]credentials|login data|cookies\.sqlite|\.git-credentials"),
    _regra("variaveis-de-ambiente", "Acesso a credenciais", "T1552", 2, "Lê todas as variáveis de ambiente (onde ficam os tokens).",
           r"os\.environ\b(?!\.get\(\s*[\"'](path|home|temp|tmp)[\"'])|process\.env\b|get-childitem\s+env:|\bprintenv\b"),
    _regra("manda-para-fora", "Exfiltração", "T1041", 3, "Envia dados para fora (POST, webhook, site de colar texto).",
           r"(curl|wget)\b[^\n]{0,120}(-d\b|--data|-F\b|--upload-file|-T\b)|requests\.post\s*\(|discord(app)?\.com/api/webhooks"
           r"|pastebin\.com|transfer\.sh|webhook\.site|ngrok"),
    _regra("codigo-disfarcado", "Evasão de defesa", "T1027", 2, "Texto embaralhado (base64 grande ou decodificação).",
           r"[A-Za-z0-9+/]{200,}={0,2}|base64\s+(-d|--decode)|frombase64string|atob\s*\("),
    _regra("desliga-protecao", "Evasão de defesa", "T1562", 4, "Desliga proteção (antivírus, verificação, hooks do Git).",
           r"set-mppreference|disablerealtimemonitoring|--no-verify\b|dangerously|executionpolicy\s+bypass|sudo\s+-s\b"),
    _regra("manda-na-ia", "Injeção de instrução", "ATLAS AML.T0051", 4, "Tenta mandar na IA ou esconder algo do usuário.",
           r"ignor[ea]\s+(all\s+|any\s+|todas\s+)?(as\s+)?(previous|prior|instru)|do\s+not\s+(tell|inform|mention)[^\n]{0,30}user"
           r"|n[aã]o\s+(avise|conte|mostre)[^\n]{0,30}usu[aá]rio|without\s+(asking|confirmation)|sem\s+(pedir|perguntar)"),
    _regra("permissao-total", "Escalada de privilégio", "T1548", 3, "Pede acesso total às ferramentas (terminal liberado para tudo).",
           r"allowed-tools\s*:\s*[\"']?\*|bash\(\s*\*\s*\)|\"permissions\"\s*:\s*\"?all|bypasspermissions"),
]

# Endereço de rede que não é o próprio PC
_URL = re.compile(r"https?://([a-z0-9.-]+)", re.IGNORECASE)
_LOCAL = ("127.0.0.1", "localhost")
REGRA_REDE = {"codigo": "rede-externa", "tatica": "Comando e controle", "tecnica": "T1071", "pontos": 1,
              "explicacao": "Fala com endereço fora do PC. Confira se é um site oficial e necessário."}


def auditar_texto(texto, nome="texto"):
    """Achados de um texto. Cada regra conta pontos UMA vez; os achados guardam só a linha, nunca o trecho."""
    achados, pontos, contadas = [], 0, set()
    for numero, linha in enumerate(str(texto).splitlines(), start=1):
        if len(achados) >= MAXIMO_ACHADOS:
            break
        linha = linha[:2000]  # linha gigante não trava o regex
        regras = [r for r in REGRAS if r["padrao"].search(linha)]
        if any(host.lower() not in _LOCAL for host in _URL.findall(linha)):
            regras.append(REGRA_REDE)
        for r in regras:
            achados.append({"arquivo": nome, "linha": numero, "regra": r["codigo"], "tatica": r["tatica"],
                            "tecnica": r["tecnica"], "explicacao": r["explicacao"]})
            if r["codigo"] not in contadas:
                contadas.add(r["codigo"])
                pontos += r["pontos"]
    return achados, pontos


def nivel(pontos):
    return "alto" if pontos >= 6 else "suspeito" if pontos >= 3 else "baixo"


def auditar(caminho):
    """Audita um arquivo ou uma pasta. Devolve o relatório (dict pronto para virar JSON)."""
    alvo = Path(caminho)
    if alvo.is_symlink() or not alvo.exists():
        raise ValueError("Não achei esse arquivo ou pasta (atalho não vale).")
    arquivos = [alvo] if alvo.is_file() else sorted(
        p for p in alvo.rglob("*") if p.is_file() and not p.is_symlink() and p.suffix.lower() in EXTENSOES)
    achados, pontos, lidos, pulados = [], 0, 0, 0
    for arquivo in arquivos[:MAXIMO_ARQUIVOS]:
        try:
            if arquivo.stat().st_size > LIMITE_ARQUIVO:
                pulados += 1
                continue
            texto = arquivo.read_text(encoding="utf-8", errors="replace")
        except OSError:
            pulados += 1
            continue
        lidos += 1
        nome = arquivo.name if alvo.is_file() else arquivo.relative_to(alvo).as_posix()
        novos, soma = auditar_texto(texto, _limpo(nome))
        achados += novos
        pontos += soma
    pulados += max(0, len(arquivos) - MAXIMO_ARQUIVOS)
    return {
        "alvo": _limpo(alvo.name), "nivel": nivel(pontos), "pontos": pontos,
        "arquivos_lidos": lidos, "arquivos_pulados": pulados,
        "achados": achados[:MAXIMO_ACHADOS],
        "conselho": conselho(nivel(pontos), pulados),
    }


def _limpo(nome):
    """Nome de arquivo no relatório: só letras, números e sinais simples (nunca marcação)."""
    return re.sub(r"[^A-Za-z0-9._/ -]", "_", str(nome))[:120]


def conselho(n, pulados=0):
    base = {
        "alto": "Não instale. Tem padrão de ataque: mostre o relatório para a 001 ou a 002 antes de qualquer coisa.",
        "suspeito": "Não instale ainda. Abra as linhas apontadas e entenda cada uma antes de decidir.",
        "baixo": "Não vi padrão de ataque. Isso é só o primeiro filtro: leia o arquivo antes de instalar.",
    }[n]
    if pulados:
        base += f" Atenção: {pulados} arquivo(s) não foram lidos (grandes demais ou ilegíveis)."
    return base


def main(argv=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 1:
        print("Uso: python auditor_skills.py <arquivo ou pasta da skill>")
        return 2
    try:
        relatorio = auditar(argv[0])
    except ValueError as e:
        print(f"❌ {e}")
        return 2
    print(json.dumps(relatorio, ensure_ascii=False, indent=1))
    return {"baixo": 0, "suspeito": 1, "alto": 1}[relatorio["nivel"]]


if __name__ == "__main__":
    sys.exit(main())
