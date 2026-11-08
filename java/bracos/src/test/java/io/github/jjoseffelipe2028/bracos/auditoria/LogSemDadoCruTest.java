package io.github.jjoseffelipe2028.bracos.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Lê o código-fonte e falha se algum log.* usar dado de fora sem LogSeguro.limpar(). 🔒
 * Ex. proibido: log.info("x={}", request.getHeader("X"))  → log injection.
 * Ex. permitido: log.info("x={}", LogSeguro.limpar(request.getHeader("X")))
 */
class LogSemDadoCruTest {

    private static final Path FONTES = Path.of("src/main/java");

    // Uma chamada de log inteira, mesmo quebrada em várias linhas: log.info( ... );
    private static final Pattern CHAMADA_LOG =
            Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\s*\\((.*?)\\);", Pattern.DOTALL);

    // Coisas que vêm de fora e não podem ir cruas para o log
    private static final Pattern DADO_DE_FORA = Pattern.compile(
            "\\w+\\.(getHeader|getRequestURI|getRequestURL|getQueryString|getParameter|getRemoteAddr"
                    + "|getServerName|getPathInfo|getMessage)\\s*\\(|\\.texto\\s*\\(\\)");

    // Objeto inteiro de fora passado para o log: o toString() pode incluir o texto do usuário
    private static final Pattern OBJETO_INTEIRO =
            Pattern.compile("[,(]\\s*(pedido|request|req|body|corpo)\\s*(?:[,)]|$)");

    /**
     * Devolve os trechos problemáticos encontrados num código-fonte.
     * ⚠️ Limite conhecido (achado 6): não segue variável. Ex.: String h = req.getHeader(..);
     * log.info("{}", h) passa despercebido. Este teste é uma rede, não substitui a revisão humana.
     */
    static List<String> problemas(String codigo) {
        List<String> achados = new ArrayList<>();
        Matcher log = CHAMADA_LOG.matcher(codigo);
        while (log.find()) {
            String argumentos = log.group(2);
            if (OBJETO_INTEIRO.matcher(argumentos).find()) {
                achados.add(log.group().replaceAll("\\s+", " "));
            }
            Matcher dado = DADO_DE_FORA.matcher(argumentos);
            while (dado.find()) {
                String antes = argumentos.substring(0, dado.start());
                boolean limpo = antes.stripTrailing().endsWith("LogSeguro.limpar(")
                        || antes.stripTrailing().endsWith("Cliente.de(");
                if (!limpo) {
                    achados.add(log.group().replaceAll("\\s+", " "));
                }
            }
        }
        return achados;
    }

    @Test
    void nenhumLogDoProjetoUsaDadoCru() throws IOException {
        List<String> achados = new ArrayList<>();
        int arquivosLidos;
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            List<Path> javas = arquivos.filter(p -> p.toString().endsWith(".java")).toList();
            arquivosLidos = javas.size();
            for (Path p : javas) {
                for (String achado : problemas(Files.readString(p, StandardCharsets.UTF_8))) {
                    achados.add(FONTES.relativize(p) + ": " + achado);
                }
            }
        }
        assertTrue(arquivosLidos > 5, "o teste precisa achar os arquivos do projeto");
        assertEquals(List.of(), achados, "logs com dado de fora sem LogSeguro.limpar()");
    }

    // Testes do próprio detector: se ele não pega o erro, o teste acima não vale nada

    @Test
    void detectorPegaHeaderCru() {
        assertFalse(problemas("log.warn(\"x={}\", request.getHeader(\"X\"));").isEmpty());
    }

    @Test
    void detectorPegaUriCruEmVariasLinhas() {
        assertFalse(problemas("log.info(\"a={} b={}\",\n  cliente,\n  request.getRequestURI());").isEmpty());
    }

    @Test
    void detectorPegaTextoDoPedido() {
        assertFalse(problemas("log.info(\"t={}\", pedido.texto());").isEmpty());
    }

    @Test
    void detectorPegaMensagemDeErro() {
        assertFalse(problemas("log.error(\"falhou {}\", e.getMessage());").isEmpty());
    }

    @Test
    void detectorPegaObjetoInteiro() {
        assertFalse(problemas("log.info(\"p={}\", pedido);").isEmpty());
        assertFalse(problemas("log.debug(\"r={}\", request);").isEmpty());
    }

    @Test
    void detectorAceitaDadoLimpo() {
        assertTrue(problemas("log.warn(\"x={}\", LogSeguro.limpar(request.getRequestURI()));").isEmpty());
        assertTrue(problemas("log.warn(\"c={}\", Cliente.de(request.getHeader(Cliente.CABECALHO)));").isEmpty());
        assertTrue(problemas("log.info(\"acao=hora resultado=ok\");").isEmpty());
    }
}
