"""
Jarvis 001 — Comando do robô de cortes 🎬 (Ticket 025, V5)

  python cortes.py cacar live.mp4
      → acha os momentos e grava a nota no cofre (decisions/videos)
  python cortes.py cortar live.mp4 3 7 12 --arroba @amigo
      → corta os momentos 3, 7 e 12 da última caçada
  python cortes.py cortar live.mp4 --trecho 1:02:10-1:02:40 --arroba @amigo
      → corte manual
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
    _arquivo_momentos(saida, video.name).write_text(json.dumps({
        "video": video.name, "duracao": len(energias),
        "momentos": [m.__dict__ for m in momentos]}), encoding="utf-8")
    print(f"✅ {len(momentos)} momento(s). Nota no cofre: {nota.name}")
    for m in momentos[:10]:
        print(f"   {m.numero:>2}. {hms(m.inicio)} → {hms(m.fim)}  (+{m.forca} dB)")


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


def main(argv=None):
    # Terminal do Windows nem sempre é UTF-8: emoji e acento não podem derrubar o robô
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    p =argparse.ArgumentParser(prog="cortes.py", description="Robô de cortes do Jarvis (Ticket 025)")
    sub = p.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("cacar", help="acha os momentos fortes")
    c.add_argument("video")
    k = sub.add_parser("cortar", help="corta os momentos escolhidos")
    k.add_argument("video")
    k.add_argument("numeros", nargs="*", type=int)
    k.add_argument("--trecho", action="append", help="corte manual: 1:02:10-1:02:40")
    k.add_argument("--arroba", required=True, help="@ do amigo (crédito em todo corte)")
    sub.add_parser("autoteste", help="confere o robô inteiro numa pasta temporária (sem .env)")
    a = p.parse_args(argv)
    try:
        if a.cmd == "autoteste":
            import autoteste_cortes
            return 0 if autoteste_cortes.rodar() else 1
        if a.cmd == "cacar":
            cacar(a.video)
        else:
            cortar(a.video, a.numeros, a.trecho, a.arroba)
    except (ErroCorte, ErroCacador, FileExistsError) as e:
        print(f"❌ {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
