"""
Jarvis 001 — Rotinas 🌅 (Ticket 009)

"Bom dia, Jarvis" → um resumo falado do dia, juntando os braços:
hora (cumprimento certo), pendências (quantas + a primeira) e git-status (algo sem commit?).
Ticket 024: + a Sentinela (portas e travas) numa frase só. Com alerta, ela vem LOGO depois do "bom dia".
No fim, a cobrança da Cyber (regra do projeto: a Cyber nunca fica 2 dias parada).

Cada braço pode falhar sozinho sem derrubar o resumo: o que der certo é falado.
Só LÊ: nenhuma rotina escreve nada.
"""

from bracos import ErroBraco
from intencoes import seguro, _limpar_para_falar


def _cumprimento(hora_texto):
    """'08:10' → 'Bom dia'. Hora estranha → 'Olá'."""
    try:
        h = int(str(hora_texto).split(":")[0])
    except (ValueError, IndexError):
        return "Olá"
    if 5 <= h < 12:
        return "Bom dia"
    if 12 <= h < 18:
        return "Boa tarde"
    return "Boa noite"


def _tentar(funcao):
    """Chama um braço; se falhar, devolve None (o resumo segue sem aquela parte).
    Exceção: ALARME de segurança (impostor, assinatura recusada) sobe e interrompe o resumo."""
    try:
        dados = funcao()
        return dados if isinstance(dados, dict) else None
    except ErroBraco as e:
        if e.alarme:
            raise
        return None


def bom_dia(bracos):
    """Monta o resumo do dia. Devolve uma frase curta e segura para falar."""
    try:
        return _bom_dia(bracos)
    except ErroBraco as e:
        # Alarme: o José precisa ouvir isso, não um "não consegui ler"
        return seguro(f"Atenção, José: {e}")


def _bom_dia(bracos):
    hora = _tentar(bracos.hora)
    pend = _tentar(bracos.pendencias)
    git = _tentar(bracos.git_status)
    sentinela = _sentinela(_tentar(bracos.portas), _tentar(bracos.integridade))

    partes = []
    if hora:
        partes.append(f"{_cumprimento(hora.get('hora'))}, José! São {hora.get('hora', '?')}.")
    else:
        partes.append("Olá, José!")

    alerta = sentinela is not None and sentinela.startswith("Sentinela em alerta")
    if alerta:
        partes.append(sentinela)  # segurança primeiro

    if pend is None:
        partes.append("Não consegui ler suas pendências.")
    else:
        try:
            total = int(pend.get("total", 0))
            itens = pend.get("pendencias") or []
            if total == 0:
                partes.append("Nenhuma pendência nos últimos 7 dias.")
            else:
                primeira = _limpar_para_falar(itens[0].get("texto", "")) if itens else ""
                primeira = primeira.rstrip(" .!?…")  # a nota já pode terminar em ponto: evita ".."
                plural = "pendência" if total == 1 else "pendências"
                partes.append(f"Você tem {total} {plural}. A primeira: {primeira}.")
        except (TypeError, ValueError, AttributeError, IndexError, OverflowError):
            partes.append("Suas pendências vieram num formato estranho.")

    if git is not None:
        try:
            if git.get("limpo"):
                partes.append("No Jarvis está tudo commitado.")
            else:
                soma = int(git.get("alterados", 0)) + int(git.get("novos", 0))
                partes.append(f"No Jarvis tem {soma} arquivo{'s' if soma != 1 else ''} sem commit.")
        except (TypeError, ValueError, OverflowError):
            pass

    if sentinela is not None and not alerta:
        partes.append(sentinela)

    partes.append("E a Cyber? Reserve 30 minutos para ela hoje.")
    return seguro(" ".join(partes))


def _sentinela(portas, integ):
    """Ticket 024: a Sentinela numa frase só (ou None se os dois braços falharam). Só lê; quem age é o José."""
    alertas, tranquilo = [], []
    if portas is not None:
        try:
            n = int(portas.get("alertas", 0))
            exposta = portas.get("jarvisSoNoPc") is not True
            if exposta:
                alertas.append("a API do Jarvis está aberta para a rede")
            if n > 0:
                alertas.append(f"{n} alerta{'s' if n != 1 else ''} nas portas")
            if not exposta and n == 0:
                tranquilo.append("portas sem alerta")
        except (TypeError, ValueError, OverflowError):
            pass
    if integ is not None:
        try:
            m = int(integ.get("alterados", 0))
            if m > 0:
                alertas.append(f"{m} trava{'s mudaram' if m != 1 else ' mudou'}")
            else:
                tranquilo.append("travas iguais")
        except (TypeError, ValueError, OverflowError):
            pass
    if alertas:
        return "Sentinela em alerta: " + "; ".join(alertas) + ". Peça os detalhes antes de tudo."
    if tranquilo:
        return "Sentinela: " + " e ".join(tranquilo) + "."
    return None
