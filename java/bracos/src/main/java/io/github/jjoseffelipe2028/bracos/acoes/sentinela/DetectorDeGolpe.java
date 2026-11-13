package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Detector de golpe brasileiro (Ticket 023). 🎣 Ideia do José de 01/11: "Sentinela comandante cyber".
 * Ticket 029 (Escudo do Criador): + golpes contra canal (falso patrocínio com anexo, falso aviso de direitos autorais).
 * - Regras FIXAS escritas à mão (sem IA): mesmas regras, mesma resposta, fácil de testar e explicar.
 * - NUNCA abre o link: só lê o texto. Nada sai do PC.
 * - A resposta tem só textos fixos daqui (motivos e conselho): o texto colado nunca volta na resposta.
 * - Só AVISA. Quem decide é o José.
 */
@Service
public class DetectorDeGolpe {

    static final int MAXIMO_LINKS = 10;
    static final String ALTO = "alto";
    static final String SUSPEITO = "suspeito";
    static final String BAIXO = "baixo";

    public record Veredito(String nivel, int pontos, int links, List<String> motivos, String conselho) {
    }

    /** Um sinal no texto: padrão, pontos e o motivo explicado em português simples. */
    private record Sinal(Pattern padrao, int pontos, String motivo) {
    }

    private static Sinal sinal(String regex, int pontos, String motivo) {
        return new Sinal(Pattern.compile(regex), pontos, motivo);
    }

    // O texto é comparado SEM acento e em minúsculas ("número" vira "numero")
    private static final List<Sinal> SINAIS_DO_TEXTO = List.of(
            sinal("troquei de (numero|celular)|mudei de numero|(meu|esse|este) (e meu )?(numero|celular) novo|meu novo numero"
                    + "|(esse|este) e meu novo|salva (esse|este|meu) (numero|contato)|perdi meu (celular|telefone)",
                    3, "Golpe do falso parente: \"troquei de número\"."),
            sinal("senha|codigo (de )?(verificacao|seguranca|confirmacao|acesso)|codigo que (chegou|recebeu|voce recebeu)"
                    + "|\\bcvv\\b|numero do cartao|dados do cartao|token do banco",
                    3, "Pede senha ou código: banco e loja nunca pedem isso."),
            sinal("\\bpix\\b|transferencia|deposito|me empresta|empresta|taxa de (liberacao|entrega|desbloqueio|envio)"
                    + "|pagar (a |uma )?taxa|boleto",
                    2, "Pede dinheiro, PIX ou pagamento de taxa."),
            sinal("urgente|urgencia|imediatamente|agora mesmo|ultimo aviso|ultima chance"
                    + "|sera (bloquead|cancelad|suspens)|bloqueio|expira|vence hoje|em 24 ?h|nas proximas horas",
                    2, "Pressa: o golpista quer que você aja sem pensar."),
            sinal("voce (ganhou|foi sorteado|foi selecionado)|parabens|premio|resgat(e|ar)|brinde|cashback|bonus",
                    2, "Prêmio ou vantagem boa demais para ser verdade."),
            sinal("nao (conta|fala|fale|conte) (pra|para) ninguem|segredo|so (por )?aqui",
                    1, "Pede segredo: golpe tenta te isolar de quem poderia avisar."),
            sinal("receita federal|cpf (irregular|bloqueado|cancelado|suspenso)|detran|\\bmulta\\b|\\binss\\b|correios"
                    + "|encomenda (retida|taxada|parada)",
                    1, "Usa nome de órgão do governo ou dos Correios."),
            // --- Escudo do Criador (Ticket 029): golpes contra quem tem canal ou página ---
            sinal("\\.(exe|scr|bat|cmd|msi|vbs|lnk|iso|rar|zip|7z)\\b|arquivo (protegido|com senha|compactado)"
                    + "|senha do (arquivo|anexo)",
                    3, "Anexo executável ou compactado com senha: é assim que roubam o canal."),
            sinal("(baix(e|ar|a)|download|instal(e|ar|a)) (\\w+ ){0,4}(jogo|game|app|aplicativo|software|programa"
                    + "|media ?kit|press ?kit|contrato|briefing|material)",
                    2, "Pede para baixar ou instalar algo para fechar a parceria."),
            sinal("(canal|conta|perfil|video|pagina) (\\w+ ){0,3}(sera|vai ser|foi|esta sendo|pode ser) "
                    + "(removid|excluid|encerrad|suspens|desativad|banid|apagad)"
                    + "|(aviso|notificacao|reivindicacao|denuncia|violacao) de (direitos autorais|copyright)"
                    + "|copyright strike|strike de direitos",
                    3, "Ameaça de remover o canal: aviso de verdade aparece dentro do YouTube Studio, não por link."),
            sinal("verifi(que|car|cacao) (\\w+ ){0,2}(conta|canal|perfil)|confirm(e|ar) (\\w+ ){0,2}(conta|identidade|login)"
                    + "|faca (o )?login|entre (na|com a) sua conta|selo (azul|de verificacao)",
                    2, "Pede para fazer login ou \"verificar\" a conta por um link."));

