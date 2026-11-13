"""
Jarvis 001 — Super produtor de vídeos 🎬 (Ticket 045: da live ao pacote pronto num comando)

Junta os robôs de vídeo que já existem:
  caça os momentos → corta os mais fortes → textos da Gemma → gancho na tela → painel no cofre
  → (se pedir) legenda traduzida de cada corte.

  python produtor.py live.mp4 --arroba @amigo
  python produtor.py live.mp4 --arroba @amigo --top 12 --idioma en

Regras:
- Usa os MESMOS porteiros de cada robô (duração, @, nome do vídeo só dentro da pasta, IA conferida).
- Um corte que falha na tradução não derruba os outros: o aviso aparece no fim.
- Nada é postado. Quem aprova, corte por corte, é o José no painel do cofre.
"""

import argparse
import sys

import legendas


def traduzir_cortes(itens, pasta_saida, idioma, perguntar):
    """Traduz a legenda de cada corte pronto. Devolve [(nome do corte, arquivo novo ou "", aviso ou "")]."""
    feitos = []
    for item in itens:
        if not getattr(item, "corte", ""):
            continue
        nome = str(item.corte)
        original = (pasta_saida / nome).with_suffix(".srt")
        destino = original.with_name(f"{original.stem}.{idioma}.srt")
        try:
            if original.is_symlink() or not original.is_file():
                raise legendas.ErroLegenda("sem legenda")
            falas = legendas.ler_srt(original.read_text(encoding="utf-8", errors="replace"))
            traduzidas = legendas.traduzir(falas, idioma, perguntar)
            with open(destino, "x", encoding="utf-8", newline="\n") as f:
                f.write(legendas.gerar_srt(traduzidas))
            feitos.append((nome, destino.name, ""))
        except FileExistsError:
            feitos.append((nome, "", "a tradução já existia"))
        except (legendas.ErroLegenda, OSError) as e:
            feitos.append((nome, "", str(e)[:120]))
    return feitos


def main(argv=None):
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(prog="produtor.py", description="Super produtor de vídeos (Ticket 045)")
    p.add_argument("video", help="nome do vídeo na pasta de entrada")
    p.add_argument("--arroba", required=True, help="@ do dono da live (crédito em todo corte)")
    p.add_argument("--top", type=int, default=8, help="quantos momentos cortar (1 a 20)")
    p.add_argument("--idioma", choices=sorted(legendas.IDIOMAS), help="também traduz a legenda de cada corte")
    a = p.parse_args(argv)
    import cortador
    import cortes
    import textos
    try:
        itens = cortes.pacote(a.video, a.arroba, a.top)
    except (cortador.ErroCorte, cortes.ErroCacador, FileExistsError) as e:
        print(f"❌ {e}")
        return 1
    if a.idioma:
        saida = cortador.ler_pasta("JARVIS_CORTES_SAIDA")
        print(f"🌎 Traduzindo as legendas para {legendas.IDIOMAS[a.idioma]}...")
        try:
            feitos = traduzir_cortes(itens, saida, a.idioma, textos.perguntar_gemma)
        except textos.ErroTextos as e:
            print(f"⚠️ Os cortes estão prontos, mas a tradução parou: {e}")
            return 1
        for nome, arquivo, aviso in feitos:
            print(f"   ✅ {arquivo}" if arquivo else f"   ⚠️ {nome}: {aviso}")
    print("🎬 Produção pronta. Abra o painel no cofre e aprove um por um.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
