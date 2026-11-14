"""
Jarvis 001 — Conector "Estúdio 001" (MCP local) 🔌 (Ticket 051)

Deixa o Claude chamar o Estúdio do Brabo (brabo.py) direto, sem digitar comando:
  listar_cortes · ler_roteiro · salvar_roteiro · rascunho_gta · fazer_short · amostra

Regras:
- Roda pelo stdio (o Claude abre o processo). Nenhuma porta de rede é aberta.
- Só mexe na pasta dos cortes (JARVIS_CORTES_SAIDA) e só com NOME de arquivo, sem caminho.
- Nunca sobrescreve nada. Roteiro passa pela mesma conferência do brabo.py.
- NÃO existe ferramenta de postar: quem posta é o José.

Ligar no Claude Code: o arquivo .mcp.json da raiz do jarvis-001 já aponta para cá.
Testar sozinho: .venv/Scripts/python estudio_mcp.py --teste
"""

import json
import sys
from pathlib import Path

import numpy as np

import avatar
import brabo
import cortador
from brabo import ErroBrabo
from cortador import ErroCorte

SUFIXOS_ROTEIRO = (".brabo.json", ".mestre.json")
MAX_LISTA = 200


def _pasta():
    return Path(brabo._pasta())


def _nome_simples(nome, sufixos):
    nome = str(nome or "")
    if (not nome.endswith(sufixos) or len(nome) > 120 or nome.startswith(".")
            or any(c in nome for c in '/\\:*?"<>|') or any(not c.isprintable() for c in nome)):
        raise ErroBrabo(f"Passe só o NOME do arquivo ({' ou '.join(sufixos)}), sem caminho.")
    return nome


def nome_do_short(nome_roteiro, modo):
    base = nome_roteiro[:-len(".json")].removesuffix(".brabo").removesuffix(".mestre")
    return f"{base}-{'brabo' if modo == 'gta' else 'mestre'}.mp4"


# ---------------- ferramentas (funções puras, testáveis sem MCP) ----------------

def listar_cortes(pasta):
    """Nomes dos cortes (.mp4), roteiros (.json) e Shorts prontos na pasta."""
    arquivos = sorted(p.name for p in Path(pasta).iterdir() if p.is_file() and not p.is_symlink())
    prontos = [n for n in arquivos if n.endswith(("-brabo.mp4", "-mestre.mp4"))]
    return {
        "cortes": [n for n in arquivos if n.endswith(".mp4") and n not in prontos][:MAX_LISTA],
        "roteiros": [n for n in arquivos if n.endswith(SUFIXOS_ROTEIRO)][:MAX_LISTA],
        "shorts_prontos": prontos[:MAX_LISTA],
    }


def ler_roteiro(pasta, nome):
    arq = brabo._arquivo(pasta, _nome_simples(nome, SUFIXOS_ROTEIRO), ".json")
    try:
        return json.loads(arq.read_text(encoding="utf-8"))
    except ValueError as e:
        raise ErroBrabo("O roteiro não é um JSON válido.") from e


def salvar_roteiro(pasta, nome, roteiro):
    """Confere o roteiro e grava (nunca sobrescreve). O sufixo tem que bater com o modo."""
    nome = _nome_simples(nome, SUFIXOS_ROTEIRO)
    conferido = brabo.validar_roteiro(roteiro)
    esperado = ".brabo.json" if conferido["modo"] == "gta" else ".mestre.json"
    if not nome.endswith(esperado):
        raise ErroBrabo(f"Roteiro do modo '{conferido['modo']}' tem que terminar em {esperado}.")
    destino = Path(pasta) / nome
    try:
        with open(destino, "x", encoding="utf-8", newline="\n") as f:
            json.dump(roteiro, f, ensure_ascii=False, indent=2)
    except FileExistsError as e:
        raise ErroBrabo(f"{nome} já existe. Use outro nome.") from e
    return {"salvo": nome, "falas": len(conferido["falas"]), "modo": conferido["modo"]}


