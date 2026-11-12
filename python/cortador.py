"""
Jarvis 001 — Robô de cortes ✂️ (Ticket 025, V5)

Pega um trecho de um vídeo longo e entrega um corte EM PÉ (9:16), com legenda e o @ do amigo.
  1. confere o pedido (as mesmas regras do porteiro Java que o José digita: defesa em camadas)
  2. tira o áudio do trecho → o Whisper escreve a legenda (local)
  3. o FFmpeg corta, deixa em pé e grava a legenda + o @

Travas 🔒
- O original só é LIDO. O corte vai para outra pasta, com nome gerado aqui (nunca do título).
- FFmpeg chamado com LISTA de argumentos, sem shell: nome de arquivo nunca vira comando.
- `-n`: o FFmpeg nunca sobrescreve um arquivo.
- Legenda num arquivo de nome FIXO numa pasta temporária; o texto do Whisper é desarmado.
- Arquivo de entrada = só o NOME, dentro da pasta permitida (anti Path Traversal).
"""

import os
import re
import shutil
import subprocess
import tempfile
from datetime import datetime
from pathlib import Path

import av

# ---------- regras do porteiro (iguais às do PorteiroDeCortes.java) ----------
MIN_DURACAO = 13        # segundos (decisão do José, 11/11)
MAX_DURACAO = 180       # 3 min
MAX_CORTES = 20         # por pedido
ESPACO_MINIMO = 2 * 1024 ** 3  # 2 GB livres antes de cortar
TEMPO_LIMITE = 30 * 60  # um corte nunca roda mais de 30 min
ARROBA = re.compile(r"@?[A-Za-z0-9_.]{1,30}")
EXTENSOES = {".mp4", ".mkv", ".mov", ".flv", ".ts", ".webm", ".m4v"}
ENV_DA_RAIZ = Path(__file__).resolve().parent.parent / ".env"

# Corta o meio da imagem no formato em pé e deixa em 1080x1920. A legenda tem nome fixo.
FILTRO = ("crop=w=min(iw\\,ih*9/16):h=min(ih\\,iw*16/9),"
          "scale=1080:1920,setsar=1,subtitles=legenda.ass")


class ErroCorte(Exception):
    """Mensagem curta para o José."""


def liberar(inicio, fim, duracao_video, arroba):
    """O porteiro: (True, "ok") ou (False, motivo). Mesmas regras do Java."""
    if not ARROBA.fullmatch(arroba or ""):
        return False, "o @ do amigo é obrigatório e só aceita letras, números, _ e ponto"
    if inicio < 0:
        return False, "o começo não pode ser negativo"
    if fim <= inicio:
        return False, "o fim precisa ser depois do começo"
    if fim > duracao_video:
        return False, "o corte passa do fim do vídeo"
    duracao = fim - inicio
    if duracao < MIN_DURACAO:
        return False, f"corte com menos de {MIN_DURACAO} s"
    if duracao > MAX_DURACAO:
        return False, f"corte com mais de {MAX_DURACAO // 60} min"
    return True, "ok"


def arroba_normal(arroba):
    return arroba if arroba.startswith("@") else "@" + arroba


# ---------- pastas e arquivos ----------

def ler_pasta(nome, env=os.environ, arquivo=ENV_DA_RAIZ):
    """Pasta da variável `nome` (ambiente ou só aquela linha do .env). Não existe → None."""
    valor = env.get(nome, "").strip()
    if not valor:
        try:
            for linha in arquivo.read_text(encoding="utf-8-sig").splitlines():
                linha = linha.strip()
                if linha.startswith("export "):
                    linha = linha[len("export "):].strip()
                if linha.startswith(nome + "="):
                    valor = linha.split("=", 1)[1].strip().strip('"').strip("'")
                    break
        except OSError:
            pass
    if not valor:
        return None
    pasta = Path(valor)
    return pasta.resolve() if pasta.is_dir() else None


