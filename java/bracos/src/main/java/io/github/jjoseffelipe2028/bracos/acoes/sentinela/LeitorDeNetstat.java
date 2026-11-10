package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê a saída do "netstat -a -n" e diz quais portas TCP estão ESCUTANDO e para quem (Ticket 021). 🛡️
 * - Função pura: só texto entra, só números e notas fixas saem. Fácil de testar.
 * - Não depende do idioma do Windows: "escutando" = endereço externo 0.0.0.0:0 ou [::]:0
 *   (o estado LISTENING pode vir traduzido, então não usamos ele).
 * - Endereço de quem está conectado, PID ou nome de programa NUNCA saem daqui.
 */
final class LeitorDeNetstat {

    /** Ex.: "  TCP    0.0.0.0:135    0.0.0.0:0    LISTENING". O último campo (estado) é ignorado. */
    private static final Pattern LINHA = Pattern.compile("^\\s*TCP\\s+(\\S+):(\\d{1,5})\\s+(\\S+)(?:\\s+\\S+)?\\s*$");

    static final int MAXIMO_NA_LISTA = 40;

    // Alcance, do mais fechado para o mais aberto
    static final String SO_NO_PC = "só no PC";
    static final String UMA_PLACA = "uma placa de rede";
    static final String TODA_A_REDE = "toda a rede";

    static final String ALTO = "alto";
    static final String ATENCAO = "atencao";
    static final String NORMAL = "normal";

    /** Portas conhecidas: risco e uma nota curta para estudo (Cyber). Texto fixo, nada vem de fora. */
    private static final Map<Integer, String[]> CONHECIDAS = Map.ofEntries(
            Map.entry(21, new String[] {ALTO, "FTP: a senha viaja sem criptografia"}),
            Map.entry(22, new String[] {ATENCAO, "SSH: acesso remoto ao PC"}),
            Map.entry(23, new String[] {ALTO, "Telnet: acesso remoto sem criptografia"}),
            Map.entry(135, new String[] {NORMAL, "RPC do Windows (padrão do sistema)"}),
            Map.entry(139, new String[] {ATENCAO, "NetBIOS: compartilhamento antigo do Windows"}),
            Map.entry(445, new String[] {ATENCAO, "SMB: compartilhamento de arquivos (alvo do WannaCry)"}),
            Map.entry(1433, new String[] {ATENCAO, "SQL Server: banco de dados"}),
            Map.entry(3306, new String[] {ATENCAO, "MySQL: banco de dados"}),
            Map.entry(3389, new String[] {ALTO, "Área de Trabalho Remota (RDP)"}),
            Map.entry(5040, new String[] {NORMAL, "serviço do Windows (CDPSvc)"}),
            Map.entry(5357, new String[] {NORMAL, "descoberta de aparelhos do Windows (WSD)"}),
            Map.entry(5432, new String[] {ATENCAO, "PostgreSQL: banco de dados"}),
            Map.entry(5900, new String[] {ALTO, "VNC: controle remoto da tela"}),
            Map.entry(7680, new String[] {NORMAL, "Windows Update (entrega entre PCs)"}),
            Map.entry(11434, new String[] {ALTO, "Ollama: a IA local devia ficar só no PC"}),
            Map.entry(27017, new String[] {ALTO, "MongoDB: banco de dados"}),
            Map.entry(27036, new String[] {NORMAL, "Steam: transmissão de jogos na rede de casa"}));

    private LeitorDeNetstat() {
    }

    static PortasService.Relatorio ler(String saida, int portaDoJarvis) {
        Map<Integer, String> alcancePorPorta = new TreeMap<>();
        for (String linha : saida.split("\\R")) {
            Matcher m = LINHA.matcher(linha);
            if (!m.matches()) {
                continue;
            }
            String remoto = m.group(3);
            if (!remoto.equals("0.0.0.0:0") && !remoto.equals("[::]:0")) {
                continue; // conexão já aberta (ESTABLISHED, TIME_WAIT...): não é porta escutando
            }
            int porta = Integer.parseInt(m.group(2));
            if (porta < 1 || porta > 65535) {
                continue;
            }
            // A mesma porta aparece no IPv4 e no IPv6: vale o alcance mais aberto
            alcancePorPorta.merge(porta, alcance(m.group(1)), LeitorDeNetstat::maisAberto);
        }

        List<PortasService.PortaAberta> abertas = new ArrayList<>();
        int soNoPc = 0;
        for (Map.Entry<Integer, String> e : alcancePorPorta.entrySet()) {
            if (SO_NO_PC.equals(e.getValue())) {
                soNoPc++;
                continue;
            }
            String[] info = nota(e.getKey(), portaDoJarvis);
            abertas.add(new PortasService.PortaAberta(e.getKey(), e.getValue(), info[0], info[1]));
        }
        abertas.sort(Comparator.comparingInt((PortasService.PortaAberta p) -> peso(p.risco()))
                .thenComparingInt(PortasService.PortaAberta::porta));

        int alertas = (int) abertas.stream().filter(p -> ALTO.equals(p.risco())).count();
        boolean jarvisSoNoPc = abertas.stream().noneMatch(p -> p.porta() == portaDoJarvis);
        List<PortasService.PortaAberta> lista = List.copyOf(abertas.subList(0, Math.min(abertas.size(), MAXIMO_NA_LISTA)));
        return new PortasService.Relatorio(alcancePorPorta.size(), soNoPc, abertas.size(), alertas, jarvisSoNoPc, lista);
    }

    static String alcance(String ip) {
        if (ip.startsWith("127.") || ip.equals("[::1]")) {
            return SO_NO_PC;
        }
        if (ip.equals("0.0.0.0") || ip.equals("[::]")) {
            return TODA_A_REDE;
        }
        return UMA_PLACA; // ex.: 192.168.0.10: só uma placa, mas ainda é a rede
    }

    private static String maisAberto(String a, String b) {
        return nivel(a) >= nivel(b) ? a : b;
    }

    private static int nivel(String alcance) {
        return switch (alcance) {
            case TODA_A_REDE -> 2;
            case UMA_PLACA -> 1;
            default -> 0;
        };
    }

    private static int peso(String risco) {
        return switch (risco) {
            case ALTO -> 0;
            case ATENCAO -> 1;
            default -> 2;
        };
    }

    static String[] nota(int porta, int portaDoJarvis) {
        if (porta == portaDoJarvis) {
            return new String[] {ALTO, "a API do Jarvis devia ficar SÓ no 127.0.0.1"};
        }
        String[] conhecida = CONHECIDAS.get(porta);
        if (conhecida != null) {
            return conhecida;
        }
        if (porta >= 49152) {
            return new String[] {NORMAL, "porta alta: costuma ser serviço do Windows (RPC)"};
        }
        return new String[] {ATENCAO, "desconhecida: vale descobrir qual programa abriu"};
    }
}