def rascunho_gta(pasta, corte, perguntar=None):
    """A Gemma (local) escreve um roteiro do Brabo a partir da legenda do corte."""
    import textos
    caminho = cortador.resolver_entrada(pasta, _nome_simples(corte, (".mp4",)))
    srt = caminho.with_suffix(".srt")
    if not srt.is_file() or srt.is_symlink():
        raise ErroBrabo("Esse corte não tem legenda (.srt).")
    _, dado = brabo.rascunho(srt.read_text(encoding="utf-8", errors="replace"), caminho.name,
                             perguntar or textos.perguntar_gemma)
    return dado


def fazer_short(pasta, roteiro_nome, fazer=brabo.fazer):
    roteiro = brabo.validar_roteiro(ler_roteiro(pasta, roteiro_nome))
    saida = fazer(roteiro, pasta, nome_do_short(roteiro_nome, roteiro["modo"]))
    return {
        "video": saida.name,
        "post": saida.with_suffix(".post.txt").name,
        "lembrete": "Nada foi postado. O José assiste inteiro e posta marcando "
                    "'conteúdo alterado ou sintético: SIM'.",
    }


def amostra(pasta, personagem):
    if personagem not in avatar.PERSONAGENS:
        raise ErroBrabo(f"Personagem tem que ser: {', '.join(avatar.PERSONAGENS)}.")
    destino = Path(pasta) / f"{personagem}-amostra.png"
    if destino.exists():
        return {"imagem": destino.name, "nova": False}
    img = np.concatenate([avatar.sprites(personagem)[(b, False)] for b in (0, 1, 2)], axis=1)
    brabo.gravar_png(cortador.achar_ffmpeg(), img, destino, pasta)
    return {"imagem": destino.name, "nova": True}


# ---------------- servidor MCP ----------------

def criar_servidor():
    from mcp.server.mcpserver import MCPServer
    from mcp.server.mcpserver.exceptions import ToolError

    srv = MCPServer(
        name="estudio-001",
        title="Estúdio 001",
        instructions=(
            "Estúdio do Brabo do Jarvis (local). Canal GTA = modo 'gta' (o Brabo); canal de casa = "
            "modo 'ensino' (Mestre Parafuso). Fluxo: listar_cortes → escrever o roteiro → "
            "salvar_roteiro → fazer_short. Nada é postado: quem posta é o José."
        ),
    )

    def protegido(func, *args):
        try:
            return func(_pasta(), *args)
        except (ErroBrabo, ErroCorte) as e:
            raise ToolError(str(e)) from e

    @srv.tool(description="Lista cortes (.mp4), roteiros (.brabo.json/.mestre.json) e Shorts prontos.")
    def listar() -> dict:
        return protegido(listar_cortes)

    @srv.tool(description="Lê um roteiro da pasta dos cortes (só o nome do arquivo).")
    def ler(nome: str) -> dict:
        return protegido(ler_roteiro, nome)

    @srv.tool(description=(
        "Confere e salva um roteiro novo (nunca sobrescreve). nome: <base>.brabo.json (modo gta) ou "
        "<base>.mestre.json (modo ensino). roteiro: {modo, fundo: corte|cartoes, corte?, titulo, "
        "nota?, credito?, som_jogo?, inicio?, falas: [{em, texto, tela?}]}."))
    def salvar(nome: str, roteiro: dict) -> dict:
        return protegido(salvar_roteiro, nome, roteiro)

    @srv.tool(description="A Gemma local escreve um rascunho do Brabo para um corte (.mp4). Revise antes de salvar.")
    def rascunho(corte: str) -> dict:
        return protegido(rascunho_gta, corte)

    @srv.tool(description="Faz o Short 9:16 a partir de um roteiro salvo (leva ~30 s). Não posta.")
    def fazer(roteiro: str) -> dict:
        return protegido(fazer_short, roteiro)

    @srv.tool(description="Grava um PNG com a cara do personagem (brabo ou mestre).")
    def ver_personagem(personagem: str) -> dict:
        return protegido(amostra, personagem)

    return srv


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    if argv == ["--teste"]:
        print(json.dumps(listar_cortes(_pasta()), ensure_ascii=False, indent=2))
        return 0
    criar_servidor().run("stdio")
    return 0


if __name__ == "__main__":
    sys.exit(main())
