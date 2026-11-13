package io.github.jjoseffelipe2028.bracos.acoes.conversa;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Roteador do chat da Central (Ticket 046): "falar com o Jarvis como fala com a 001". 🎯
 *
 * Decide, com REGRAS FIXAS (sem IA), se a frase digitada é um pedido de ação da lista fechada ou só conversa.
 * É o mesmo jeito do {@code intencoes.py} da voz:
 * - A IA nunca escolhe a ação. Se a frase não bate em nenhuma regra, ela vai para a conversa normal.
 * - A resposta é só o NOME de uma ação desta lista. Quem executa é a tela, chamando a rota que já existe
 *   (com os mesmos porteiros e o mesmo crachá). Este roteador não executa nada.
 * - Pedido de mudar o PC ("apaga", "instala", "faz um pix"...) é recusado aqui e nem chega na IA.
 */
@Component
public class RoteadorDoChat {

    /** A lista fechada. Uma frase vira no máximo UMA destas. */
    public enum Acao { PENDENCIAS, GIT_STATUS, PORTAS, INTEGRIDADE, ISCA, GOLPE, PAGINA, HORA, RONDA, PROIBIDA }

    /** O resultado: a ação e, só para GOLPE (a mensagem colada) e PAGINA (o nome da página), um argumento. */
    public record Rota(Acao acao, String argumento) {
    }

    private static final String JARVIS = "^\\s*(?:jarvis[\\s,]+)?(?:por favor[\\s,]+)?";
    private static final String PEDIDO = JARVIS + "(?:e\\s+)?(?:quais|tenho|tem|mostra|mostre|lista|liste|ve|veja|fiz|esqueci|"
            + "como\\s+(?:ta|esta))\\b";

    private static final Pattern GOLPE = Pattern.compile(JARVIS + "(?:"
            + "(?:sera\\s+que\\s+)?(?:isso|essa\\s+mensagem|esta\\s+mensagem|esse\\s+link)?\\s*e\\s+golpe"
            + "|(?:verifica|verifique|confere|confira|analisa|analise|checa|cheque|ve|veja)\\s+(?:se\\s+)?"
            + "(?:essa|esta|a|esse|este|o)\\s+(?:mensagem|msg|sms|link|zap)(?:\\s+e\\s+golpe)?"
            + "|detector\\s+de\\s+golpe|golpe)\\s*[?:,\\-]\\s*");

    private static final Pattern PAGINA = Pattern.compile(JARVIS
            + "(?:abre|abra|abrir|mostra|mostre|vai\\s+(?:para|pra)|ir\\s+(?:para|pra))\\s+(?:(?:a|o)\\s+)?(?:pagina\\s+)?(?:da\\s+|de\\s+|do\\s+)?"
            + "(cyber|videos?|jogos?|inicio)[\\s.!?]*$");

    private static final Pattern RONDA = Pattern.compile(JARVIS
            + "(?:(?:faz|faca|fazer|roda|rode|rodar|chama|chame)\\s+(?:a\\s+)?ronda\\b|(?:super\\s+)?defensor[\\s.!?]*$)");

    private static final Pattern PROIBIDA = Pattern.compile(JARVIS
            + "(?:apaga|apague|deleta|delete|exclui|exclua|remove|remova|formata|formate|executa|execute|roda|rode"
            + "|instala|instale|desinstala|baixa|baixe|manda|mande|envia|envie|desliga|desligue|reinicia|reinicie"
            + "|abre|abra|fecha|feche|compra|compre|transfere|transfira|faz um pix|commita|commite|da push|faz push"
            + "|posta|poste|publica|publique|escreve|escreva|muda|mude|altera|altere)\\b");

    private static final Pattern PORTAS = Pattern.compile(JARVIS + "(?:"
            + "(?:vigia|vigie|confere|confira|verifica|verifique|checa|cheque|olha|olhe|mostra|mostre|lista|liste)"
            + "\\s+(?:as\\s+|minhas\\s+)?portas\\b"
            + "|(?:tem|tenho|ha|quais|que)\\s+(?:alguma\\s+)?portas?(?:\\s+(?:estao|esta|ta|tao))?\\s+abertas?\\b"
            + "|(?:a\\s+)?sentinela(?:[\\s,]+jarvis)?[\\s.!?]*$)");

