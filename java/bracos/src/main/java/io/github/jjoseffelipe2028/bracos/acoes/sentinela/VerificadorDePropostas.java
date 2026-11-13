package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Verificador de propostas de marca (Ticket 030, fase V4 do módulo de vídeos). 🤝🔎
 * "Chegou um e-mail de patrocínio: é de verdade ou querem roubar o canal?"
 * - Usa as MESMAS regras do detector de golpe (Tickets 023 e 029) no texto do e-mail.
 * - Confere o REMETENTE: e-mail gratuito dizendo ser marca, endereço que não bate com a marca,
 *   endereço com o nome da marca "e algo a mais" (imitação).
 * - Regras fixas, sem IA. Nunca abre link, nunca manda e-mail, nada sai do PC.
 * - A resposta só tem textos fixos daqui: o texto, o remetente e a marca nunca voltam.
 * - Só AVISA. Quem decide é o José.
 */
@Service
public class VerificadorDePropostas {

    public record Parecer(String nivel, int pontos, int links, List<String> motivos, String conselho,
            List<String> passos) {
    }

    /** O que conferir SEMPRE antes de responder uma proposta, mesmo quando parece boa. */
    static final List<String> PASSOS = List.of(
            "Ache o site oficial da marca por conta própria (não pelo link do e-mail) e confira o contato por lá.",
            "Não abra anexo .exe, .scr ou arquivo compactado com senha. Contrato de verdade vem em PDF.",
            "Nunca faça login no canal por um link recebido. Entre digitando o endereço.",
            "Marca de verdade paga você: não existe taxa para receber patrocínio.",
            "Ative a verificação em duas etapas do canal antes de fechar qualquer parceria.");

    private static final Set<String> EMAIL_GRATUITO = Set.of("gmail.com", "googlemail.com", "outlook.com",
            "outlook.com.br", "hotmail.com", "live.com", "yahoo.com", "yahoo.com.br", "icloud.com", "proton.me",
            "protonmail.com", "bol.com.br", "uol.com.br", "terra.com.br", "gmx.com", "mail.ru", "yandex.com",
            "yandex.ru", "aol.com", "zoho.com");

    /** Nomes que ficam no "meio" do endereço em vários países: empresa.com.br, empresa.co.uk. */
    private static final Set<String> SEGUNDO_NIVEL = Set.of("com", "net", "org", "gov", "edu", "co");

    // Domínio simples: letras, números, hífen e pontos (sem "..", sem começar com ponto). Tamanho já vem limitado.
    private static final Pattern DOMINIO = Pattern.compile("^[a-z0-9-]{1,63}(\\.[a-z0-9-]{1,63}){1,6}$");

    private final DetectorDeGolpe detector;

    public VerificadorDePropostas(DetectorDeGolpe detector) {
        this.detector = detector;
    }

    public Parecer analisar(String texto, String remetente, String marca) {
        DetectorDeGolpe.Veredito base = detector.analisar(texto);
        Set<String> motivos = new LinkedHashSet<>(base.motivos());
        int pontos = base.pontos();

        String dominio = dominioDe(remetente);
        String nomeDaMarca = soLetrasENumeros(marca);
        if (remetente != null && !remetente.isBlank()) {
            if (dominio == null) {
                pontos += novo(motivos, 2, "O endereço do remetente está num formato estranho.");
            } else {
                pontos += conferirRemetente(dominio, nomeDaMarca, motivos);
            }
        }

        String nivel = pontos >= 4 ? DetectorDeGolpe.ALTO : pontos >= 2 ? DetectorDeGolpe.SUSPEITO : DetectorDeGolpe.BAIXO;
        return new Parecer(nivel, pontos, base.links(), List.copyOf(motivos), conselho(nivel), PASSOS);
    }

    private static int conferirRemetente(String dominio, String nomeDaMarca, Set<String> motivos) {
        // Os truques de link também valem para o endereço de quem mandou (punycode, final suspeito, imitação)
        int pontos = DetectorDeGolpe.analisarLink(dominio, motivos);
        boolean temMarca = !nomeDaMarca.isEmpty();

        if (EMAIL_GRATUITO.contains(dominio)) {
            if (temMarca) {
                pontos += novo(motivos, 2, "Diz ser de uma marca, mas veio de e-mail gratuito (Gmail, Outlook...).");
            }
            return pontos;
        }
        if (!temMarca) {
            return pontos;
        }
        String nomeDoDominio = soLetrasENumeros(nomePrincipal(dominio));
        if (nomeDoDominio.equals(nomeDaMarca)) {
            return pontos; // marca.com, marca.com.br: bate
        }
        if (nomeDoDominio.contains(nomeDaMarca)) {
            return pontos + novo(motivos, 2,
                    "O endereço do remetente tem o nome da marca com algo a mais: imitação comum.");
        }
        return pontos + novo(motivos, 2, "O endereço do remetente não bate com o nome da marca.");
    }

    /** "Fulano <contato@marca.com.br>" ou "contato@marca.com.br" → "marca.com.br". Estranho → null. */
    static String dominioDe(String remetente) {
        if (remetente == null) {
            return null;
        }
        String limpo = DetectorDeGolpe.normalizar(remetente).trim();
        int fecha = limpo.lastIndexOf('>');
        int abre = limpo.lastIndexOf('<');
        if (abre >= 0 && fecha > abre) {
            limpo = limpo.substring(abre + 1, fecha).trim();
        }
        int arroba = limpo.lastIndexOf('@');
        if (arroba <= 0 || arroba == limpo.length() - 1) {
            return null;
        }
        String dominio = limpo.substring(arroba + 1);
        // Letra fora do ASCII (ex.: "а" russo) é punycode disfarçado: deixa passar para o detector acusar
        boolean soAscii = dominio.chars().allMatch(c -> c < 128);
        if (soAscii && !DOMINIO.matcher(dominio).matches()) {
            return null;
        }
        return dominio.contains(" ") ? null : dominio;
    }

    /** "mail.marca.com.br" → "marca" · "marca-parcerias.com" → "marca-parcerias". */
    static String nomePrincipal(String dominio) {
        String[] partes = dominio.split("\\.");
        int n = partes.length;
        if (n >= 3 && partes[n - 1].length() == 2 && SEGUNDO_NIVEL.contains(partes[n - 2])) {
            return partes[n - 3];
        }
        return n >= 2 ? partes[n - 2] : dominio;
    }

    static String soLetrasENumeros(String texto) {
        return DetectorDeGolpe.normalizar(texto).replaceAll("[^a-z0-9]", "");
    }

    private static int novo(Set<String> motivos, int pontos, String motivo) {
        return motivos.add(motivo) ? pontos : 0;
    }

    static String conselho(String nivel) {
        return switch (nivel) {
            case DetectorDeGolpe.ALTO -> "Não responda, não baixe nada e não clique. Tem muita cara de golpe para roubar o canal.";
            case DetectorDeGolpe.SUSPEITO -> "Desconfie. Só responda depois de confirmar o contato no site oficial da marca.";
            default -> "Não vi sinais fortes de golpe. Mesmo assim, siga os passos antes de fechar.";
        };
    }
}
