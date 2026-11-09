package io.github.jjoseffelipe2028.bracos.acoes.video;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.jjoseffelipe2028.bracos.acoes.PastaSegura;
import io.github.jjoseffelipe2028.bracos.acoes.TextoSeguro;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Grava o relatório de retenção como nota NOVA no Obsidian (V2). 🔒
 * - Pasta só da configuração (JARVIS_PASTA_DECISOES no .env), nunca do pedido. Sem pasta → 503.
 * - Nome gerado aqui (AAAA-MM-DD-HHmmss-retencao.md): o título NUNCA vira nome de arquivo.
 * - Só cria arquivo novo (CREATE_NEW): nunca sobrescreve nota existente; não segue atalhos.
 * - Título "desarmado" com a mesma regra do daily (TextoSeguro).
 */
@Service
public class RelatorioService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter NOME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TENTATIVAS = 10;

    private static final Set<OpenOption> CRIAR_NOVO =
            Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);

    private final String pastaConfigurada;
    private final Clock relogio;

    @Autowired
    public RelatorioService(@Value("${jarvis.pastas.decisoes:}") String pastaConfigurada) {
        this(pastaConfigurada, Clock.system(FUSO));
    }

    /** Usado nos testes, para fixar a data e a hora. */
    RelatorioService(String pastaConfigurada, Clock relogio) {
        this.pastaConfigurada = pastaConfigurada;
        this.relogio = relogio;
    }

    /** @return só o NOME do arquivo criado, nunca o caminho. */
    public String gravar(RelatorioRequest pedido, Analise analise) {
        Path base = PastaSegura.base(pastaConfigurada);
        LocalDateTime agora = LocalDateTime.now(relogio);
        byte[] conteudo = montar(pedido, analise, agora).getBytes(StandardCharsets.UTF_8);

        // Dois relatórios no mesmo segundo: o segundo ganha "-2", "-3"...
        for (int i = 1; i <= TENTATIVAS; i++) {
            String nome = agora.format(NOME) + (i == 1 ? "" : "-" + i) + "-retencao.md";
            Path alvo = PastaSegura.dentroDaPasta(base, nome);
            try (SeekableByteChannel canal = Files.newByteChannel(alvo, CRIAR_NOVO)) {
                ByteBuffer bytes = ByteBuffer.wrap(conteudo);
                while (bytes.hasRemaining()) {
                    canal.write(bytes);
                }
                return nome;
            } catch (FileAlreadyExistsException e) {
                // tenta o próximo nome
            } catch (IOException e) {
                throw new UncheckedIOException("falha ao gravar relatorio", e);
            }
        }
        throw new AcaoIndisponivelException("muitos relatorios no mesmo segundo");
    }

    static String montar(RelatorioRequest p, Analise a, LocalDateTime quando) {
        String titulo = TextoSeguro.desarmar(p.titulo());
        String quedas = p.quedasSeg().isEmpty() ? "—"
                : p.quedasSeg().stream().sorted().distinct().map(AnalisadorDeRetencao::mmss)
                        .collect(Collectors.joining(", "));
        String dublagem = (a.dublagem().libera() ? "✅ liberada" : "⛔ bloqueada")
                + " (" + String.join("; ", a.dublagem().motivos()) + ")";

        StringBuilder sb = new StringBuilder();
        sb.append("# 🎬 Retenção: ").append(titulo).append("\n\n");
        sb.append(quando.format(DATA)).append(" · ").append(p.plataforma())
                .append(" · gerado pelo Jarvis (Ticket 013)\n\n");
        sb.append("## Números\n");
        sb.append("| Métrica | Valor | Meta |\n|---|---|---|\n");
        sb.append("| Duração | ").append(AnalisadorDeRetencao.mmss(p.duracaoSeg())).append(" | ≥ 3:05 no Facebook |\n");
        sb.append("| 3s→1min | ").append(pct(p.proporcao3sPara1min())).append(" | > 50% |\n");
        sb.append("| Média assistida | ").append(AnalisadorDeRetencao.mmss(p.mediaAssistidaSeg())).append(" | ≥ 1:14 |\n");
        sb.append("| CTR | ").append(pct(p.ctrPct())).append(" | (sem meta ainda) |\n");
        sb.append("| Quedas | ").append(quedas).append(" | |\n\n");
        sb.append("## Previsão de desempenho\n**").append(a.previsao()).append("** · Dublagem: ")
                .append(dublagem).append("\n\n");
        sb.append("## Erros identificados → Ajustes necessários\n");
        if (a.achados().isEmpty()) {
            sb.append("- nenhum erro encontrado ✅\n");
        }
        for (Achado achado : a.achados()) {
            sb.append("- ").append(achado.erro()).append(" → ").append(achado.ajuste()).append("\n");
        }
        return sb.toString();
    }

    private static String pct(double valor) {
        return String.format(Locale.ROOT, "%.1f%%", valor);
    }
}