    // Link: com http(s)://, com www., ou "algo.com.br/..." solto no texto.
    // Ticket 029: sem grupo repetido "(\.nome)*". No Java, grupo repetido usa a pilha a cada volta, e um texto
    // de 4000 letras tipo "a.a.a.a..." estourava a pilha (erro 500). Uma classe de letras só não tem esse risco.
    // Ticket 035 (achado do duelo): mesmo sem estourar a pilha, o regex antigo "voltava atrás" milhares de vezes
    // e levava 2,6 s num texto de 4000 letras. Agora o regex só separa os PEDAÇOS do texto em uma passada
    // ("++" e "*+" = não volta atrás), e quem decide se o pedaço é link é o código (ehLinkSolto), sem regex.
    private static final Pattern LINK = Pattern.compile(
            "(?:https?://|www\\.)[^\\s<>\"']++"
                    + "|[a-z0-9][a-z0-9.-]*+(?:/[^\\s<>\"']*+)?");
    /** Finais de endereço aceitos para link SEM http/www (ex.: "bit.ly/x", "site.com.br"). */
    private static final Set<String> FINAIS_DE_LINK = Set.of("com", "net", "org", "br", "xyz", "top", "click", "icu",
            "live", "buzz", "info", "site", "online", "me", "io", "ly", "gl", "co", "at", "is", "gd", "cc", "ai",
            "sbs", "cfd", "rest", "monster", "shop", "tv", "app", "gg", "ru", "nz");
    private static final Pattern IP = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");

    private static final Set<String> ENCURTADORES = Set.of("bit.ly", "tinyurl.com", "cutt.ly", "t.co", "is.gd",
            "encurtador.com.br", "shorturl.at", "rb.gy", "t.ly", "tiny.cc", "ow.ly", "goo.gl", "abre.ai", "s.id");
    /** Ticket 029: onde o falso "media kit" costuma ficar hospedado. */
    private static final Set<String> GUARDA_ARQUIVOS = Set.of("mediafire.com", "mega.nz", "dropbox.com",
            "wetransfer.com", "4shared.com", "anonfiles.com", "cdn.discordapp.com", "gofile.io");
    private static final Set<String> TLD_SUSPEITO =Set.of("xyz", "top", "click", "icu", "live", "buzz", "sbs",
            "cfd", "rest", "monster", "online", "site", "shop");

    /** Marca → domínios oficiais. Marca no endereço SEM ser o domínio oficial = imitação. */
    private static final Map<String, List<String>> MARCAS = Map.ofEntries(
            Map.entry("nubank", List.of("nubank.com.br")),
            Map.entry("itau", List.of("itau.com.br", "itaucard.com.br")),
            Map.entry("bradesco", List.of("bradesco.com.br")),
            Map.entry("santander", List.of("santander.com.br")),
            Map.entry("caixa", List.of("caixa.gov.br")),
            Map.entry("bancodobrasil", List.of("bb.com.br")),
            Map.entry("mercadopago", List.of("mercadopago.com.br", "mercadopago.com")),
            Map.entry("mercadolivre", List.of("mercadolivre.com.br")),
            Map.entry("correios", List.of("correios.com.br")),
            Map.entry("serasa", List.of("serasa.com.br")),
            Map.entry("picpay", List.of("picpay.com")),
            Map.entry("whatsapp", List.of("whatsapp.com", "wa.me")),
            // Escudo do Criador (Ticket 029): as plataformas que o golpista imita para roubar canal
            Map.entry("youtube", List.of("youtube.com", "youtu.be")),
            Map.entry("google", List.of("google.com", "google.com.br", "withgoogle.com")),
            Map.entry("instagram", List.of("instagram.com")),
            Map.entry("tiktok", List.of("tiktok.com")),
            Map.entry("facebook", List.of("facebook.com", "fb.com")),
            Map.entry("twitch", List.of("twitch.tv")),
            Map.entry("kwai", List.of("kwai.com")),
            Map.entry("gov", List.of("gov.br")),
            Map.entry("receitafederal", List.of("gov.br")));

