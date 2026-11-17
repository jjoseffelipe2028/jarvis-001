"""
Jarvis 001 — Cortes da live AO VIVO 📡 (Ticket 094)

  python ao_vivo.py <url-da-live> --arroba @amigo [--falas]
      → grava a live em blocos de 60 s e vai cortando enquanto ela acontece
  python ao_vivo.py --pasta <pasta-com-blocos> --arroba @amigo [--falas]
      → modo teste: usa blocos já gravados (bloco_0000.ts, bloco_0001.ts...), sem internet

Como funciona:
  1. streamlink (pega a live) → ffmpeg (fatia em blocos de 60 s, sem reprocessar o vídeo).
  2. O vigia só mexe no bloco FECHADO: o bloco N está pronto quando aparece o N+1.
  3. Janela de 2 blocos (o anterior + o novo): um momento na virada do minuto não é cortado no meio.
  4. Caçada (volume, e fala com --falas) só nessa janela → corte 9:16 com legenda (o de sempre).
  5. Pico já cortado (menos de 45 s de distância) não vira corte de novo.
  Nada é postado. Os cortes vão para JARVIS_CORTES_SAIDA/ao-vivo e o resumo vai para o cofre.
"""

import argparse
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

import cacador
import cortador
from cacador import ErroCacador, hms
from cortador import ErroCorte

SEGUNDOS_BLOCO = 60
MANTER_BLOCOS = 30          # ~30 min de live no disco; os mais velhos são apagados
MAX_POR_JANELA = 2          # no máximo 2 cortes por minuto novo de live
ESPERA = 5                  # o vigia olha a pasta a cada 5 s
SITES = ("youtube.com", "youtu.be", "twitch.tv", "kick.com")
NOME_BLOCO = re.compile(r"bloco_(\d{4,})\.ts")


# ---------- regras puras (testadas sem vídeo) ----------

def validar_url(url):
    """Só https de YouTube, Twitch ou Kick. Qualquer outra coisa → ErroCorte."""
    try:
        u = urlparse(str(url or "").strip())
    except ValueError as e:
        raise ErroCorte("Link inválido.") from e
    host = (u.hostname or "").lower()
    if u.scheme != "https" or not any(host == s or host.endswith("." + s) for s in SITES):
        raise ErroCorte("Só aceito link https de YouTube, Twitch ou Kick.")
    return u.geturl()


def blocos_fechados(pasta, vistos, final=False):
    """Blocos prontos e ainda não vistos, em ordem. O último fica de fora (o ffmpeg ainda escreve nele),
    a não ser no `final` (gravação acabou)."""
    blocos = sorted((p for p in Path(pasta).iterdir() if NOME_BLOCO.fullmatch(p.name)),
                    key=lambda p: int(NOME_BLOCO.fullmatch(p.name).group(1)))
    prontos = blocos if final else blocos[:-1]
    return [b for b in prontos if b.name not in vistos]


def ja_cortado(pico_absoluto, cortados, distancia=cacador.DISTANCIA_MIN):
    return any(abs(pico_absoluto - c) < distancia for c in cortados)


def limpar(pasta, manter=MANTER_BLOCOS):
    """Apaga os blocos mais velhos e deixa só os `manter` mais novos. Devolve quantos apagou."""
    blocos = blocos_fechados(pasta, set(), final=True)
    velhos = blocos[:-manter] if len(blocos) > manter else []
    for b in velhos:
        b.unlink(missing_ok=True)
    return len(velhos)


