"""
Jarvis 001 — Comando do robô de cortes 🎬 (Ticket 025, V5)

  python cortes.py cacar live.mp4
      → acha os momentos e grava a nota no cofre (decisions/videos)
  python cortes.py cortar live.mp4 3 7 12 --arroba @amigo
      → corta os momentos 3, 7 e 12 da última caçada
  python cortes.py cortar live.mp4 --trecho 1:02:10-1:02:40 --arroba @amigo
      → corte manual
  python cortes.py textos 2026-11-12-...-corte-01.mp4 --arroba @amigo
      → V6: a Gemma sugere 3 títulos, gancho, legenda e hashtags (nota no cofre)
  python cortes.py gancho 2026-11-12-...-corte-01.mp4 [--texto "MEU GANCHO"]
      → V6: vídeo novo com o gancho na tela nos 3 primeiros segundos
  python cortes.py pacote live.mp4 --arroba @amigo [--top 8]
      → Ticket 028: caça (se ainda não caçou), corta os mais fortes, pede os textos,
        grava o gancho e monta o PAINEL no cofre para você aprovar um por um
  python cortes.py autoteste
      → confere o robô inteiro numa pasta temporária (não precisa do .env)

O vídeo é só o NOME: ele precisa estar na pasta JARVIS_CORTES_ENTRADA (.env).
"""

import argparse
import hashlib
import json
import sys
from datetime import datetime
from pathlib import Path

import cacador
import cortador
from cacador import ErroCacador, hms, ler_hms
from cortador import ErroCorte


def _pastas():
    entrada = cortador.ler_pasta("JARVIS_CORTES_ENTRADA")
    saida = cortador.ler_pasta("JARVIS_CORTES_SAIDA")
    notas = cortador.ler_pasta("JARVIS_PASTA_DECISOES")
    falta = [n for n, p in (("JARVIS_CORTES_ENTRADA", entrada), ("JARVIS_CORTES_SAIDA", saida),
                            ("JARVIS_PASTA_DECISOES", notas)) if p is None]
    if falta:  # falha fechada: sem pasta configurada, não faz nada
        raise ErroCorte("Falta configurar no .env (pasta que exista): " + ", ".join(falta))
    if entrada == saida:
        raise ErroCorte("A pasta de saída não pode ser a mesma da entrada (o original fica intocado).")
    return entrada, saida, notas


def _arquivo_momentos(saida, nome_video):
    """Lista da última caçada. O nome vem de um hash: o nome do vídeo nunca vira caminho."""
    chave = hashlib.sha256(nome_video.encode("utf-8")).hexdigest()[:16]
    return saida / f".momentos-{chave}.json"


def cacar(nome_video):
    entrada, saida, notas = _pastas()
    video = cortador.resolver_entrada(entrada, nome_video)
    print("🔎 Ouvindo o vídeo inteiro (uma live de 24h demora alguns minutos)...")
    energias = cacador.energia_por_segundo(
        video, progresso=lambda s: print(f"   ... {hms(s)} lidos", flush=True))
    momentos = cacador.achar_picos(energias)
    nota = notas / f"{datetime.now():%Y-%m-%d-%H%M%S}-momentos.md"
    with open(nota, "x", encoding="utf-8") as f:  # "x" = só cria; nunca sobrescreve
        f.write(cacador.nota_markdown(video.name, momentos, len(energias)))
    lista = [m.__dict__ for m in momentos]
    _arquivo_momentos(saida, video.name).write_text(json.dumps({
        "video": video.name, "duracao": len(energias), "momentos": lista}), encoding="utf-8")
    print(f"✅ {len(momentos)} momento(s). Nota no cofre: {nota.name}")
    for m in momentos[:10]:
        print(f"   {m.numero:>2}. {hms(m.inicio)} → {hms(m.fim)}  (+{m.forca} dB)")
    return lista