    public Veredito analisar(String texto) {
        String normal = normalizar(texto);
        Set<String> motivos = new LinkedHashSet<>();
        int pontos = 0;

        for (Sinal s : SINAIS_DO_TEXTO) {
            if (s.padrao().matcher(normal).find()) {
                pontos += s.pontos();
                motivos.add(s.motivo());
            }
        }

        int links = 0;
        Matcher m = LINK.matcher(normal);
        while (links < MAXIMO_LINKS && m.find()) {
            String pedaco = semPontuacaoNoFim(m.group()); // "site.com.br." no fim da frase → "site.com.br"
            boolean comPrefixo = pedaco.startsWith("http://") || pedaco.startsWith("https://") || pedaco.startsWith("www.");
            if (!comPrefixo && !ehLinkSolto(pedaco)) {
                continue; // palavra comum, não é link
            }
            links++;
            pontos += analisarLink(pedaco, motivos);
        }

        // 4 pontos = 2 sinais fortes juntos (ex.: pressa + PIX, prêmio + link encurtado): o desenho clássico do golpe
        String nivel = pontos >= 4 ? ALTO : pontos >= 2 ? SUSPEITO : BAIXO;
        return new Veredito(nivel, pontos, links, List.copyOf(motivos), conselho(nivel));
    }

    static String semPontuacaoNoFim(String pedaco) {
        int fim = pedaco.length();
        while (fim > 0 && ".,;:!?)-".indexOf(pedaco.charAt(fim - 1)) >= 0) {
            fim--;
        }
        return pedaco.substring(0, fim);
    }

    /** "bit.ly/x" e "site.com.br." são links; "app.", "fim.depois" e "a.a.a.a" não. Sem regex: uma passada só. */
    static boolean ehLinkSolto(String pedaco) {
        int barra = pedaco.indexOf('/');
        String host = barra < 0 ? pedaco : pedaco.substring(0, barra);
        int fim = host.length();
        while (fim > 0 && (host.charAt(fim - 1) == '.' || host.charAt(fim - 1) == '-')) {
            fim--; // ponto final da frase não faz parte do endereço
        }
        host = host.substring(0, fim);
        int ponto = host.lastIndexOf('.');
        return ponto > 0 && FINAIS_DE_LINK.contains(host.substring(ponto + 1));
    }

    /** Pontos de UM link. Cada tipo de problema só entra uma vez nos motivos (e nos pontos). */
    static int analisarLink(String link, Set<String> motivos) {
        boolean semCadeado = link.startsWith("http://");
        String resto = link.replaceFirst("^https?://", "");
        int fim = indiceFim(resto);
        String autoridade = resto.substring(0, fim);
        int pontos = 0;

        if (autoridade.contains("@")) {
            pontos += novo(motivos, 3, "Link com \"@\": o site de verdade é o que vem DEPOIS do @.");
            autoridade = autoridade.substring(autoridade.lastIndexOf('@') + 1);
        }
        String host = autoridade.replaceFirst(":\\d+$", "").replaceFirst("^www\\.", "");

        if (IP.matcher(host).matches()) {
            pontos += novo(motivos, 3, "Link com números (IP) no lugar do nome do site.");
        }
        // xn-- = punycode; letra fora do ASCII (ex.: "а" russo no lugar do "a") = mesmo truque
        if (host.contains("xn--") || host.chars().anyMatch(c -> c > 127)) {
            pontos += novo(motivos, 3, "Endereço com letra disfarçada (punycode), imitando outro site.");
        }
        if (ENCURTADORES.contains(host)) {
            pontos += novo(motivos, 2, "Link encurtado: esconde para onde você vai.");
        }
        if (imitaMarca(host)) {
            pontos += novo(motivos, 3, "Endereço imita banco, loja ou governo, mas não é o site oficial.");
        }
        if (GUARDA_ARQUIVOS.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d))) {
            pontos += novo(motivos, 1, "Link para baixar arquivo de um site de armazenamento: confira antes de abrir.");
        }
        String tld = host.substring(host.lastIndexOf('.') + 1);
        if (TLD_SUSPEITO.contains(tld)) {
            pontos += novo(motivos, 1, "Final de endereço muito usado em golpe (." + tld + ").");
        }
        if (semCadeado) {
            pontos += novo(motivos, 1, "Link sem cadeado (http, sem o \"s\").");
        }
        return pontos;
    }

    static boolean imitaMarca(String host) {
        for (Map.Entry<String, List<String>> e : MARCAS.entrySet()) {
            if (!host.contains(e.getKey())) {
                continue;
            }
            boolean oficial = e.getValue().stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
            if (!oficial) {
                return true;
            }
        }
        return false;
    }

    private static int novo(Set<String> motivos, int pontos, String motivo) {
        return motivos.add(motivo) ? pontos : 0;
    }

    private static int indiceFim(String resto) {
        int fim = resto.length();
        for (char c : new char[] {'/', '?', '#'}) {
            int i = resto.indexOf(c);
            if (i >= 0 && i < fim) {
                fim = i;
            }
        }
        return fim;
    }

    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    static String conselho(String nivel) {
        return switch (nivel) {
            case ALTO -> "Não clique, não pague e não passe código. Confirme ligando para a pessoa ou o banco pelo número oficial.";
            case SUSPEITO -> "Desconfie. Confirme por outro caminho antes de clicar ou pagar.";
            default -> "Não vi sinais fortes de golpe. Mesmo assim, nunca passe senha ou código.";
        };
    }
}