class Vigia:
    """Olha a pasta de blocos e corta o que for forte. Tudo que mexe em vídeo vem de fora
    (`medir`, `juntar`, `cacar`, `cortar`), para os testes rodarem sem FFmpeg nem Whisper."""

    def __init__(self, pasta, medir, juntar, cacar, cortar, avisar=print, maximo=MAX_POR_JANELA):
        self.pasta = Path(pasta)
        self.janelas = self.pasta / "janelas"
        self._medir, self._juntar, self._cacar, self._cortar = medir, juntar, cacar, cortar
        self._avisar, self._maximo = avisar, maximo
        self.vistos, self.cortados, self.feitos = set(), [], []
        self._anterior = None       # (bloco, início absoluto em s)
        self._relogio = 0           # segundos de live já vistos

    def passo(self, final=False):
        """Processa todos os blocos fechados que chegaram. Devolve os cortes feitos agora."""
        novos = []
        fila = blocos_fechados(self.pasta, self.vistos, final)
        for i, bloco in enumerate(fila):
            ultimo = final and i == len(fila) - 1
            try:
                novos += self._bloco(bloco, ultimo)
            except (ErroCorte, ErroCacador, OSError) as e:  # bloco ruim não derruba a live
                self._avisar(f"   ⚠️ {bloco.name}: {str(e)[:100]}")
            self.vistos.add(bloco.name)
        limpar(self.pasta)
        return novos

    def _bloco(self, bloco, ultimo):
        inicio = self._relogio
        self._relogio += self._medir(bloco)
        lista = [self._anterior[0], bloco] if self._anterior else [bloco]
        inicio_janela = self._anterior[1] if self._anterior else inicio
        self._anterior = (bloco, inicio)
        self.janelas.mkdir(exist_ok=True)
        janela = self.janelas / f"janela-{bloco.stem}.mp4"
        janela.unlink(missing_ok=True)
        feitos = []
        try:
            duracao = self._juntar(lista, janela)
            for m in self._cacar(janela):
                if len(feitos) >= self._maximo:
                    break
                if not ultimo and m.pico + cacador.DEPOIS > duracao:
                    continue  # pico colado no fim: a próxima janela pega ele inteiro
                absoluto = inicio_janela + m.pico
                if ja_cortado(absoluto, self.cortados):
                    continue
                try:
                    corte = self._cortar(janela, m, duracao)
                except ErroCorte as e:
                    self._avisar(f"   ⚠️ não cortei {hms(absoluto)}: {e}")
                    continue
                self.cortados.append(absoluto)
                feitos.append((absoluto, m, corte))
                self._avisar(f"✂️  {hms(absoluto)} da live (+{m.forca} dB) → {Path(corte).name}")
        finally:
            janela.unlink(missing_ok=True)  # a janela é rascunho: o corte já saiu
        self.feitos += feitos
        return feitos


# ---------- peças reais (FFmpeg, streamlink, Whisper) ----------

def comando_gravador(url):
    return [sys.executable, "-m", "streamlink", "--retry-streams", "30", "--retry-max", "20",
            "--stdout", url, "best"]


def comando_fatiar(ffmpeg, pasta):
    return [ffmpeg, "-hide_banner", "-loglevel", "error", "-i", "pipe:0", "-c", "copy",
            "-f", "segment", "-segment_time", str(SEGUNDOS_BLOCO), "-segment_format", "mpegts",
            str(Path(pasta) / "bloco_%04d.ts")]


def comando_juntar(ffmpeg, lista_txt, saida):
    return [ffmpeg, "-hide_banner", "-nostdin", "-loglevel", "error", "-n",
            "-f", "concat", "-safe", "0", "-i", str(lista_txt),
            "-c", "copy", "-avoid_negative_ts", "make_zero", str(saida)]


def juntar_real(ffmpeg, blocos, saida, executar=subprocess.run):
    """Cola os blocos num .mp4 só (sem reprocessar) e devolve a duração em segundos."""
    lista = Path(saida).with_suffix(".txt")
    linhas = ["file '" + str(Path(b).resolve()).replace("'", "'\\''") + "'" for b in blocos]
    lista.write_text("\n".join(linhas) + "\n", encoding="utf-8")
    try:
        r = executar(comando_juntar(ffmpeg, lista, saida), stdin=subprocess.DEVNULL,
                     capture_output=True, timeout=300, shell=False, check=False)
    finally:
        lista.unlink(missing_ok=True)
    if r.returncode != 0:
        erro = (r.stderr or b"").decode("utf-8", "replace").strip().splitlines()
        raise ErroCorte("O FFmpeg não juntou os blocos: " + (erro[-1][:120] if erro else "sem detalhe"))
    return cortador.duracao_video(saida)


def cacar_real(janela, robo=None):
    energias = cacador.energia_por_segundo(janela)
    if robo is None:
        return cacador.achar_picos(energias, maximo=MAX_POR_JANELA * 3)
    candidatos = cacador.achar_picos(energias, maximo=MAX_POR_JANELA * cacador.CANDIDATOS)
    return cacador.reordenar(candidatos, lambda m: robo.ouvir(janela, m.inicio, m.fim),
                             maximo=MAX_POR_JANELA * 3)


class Gravador:
    """streamlink | ffmpeg rodando por trás. `parar()` desliga os dois."""

    def __init__(self, url, ffmpeg, pasta):
        self._log = open(Path(pasta) / "gravador.log", "ab")  # se a live não pegar, o motivo fica aqui
        self._streamlink = subprocess.Popen(comando_gravador(url), stdout=subprocess.PIPE,
                                            stderr=self._log, stdin=subprocess.DEVNULL)
        self._ffmpeg = subprocess.Popen(comando_fatiar(ffmpeg, pasta), stdin=self._streamlink.stdout,
                                        stderr=self._log)
        self._streamlink.stdout.close()  # o pipe agora é só do ffmpeg

    def vivo(self):
        return self._ffmpeg.poll() is None

    def parar(self):
        for p in (self._streamlink, self._ffmpeg):
            if p.poll() is None:
                p.terminate()
        for p in (self._streamlink, self._ffmpeg):
            try:
                p.wait(timeout=15)
            except subprocess.TimeoutExpired:
                p.kill()
        self._log.close()