    private static final Pattern INTEGRIDADE = Pattern.compile(JARVIS + "(?:"
            + "(?:vigia|vigie|confere|confira|verifica|verifique|checa|cheque)\\s+(?:a\\s+)?integridade\\b"
            + "|(?:alguem|algo|alguma\\s+coisa)\\s+mexeu\\s+(?:nas?|nos?)\\s+(?:minhas\\s+)?(?:travas|regras)\\b"
            + "|(?:confere|confira|verifica|verifique|checa|cheque|mostra|mostre)\\s+(?:as\\s+)?(?:minhas\\s+)?travas\\b"
            + "|(?:as\\s+)?(?:minhas\\s+)?travas\\s+(?:estao|tao|ta|continuam)\\s+(?:iguais|intactas|ok|inteiras)\\b)");

    private static final Pattern ISCA = Pattern.compile(JARVIS + "(?:"
            + "(?:confere|confira|verifica|verifique|checa|cheque|olha|olhe|mostra|mostre|vigia|vigie)\\s+(?:a\\s+)?isca\\b"
            + "|(?:alguem|algo)\\s+(?:tocou|mexeu|pegou)\\s+na\\s+isca\\b"
            + "|(?:como\\s+(?:ta|esta)\\s+)?a\\s+isca[\\s?!.]*$)");

    private static final Pattern PENDENCIAS = Pattern.compile(PEDIDO + ".*\\b(?:pendencias?|tarefas?\\s+(?:abertas?|pendentes?))\\b"
            + "|" + JARVIS + "o\\s+que\\s+(?:falta|tenho\\s+pra\\s+fazer)\\b"
            + "|" + JARVIS + "(?:minhas\\s+)?pendencias[\\s?!.]*$");

    private static final Pattern GIT = Pattern.compile(PEDIDO + ".*\\b(?:commit\\w*|git\\s*status)\\b"
            + "|" + JARVIS + "(?:status\\s+do\\s+git|git\\s*status)[\\s?!.]*$");

    private static final Pattern HORA = Pattern.compile(JARVIS + "(?:que\\s+horas?\\s+(?:sao|e)|que\\s+dia\\s+e\\s+hoje)\\b");

    /** Devolve a ação da lista, ou vazio se for só conversa (aí a frase vai para a IA). */
    public Optional<Rota> rotear(String frase) {
        if (frase == null || frase.isBlank()) {
            return Optional.empty();
        }
        String normal = semAcento(frase);

        Matcher golpe = GOLPE.matcher(normal);
        if (golpe.lookingAt()) {
            // Antes da lista proibida: a mensagem colada pode ter "faz um pix", e isso NÃO é pedido do José.
            // Corta no ORIGINAL (mesmo tamanho), para os acentos da mensagem ficarem.
            return Optional.of(new Rota(Acao.GOLPE, frase.substring(golpe.end()).strip()));
        }
        Matcher pagina = PAGINA.matcher(normal);
        if (pagina.lookingAt()) {
            String nome = pagina.group(1);
            String fixo = nome.startsWith("video") ? "videos" : nome.startsWith("jogo") ? "jogos" : nome;
            return Optional.of(new Rota(Acao.PAGINA, fixo)); // só um dos 4 nomes fixos, nunca o texto digitado
        }
        if (RONDA.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.RONDA, null));
        }
        if (PROIBIDA.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.PROIBIDA, null));
        }
        if (PORTAS.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.PORTAS, null));
        }
        if (INTEGRIDADE.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.INTEGRIDADE, null));
        }
        if (ISCA.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.ISCA, null));
        }
        if (PENDENCIAS.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.PENDENCIAS, null));
        }
        if (GIT.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.GIT_STATUS, null));
        }
        if (HORA.matcher(normal).lookingAt()) {
            return Optional.of(new Rota(Acao.HORA, null));
        }
        return Optional.empty();
    }

    /**
     * Minúsculas e sem acento, mantendo o MESMO tamanho (1 letra → 1 letra), para cortar a mensagem
     * do golpe no lugar certo do texto original.
     */
    static String semAcento(String texto) {
        StringBuilder saida = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFKD);
            char primeira = base.isEmpty() ? c : base.charAt(0);
            String minuscula = String.valueOf(primeira).toLowerCase(Locale.ROOT);
            saida.append(minuscula.isEmpty() ? primeira : minuscula.charAt(0));
        }
        return saida.toString();
    }
}