def pacote(nome_video, arroba, top):
    """Ticket 028: a fila inteira num comando só. O painel vai para o cofre."""
    import pacote as p
    import textos as t
    entrada, saida, notas = _pastas()
    video = cortador.resolver_entrada(entrada, nome_video)
    if not cortador.ARROBA.fullmatch(arroba or ""):
        raise ErroCorte("O @ do amigo é obrigatório e só aceita letras, números, _ e ponto.")
    duracao = cortador.duracao_video(video)
    arquivo = _arquivo_momentos(saida, video.name)
    if arquivo.is_file():
        print("🔎 Usando a caçada que já existe desse vídeo.")
        try:
            momentos = json.loads(arquivo.read_text(encoding="utf-8"))["momentos"]
        except (ValueError, KeyError, TypeError) as e:
            raise ErroCorte("A lista da caçada está estragada. Rode: python cortes.py cacar <video>") from e
    else:
        momentos = cacar(nome_video)
    ffmpeg = cortador.achar_ffmpeg()
    print("👂 Ligando o Whisper para as legendas...")
    robo = cortador.Cortador(ffmpeg, cortador.transcritor_whisper())
    itens = p.montar(video, duracao, momentos, arroba, saida, robo, t.sugerir,
                     lambda corte, texto: cortador.gravar_gancho(ffmpeg, corte, texto),
                     top=top, avisar=lambda m: print(m, flush=True))
    nota = notas / f"{datetime.now():%Y-%m-%d-%H%M%S}-pacote.md"
    with open(nota, "x", encoding="utf-8") as f:
        f.write(p.painel_markdown(video.name, arroba, itens))
    prontos = sum(1 for i in itens if i.corte)
    print(f"📦 {prontos} de {len(itens)} cortes prontos. Painel no cofre: {nota.name}")
    for i in itens:
        if i.aviso:
            print(f"   ⚠️ Momento {i.numero}: {i.aviso}")
    print("⚠️ Nada foi postado. Abra o painel e aprove um por um.")
    return itens  # Ticket 045: o super produtor continua daqui (ex.: legenda traduzida)


def _trechos(saida, video, numeros, manuais):
    trechos = []
    if numeros:
        arquivo = _arquivo_momentos(saida, video.name)
        if not arquivo.is_file():
            raise ErroCorte("Não achei a caçada desse vídeo. Rode antes: python cortes.py cacar <video>")
        lista = {int(m["numero"]): m for m in json.loads(arquivo.read_text(encoding="utf-8"))["momentos"]}
        for n in numeros:
            if n not in lista:
                raise ErroCorte(f"O momento {n} não está na lista da caçada.")
            trechos.append((n, int(lista[n]["inicio"]), int(lista[n]["fim"])))
    for i, texto in enumerate(manuais or [], start=1):
        try:
            a, b = texto.split("-", 1)
            trechos.append((100 + i, ler_hms(a), ler_hms(b)))
        except ValueError as e:
            raise ErroCorte("Trecho inválido: use 1:02:10-1:02:40") from e
    if not trechos:
        raise ErroCorte("Diga quais momentos cortar (números) ou um --trecho.")
    if len(trechos) > cortador.MAX_CORTES:
        raise ErroCorte(f"No máximo {cortador.MAX_CORTES} cortes por pedido.")
    return trechos


def cortar(nome_video, numeros, manuais, arroba):
    entrada, saida, _ = _pastas()
    video = cortador.resolver_entrada(entrada, nome_video)
    duracao = cortador.duracao_video(video)
    trechos = _trechos(saida, video, numeros, manuais)
    # O porteiro confere TUDO antes de começar: pedido ruim não deixa corte pela metade
    for n, ini, fim in trechos:
        ok, motivo = cortador.liberar(ini, fim, duracao, arroba)
        if not ok:
            raise ErroCorte(f"Porteiro barrou o corte {n}: {motivo}.")
    ffmpeg = cortador.achar_ffmpeg()
    print("👂 Ligando o Whisper para as legendas...")
    robo = cortador.Cortador(ffmpeg, cortador.transcritor_whisper())
    for n, ini, fim in trechos:
        destino = cortador.nome_saida(saida, n)
        print(f"✂️  Corte {n}: {hms(ini)} → {hms(fim)} ...", flush=True)
        robo.cortar(video, ini, fim, duracao, arroba, destino)
        print(f"   ✅ {destino.name}")
    print("⚠️ Antes de postar: confira se tem música da rádio do GTA no corte.")


def _textos_json(corte):
    return corte.with_name(f"{corte.stem}.textos.json")