def _pastas():
    entrada = cortador.ler_pasta("JARVIS_CORTES_ENTRADA")
    saida = cortador.ler_pasta("JARVIS_CORTES_SAIDA")
    notas = cortador.ler_pasta("JARVIS_PASTA_DECISOES")
    falta = [n for n, p in (("JARVIS_CORTES_ENTRADA", entrada), ("JARVIS_CORTES_SAIDA", saida),
                            ("JARVIS_PASTA_DECISOES", notas)) if p is None]
    if falta:
        raise ErroCorte("Falta configurar no .env (pasta que exista): " + ", ".join(falta))
    if entrada == saida:
        raise ErroCorte("A pasta de saída não pode ser a mesma da entrada.")
    return entrada, saida, notas


def rodar(url=None, pasta=None, arroba="", falas=False, avisar=print):
    if not cortador.ARROBA.fullmatch(arroba or ""):
        raise ErroCorte("O @ do amigo é obrigatório e só aceita letras, números, _ e ponto.")
    entrada, saida, notas = _pastas()
    ffmpeg = cortador.achar_ffmpeg()
    agora = datetime.now()
    if pasta:
        blocos = Path(pasta).resolve()
        if not blocos.is_dir():
            raise ErroCorte("Não achei a pasta de blocos.")
    else:
        url = validar_url(url)
        blocos = entrada / f"ao-vivo-{agora:%Y-%m-%d-%H%M%S}"
        blocos.mkdir()
    destino = saida / "ao-vivo"
    destino.mkdir(exist_ok=True)

    avisar("👂 Ligando o Whisper (legendas)...")
    robo = cortador.Cortador(ffmpeg, cortador.transcritor_whisper())
    contador = iter(range(1, 10_000))

    def cortar(janela, m, duracao):
        saida_corte = cortador.nome_saida(destino, next(contador))
        return robo.cortar(janela, m.inicio, m.fim, duracao, arroba, saida_corte)

    vigia = Vigia(blocos, cortador.duracao_video, lambda b, s: juntar_real(ffmpeg, b, s),
                  lambda j: cacar_real(j, robo if falas else None), cortar, avisar=avisar)
    gravador = None
    try:
        if pasta:
            avisar(f"📂 Modo teste: blocos de {blocos.name}")
            vigia.passo(final=True)
        else:
            avisar(f"📡 Gravando a live em blocos de {SEGUNDOS_BLOCO} s → {blocos.name}  (Ctrl+C para parar)")
            gravador = Gravador(url, ffmpeg, blocos)
            while gravador.vivo():
                vigia.passo()
                time.sleep(ESPERA)
            avisar("📴 A live acabou (ou caiu de vez).")
            vigia.passo(final=True)
    except KeyboardInterrupt:
        avisar("🛑 Parando...")
        if gravador:
            gravador.parar()
        vigia.passo(final=True)
    finally:
        if gravador:
            gravador.parar()
    nota = notas / f"{agora:%Y-%m-%d-%H%M%S}-ao-vivo.md"
    with open(nota, "x", encoding="utf-8") as f:
        f.write(nota_markdown(blocos.name, arroba, vigia.feitos))
    avisar(f"✅ {len(vigia.feitos)} corte(s) em {destino}. Resumo no cofre: {nota.name}")
    avisar("⚠️ Nada foi postado. Confira cada corte (e a rádio do GTA) antes de publicar.")
    return vigia.feitos


def nota_markdown(origem, arroba, feitos):
    linhas = [f"# 📡 Cortes ao vivo — {origem}", "", f"Crédito: {cortador.arroba_normal(arroba)}", "",
              "| Hora da live | Força | Corte |", "|---|---|---|"]
    for absoluto, m, corte in feitos:
        linhas.append(f"| {hms(absoluto)} | +{m.forca} dB | `{Path(corte).name}` |")
    if not feitos:
        linhas.append("| — | — | nenhum momento forte |")
    return "\n".join(linhas) + "\n\n> Nada foi postado. Aprovar um por um.\n"


def main(argv=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(prog="ao_vivo.py", description="Cortes da live ao vivo (Ticket 094)")
    p.add_argument("url", nargs="?", help="link https da live (YouTube, Twitch ou Kick)")
    p.add_argument("--pasta", help="modo teste: pasta com bloco_0000.ts, bloco_0001.ts...")
    p.add_argument("--arroba", required=True, help="@ do amigo (crédito em todo corte)")
    p.add_argument("--falas", action="store_true", help="Ticket 092: Whisper ouve os candidatos (mais lento)")
    a = p.parse_args(argv)
    if bool(a.url) == bool(a.pasta):
        p.error("passe o link da live OU --pasta (um dos dois)")
    try:
        rodar(a.url, a.pasta, a.arroba, a.falas, avisar=lambda t: print(t, flush=True))
    except (ErroCorte, ErroCacador, FileExistsError) as e:
        print(f"❌ {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