def resolver_entrada(pasta, nome):
    """Só o NOME do vídeo, dentro da pasta permitida. Caminho, `..`, atalho e extensão estranha → recusa."""
    nome = str(nome or "")
    if (not nome or nome != nome.strip() or any(c in nome for c in '/\\:*?"<>|')
            or nome.startswith(".") or any(not c.isprintable() for c in nome)):
        raise ErroCorte("Passe só o nome do vídeo (sem pasta), que está na pasta de entrada.")
    pasta = Path(pasta).resolve()
    caminho = pasta / nome
    if caminho.suffix.lower() not in EXTENSOES:
        raise ErroCorte("Extensão de vídeo fora da lista.")
    if caminho.is_symlink() or not caminho.is_file():
        raise ErroCorte("Não achei esse vídeo na pasta de entrada.")
    if caminho.resolve().parent != pasta:
        raise ErroCorte("Esse vídeo está fora da pasta de entrada.")
    return caminho.resolve()


def nome_saida(pasta, numero, agora=None):
    """Nome gerado aqui: 2026-11-11-143005-corte-03.mp4 (com -2, -3 se já existir)."""
    agora = agora or datetime.now()
    base = f"{agora:%Y-%m-%d-%H%M%S}-corte-{numero:02d}"
    caminho, n = Path(pasta) / f"{base}.mp4", 2
    while caminho.exists() or caminho.with_suffix(".srt").exists():
        caminho, n = Path(pasta) / f"{base}-{n}.mp4", n + 1
    return caminho


def duracao_video(caminho):
    """Duração em segundos (inteiros, para baixo)."""
    try:
        with av.open(str(caminho)) as c:
            if c.duration is None:
                raise ErroCorte("Não consegui ler a duração do vídeo.")
            return int(c.duration / av.time_base)
    except (av.FFmpegError, OSError) as e:
        raise ErroCorte("Não consegui abrir o vídeo.") from e


def achar_ffmpeg():
    caminho = shutil.which("ffmpeg")
    if not caminho:
        raise ErroCorte("FFmpeg não instalado. Rode: winget install --id Gyan.FFmpeg -e (fonte oficial).")
    return caminho


# ---------- legenda (.ass) ----------