def textos(nome_corte, arroba):
    """V6: a Gemma sugere título, gancho, legenda e hashtags a partir do .srt do corte."""
    import textos as t
    if not cortador.ARROBA.fullmatch(arroba or ""):
        raise ErroCorte("O @ do amigo é obrigatório e só aceita letras, números, _ e ponto.")
    _, saida, notas = _pastas()
    corte = cortador.resolver_entrada(saida, nome_corte)
    srt = corte.with_suffix(".srt")
    if not srt.is_file():
        raise ErroCorte("Esse corte não tem legenda (.srt). Corte de novo com a versão atual.")
    print("✍️ Pedindo os textos para a Gemma (local)...")
    try:
        s = t.sugerir(srt.read_text(encoding="utf-8"), arroba)
    except t.ErroTextos as e:
        raise ErroCorte(str(e)) from e
    _textos_json(corte).write_text(json.dumps(s, ensure_ascii=False), encoding="utf-8")
    nota = notas / f"{datetime.now():%Y-%m-%d-%H%M%S}-textos.md"
    with open(nota, "x", encoding="utf-8") as f:
        f.write(t.nota_markdown(corte.name, s))
    print(f"✅ Nota no cofre: {nota.name}")
    for i, titulo in enumerate(s["titulos"], start=1):
        print(f"   {i}. {titulo}")
    print(f"   Gancho: {s['gancho']}")


def gancho(nome_corte, texto=None):
    """V6: grava o gancho nos 3 primeiros segundos, num vídeo NOVO (o corte fica igual)."""
    import textos as t
    _, saida, _ = _pastas()
    corte = cortador.resolver_entrada(saida, nome_corte)
    if texto is None:
        arquivo = _textos_json(corte)
        if not arquivo.is_file():
            raise ErroCorte("Rode antes: python cortes.py textos <corte> --arroba @amigo (ou use --texto).")
        texto = json.loads(arquivo.read_text(encoding="utf-8"))["gancho"]
    try:
        texto = t._limpo(texto, 40, "gancho")
    except t.ErroTextos as e:
        raise ErroCorte(str(e)) from e
    novo = cortador.gravar_gancho(cortador.achar_ffmpeg(), corte, texto)
    print(f"✅ {novo.name} (gancho: {texto})")


def main(argv=None):
    # Terminal do Windows nem sempre é UTF-8: emoji e acento não podem derrubar o robô
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(prog="cortes.py", description="Robô de cortes do Jarvis (Ticket 025)")
    sub = p.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("cacar", help="acha os momentos fortes")
    c.add_argument("video")
    k = sub.add_parser("cortar", help="corta os momentos escolhidos")
    k.add_argument("video")
    k.add_argument("numeros", nargs="*", type=int)
    k.add_argument("--trecho", action="append", help="corte manual: 1:02:10-1:02:40")
    k.add_argument("--arroba", required=True, help="@ do amigo (crédito em todo corte)")
    sub.add_parser("autoteste", help="confere o robô inteiro numa pasta temporária (sem .env)")
    pc = sub.add_parser("pacote", help="Ticket 028: caça, corta, textos, gancho e painel no cofre")
    pc.add_argument("video")
    pc.add_argument("--arroba", required=True, help="@ do amigo (crédito em todo corte)")
    pc.add_argument("--top", type=int, default=8, help="quantos momentos cortar (1 a 20; padrão 8)")
    x = sub.add_parser("textos", help="V6: a Gemma sugere título, gancho, legenda e hashtags")
    x.add_argument("corte", help="nome do corte na pasta de saída")
    x.add_argument("--arroba", required=True)
    g = sub.add_parser("gancho", help="V6: grava o gancho no 1º segundo (vídeo novo)")
    g.add_argument("corte")
    g.add_argument("--texto", help="gancho seu, em vez do da Gemma (até 40 letras)")
    a = p.parse_args(argv)
    try:
        if a.cmd == "autoteste":
            import autoteste_cortes
            return 0 if autoteste_cortes.rodar() else 1
        if a.cmd == "cacar":
            cacar(a.video)
        elif a.cmd == "textos":
            textos(a.corte, a.arroba)
        elif a.cmd == "gancho":
            gancho(a.corte, a.texto)
        elif a.cmd == "pacote":
            pacote(a.video, a.arroba, a.top)
        else:
            cortar(a.video, a.numeros, a.trecho, a.arroba)
    except (ErroCorte, ErroCacador, FileExistsError) as e:
        print(f"❌ {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
