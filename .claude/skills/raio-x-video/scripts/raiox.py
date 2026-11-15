"""raio-x-video — bloco 4: roda tudo e monta a nota do raio-X.

Roda cortes.py → fotos.py → som.py e escreve uma nota Markdown com os números já preenchidos
e o checklist em branco para a IA completar olhando as folhas e a onda.

Uso:
    python raiox.py VIDEO.mp4 --nota PASTA_DAS_NOTAS [--limiar 0.3] [--fonte "meu vídeo"]

--nota: pasta onde a nota vai (no cofre: knowledge/raio-x). Ela recebe:
    <nome>.md   e   <nome>/folha_1.jpg, onda.png  (cópias pequenas, para abrir no Obsidian)
A pasta do cofre vem do argumento, nunca escrita no código (o caminho mostra o usuário do Windows).
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
from datetime import date
from pathlib import Path

AQUI = Path(__file__).resolve().parent


def etapa(script: str, args: list[str]) -> None:
    r = subprocess.run([sys.executable, str(AQUI / script), *args],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(r.stdout.rstrip())
    if r.returncode != 0:
        sys.exit(f"❌ {script} falhou:\n{r.stderr[-800:]}")


def nome_seguro(texto: str) -> str:
    limpo = re.sub(r"[^\w\-]+", "-", texto, flags=re.UNICODE).strip("-").lower()
    return limpo[:60] or "video"


def linha_do_tempo(cenas: list[dict], golpes: list[float]) -> str:
    linhas = ["| Cena | Início | Duração | Golpes de som |", "|---|---|---|---|"]
    for c in cenas[:60]:
        dentro = [g for g in golpes if c["inicio"] <= g < c["fim"]]
        marca = ", ".join(f"{g:.1f}" for g in dentro[:6]) or "—"
        linhas.append(f"| {c['n']} | {c['inicio']:.2f} s | {c['dura']:.2f} s | {marca} |")
    if len(cenas) > 60:
        linhas.append(f"| … | mais {len(cenas) - 60} cenas no cortes.json | | |")
    return "\n".join(linhas)


def montar_nota(video: Path, fonte: str, corte: dict, som: dict, imagens: list[str]) -> str:
    r = corte["resumo"]
    if som.get("tem_audio"):
        bpm = f"{som['bpm']} (confiança {som['bpm_confianca']})" if som.get("bpm") else "sem batida regular"
        tempo = som.get("cortes_na_batida")
        na_batida = f"{tempo['no_tempo']} de {tempo['cortes']} ({tempo['pct']}%)" if tempo else "—"
        linhas_som = (
            f"| Volume médio / pico | {som['volume_medio_db']} dB / {som['volume_pico_db']} dB |\n"
            f"| Silêncios | {len(som['silencios'])} ({som['tempo_em_silencio_pct']}% do vídeo) |\n"
            f"| Golpes de som | {len(som['golpes'])} ({som['golpes_por_minuto']}/min) |\n"
            f"| Batida (BPM) | {bpm} |\n"
            f"| Cortes na batida | {na_batida} |"
        )
        golpes = som["golpes"]
    else:
        linhas_som = "| Som | 🔇 sem áudio |"
        golpes = []
    primeiro = f"{r['primeiro_corte_s']} s" if r["primeiro_corte_s"] is not None else "sem corte"
    figuras = "\n".join(f"![[{i}]]" for i in imagens)
    return f"""# 🩻 Raio-X — {video.name}

- **Data:** {date.today().isoformat()}
- **Fonte / autorização:** {fonte}
- Feito com a skill `raio-x-video`. Ver [[rea-video]] · [[raio-x-padroes]] · [[canal-brabo]]

## Números (automático)
| Medida | Valor |
|---|---|
| Duração | {r['duracao_s']} s |
| Cortes | {r['cortes']} ({r['cortes_por_minuto']}/min) |
| Cena média | {r['cena_media_s']} s → {r['ritmo']} |
| Cena mais curta / longa | {r['cena_mais_curta_s']} s / {r['cena_mais_longa_s']} s |
| Primeiro corte (fim do gancho) | {primeiro} |
{linhas_som}

{figuras}

## Linha do tempo
{linha_do_tempo(corte['cenas'], golpes)}

## Checklist (a 001 preenche olhando a folha e a onda)
1. **Gancho** (antes do 1º corte): _preencher_
2. **Cortes e ritmo**: _preencher_
3. **Legenda** (fonte, cor, como entra): _preencher_
4. **Som** (voz, música, efeitos): _preencher_
5. **Tipo de cada cena** (filmada, IA, tela gravada, texto animado): _preencher_
6. **Material próprio** (o que o José teria que gravar): _preencher_

## Padrão aprendido
- _uma frase curta, que também vai para [[raio-x-padroes]]_

## Como fazer no nosso estilo (sem copiar)
- _preencher_
"""


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description="Raio-X completo de um vídeo")
    p.add_argument("video", type=Path)
    p.add_argument("--nota", type=Path, required=True, help="pasta das notas (no cofre: knowledge/raio-x)")
    p.add_argument("--limiar", type=float, default=0.3)
    p.add_argument("--modo", choices=["normal", "jogo"], default="normal")
    p.add_argument("--fonte", default="_preencher: vídeo próprio ou autorização do dono_")
    args = p.parse_args()

    video = args.video.resolve()
    if not video.is_file():
        sys.exit(f"Vídeo não encontrado: {video}")
    pasta = video.parent / f"raio-x_{video.stem}"
    etapa("cortes.py", [str(video), "--limiar", str(args.limiar), "--modo", args.modo, "--saida", str(pasta)])
    etapa("fotos.py", [str(video), "--saida", str(pasta)])
    etapa("som.py", [str(video), "--saida", str(pasta)])

    corte = json.loads((pasta / "cortes.json").read_text(encoding="utf-8"))
    som = json.loads((pasta / "som.json").read_text(encoding="utf-8"))

    notas = args.nota.resolve()
    notas.mkdir(parents=True, exist_ok=True)
    base = nome_seguro(video.stem)
    nome, n = base, 2
    while (notas / f"{nome}.md").exists():  # nunca sobrescreve um raio-X antigo
        nome, n = f"{base}-{n}", n + 1
    anexos = notas / nome
    anexos.mkdir(exist_ok=True)
    imagens = []
    for img in sorted(pasta.glob("folha_*.jpg")) + [pasta / "onda.png"]:
        if img.is_file():
            shutil.copy2(img, anexos / img.name)
            imagens.append(f"{nome}/{img.name}")

    destino = notas / f"{nome}.md"
    destino.write_text(montar_nota(video, args.fonte, corte, som, imagens), encoding="utf-8")
    print(f"📝 Nota pronta para completar: {destino}")


if __name__ == "__main__":
    main()