_CABECALHO_ASS = """[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 0

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Legenda,Arial,72,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,6,2,2,60,60,420,1
Style: Credito,Arial,54,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,4,1,8,60,60,160,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""


def desarmar_ass(texto):
    """`{...}` e `\\` são comandos de legenda: viram texto comum. Controle e quebra de linha somem."""
    texto = str(texto).replace("\\", "/").replace("{", "(").replace("}", ")")
    texto = "".join(c if c.isprintable() else " " for c in texto)
    return " ".join(texto.split())[:200]


def _tempo_ass(seg):
    cs = max(0, round(seg * 100))
    h, cs = divmod(cs, 360000)
    m, cs = divmod(cs, 6000)
    s, cs = divmod(cs, 100)
    return f"{h}:{m:02d}:{s:02d}.{cs:02d}"


def gerar_ass(falas, duracao, arroba):
    """falas = [(inicio, fim, texto)] em segundos do corte. O @ fica no topo o corte inteiro."""
    linhas = [_CABECALHO_ASS.rstrip("\n"),
              f"Dialogue: 0,{_tempo_ass(0)},{_tempo_ass(duracao)},Credito,,0,0,0,,{desarmar_ass(arroba)}"]
    for ini, fim, texto in falas:
        texto = desarmar_ass(texto)
        ini, fim = max(0.0, float(ini)), min(float(duracao), float(fim))
        if texto and fim > ini:
            linhas.append(f"Dialogue: 0,{_tempo_ass(ini)},{_tempo_ass(fim)},Legenda,,0,0,0,,{texto}")
    return "\n".join(linhas) + "\n"


# ---------- comandos do FFmpeg (sempre lista, nunca texto para o shell) ----------

_BASE = ["-hide_banner", "-nostdin", "-loglevel", "error", "-n"]


def comando_audio(ffmpeg, entrada, inicio, duracao):
    """Tira só o áudio do trecho (mono, 16 kHz) para o Whisper."""
    return [ffmpeg, *_BASE, "-ss", str(inicio), "-t", str(duracao), "-i", f"file:{entrada}",
            "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "file:audio.wav"]


def comando_corte(ffmpeg, entrada, inicio, duracao, saida):
    """Corta, deixa em pé (9:16) e grava a legenda. `file:` impede o nome de virar protocolo."""
    return [ffmpeg, *_BASE, "-ss", str(inicio), "-t", str(duracao), "-i", f"file:{entrada}",
            "-vf", FILTRO, "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", f"file:{saida}"]


def ler_wav(caminho):
    """Lê o .wav que o FFmpeg gerou (mono, 16 kHz, 16 bits) → números de -1 a 1 para o Whisper."""
    import wave

    import numpy as np
    with wave.open(str(caminho), "rb") as w:
        if (w.getnchannels(), w.getframerate(), w.getsampwidth()) != (1, 16000, 2):
            raise ErroCorte("O áudio do trecho veio num formato inesperado.")
        dados = w.readframes(w.getnframes())
    return np.frombuffer(dados, dtype=np.int16).astype(np.float32) / 32768.0


# Dica para o Whisper: palavras comuns numa live de GTA em português. Ele passa a acertar mais.
DICA_GTA = ("Live de GTA em português do Brasil. Mano, véi, caraca, irmão, incrível, polícia, fuga, "
            "assalto, banco, aeroporto, carro, moto, helicóptero, tiroteio, estrela, procurado, "
            "missão, Los Santos, Vice City, PS5.")


def _tempo_srt(seg):
    ms = max(0, round(seg * 1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def gerar_srt(falas, duracao):
    """Legenda separada (.srt) para subir junto no YouTube/TikTok e para o V6 ler. Texto desarmado."""
    blocos, n = [], 0
    for ini, fim, texto in falas:
        texto = desarmar_ass(texto)
        ini, fim = max(0.0, float(ini)), min(float(duracao), float(fim))
        if texto and fim > ini:
            n += 1
            blocos.append(f"{n}\n{_tempo_srt(ini)} --> {_tempo_srt(fim)}\n{texto}\n")
    return "\n".join(blocos)


def transcritor_whisper():
    """Liga o Whisper uma vez só (demora) e devolve a função que transcreve um .wav."""
    from faster_whisper import WhisperModel
    modelo = WhisperModel("small", device="cpu", compute_type="int8")

    def transcrever(wav):
        # Entrega o áudio já lido (igual ao ouvido.py): o faster-whisper 1.2.1 não abre arquivo com o PyAV 19
        segmentos, _ = modelo.transcribe(ler_wav(wav), language="pt", vad_filter=True,
                                         initial_prompt=DICA_GTA)
        return [(s.start, s.end, s.text.strip()) for s in segmentos]
    return transcrever


class Cortador:
    """Faz um corte por vez. `executar` e `transcrever` podem ser falsos nos testes."""

    def __init__(self, ffmpeg, transcrever, executar=subprocess.run):
        self._ffmpeg = ffmpeg
        self._transcrever = transcrever
        self._executar = executar

    def cortar(self, entrada, inicio, fim, duracao_total, arroba, saida):
        ok, motivo = liberar(inicio, fim, duracao_total, arroba)
        if not ok:
            raise ErroCorte(f"Porteiro barrou: {motivo}.")
        if shutil.disk_usage(Path(saida).parent).free < ESPACO_MINIMO:
            raise ErroCorte("Menos de 2 GB livres no disco. Libere espaço antes de cortar.")
        duracao = fim - inicio
        with tempfile.TemporaryDirectory(prefix="jarvis-corte-") as tmp:
            self._rodar(comando_audio(self._ffmpeg, entrada, inicio, duracao), tmp)
            falas = self._transcrever(Path(tmp) / "audio.wav")
            Path(tmp, "legenda.ass").write_text(
                gerar_ass(falas, duracao, arroba_normal(arroba)), encoding="utf-8")
            self._rodar(comando_corte(self._ffmpeg, entrada, inicio, duracao, saida), tmp)
        # Só depois do vídeo pronto: corte que falhou não deixa .srt sozinho. "x" = nunca sobrescreve.
        with open(Path(saida).with_suffix(".srt"), "x", encoding="utf-8") as f:
            f.write(gerar_srt(falas, duracao))
        return Path(saida)

    def _rodar(self, comando, pasta):
        try:
            r = self._executar(comando, cwd=pasta, stdin=subprocess.DEVNULL, capture_output=True,
                               timeout=TEMPO_LIMITE, shell=False, check=False)
        except subprocess.TimeoutExpired as e:
            raise ErroCorte("O FFmpeg passou de 30 min num corte. Parei.") from e
        if r.returncode != 0:
            erro = (r.stderr or b"").decode("utf-8", "replace").strip().splitlines()
            ultima = desarmar_ass(erro[-1]) if erro else "sem detalhe"
            raise ErroCorte(f"O FFmpeg falhou: {ultima[:160]}")
