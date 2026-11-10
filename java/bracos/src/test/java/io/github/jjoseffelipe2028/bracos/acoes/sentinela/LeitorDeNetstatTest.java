package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Ticket 021: o leitor do netstat entende o Windows em português e não vaza nada. 🛡️ */
class LeitorDeNetstatTest {

    /** Saída parecida com a do PC do José (cabeçalho em português, IPv4 + IPv6 + UDP + conexões). */
    private static final String SAIDA = String.join("\r\n",
            "",
            "Conexões ativas",
            "",
            "  Proto  Endereço local         Endereço externo       Estado",
            "  TCP    0.0.0.0:135            0.0.0.0:0              LISTENING",
            "  TCP    0.0.0.0:445            0.0.0.0:0              ESCUTANDO",
            "  TCP    0.0.0.0:7260           0.0.0.0:0              LISTENING",
            "  TCP    0.0.0.0:49664          0.0.0.0:0              LISTENING",
            "  TCP    127.0.0.1:8080         0.0.0.0:0              LISTENING",
            "  TCP    127.0.0.1:11434        0.0.0.0:0              LISTENING",
            "  TCP    192.168.0.10:139       0.0.0.0:0              LISTENING",
            "  TCP    192.168.0.10:51515     142.250.1.1:443        ESTABLISHED",
            "  TCP    127.0.0.1:8080         127.0.0.1:60000        TIME_WAIT",
            "  TCP    [::]:135               [::]:0                 LISTENING",
            "  TCP    [::1]:5000             [::]:0                 LISTENING",
            "  UDP    0.0.0.0:5353           *:*",
            "");

    @Test
    void separaQuemEstaAbertoParaARede_deQuemFicaSoNoPc() {
        PortasService.Relatorio r = LeitorDeNetstat.ler(SAIDA, 8080);
        // Portas escutando: 135, 445, 7260, 49664, 8080, 11434, 139, 5000 (135 conta uma vez só)
        assertEquals(8, r.escutando());
        assertEquals(3, r.soNoPc(), "8080, 11434 e 5000 ficam só no 127.0.0.1/[::1]");
        assertEquals(5, r.abertasParaARede());
        assertTrue(r.jarvisSoNoPc(), "a API do Jarvis está só no 127.0.0.1");
        List<Integer> portas = r.portas().stream().map(PortasService.PortaAberta::porta).toList();
        assertEquals(List.of(139, 445, 7260, 135, 49664), portas, "ordem: atenção primeiro, depois normal");
        assertFalse(portas.contains(51515), "conexão já aberta não é porta escutando");
        assertFalse(portas.contains(5353), "UDP fica para a v2");
    }

    @Test
    void estadoTraduzidoNaoAtrapalha_eUmaPlacaDeRedeContaComoRede() {
        PortasService.Relatorio r = LeitorDeNetstat.ler(SAIDA, 8080);
        PortasService.PortaAberta smb = acha(r, 445);
        assertEquals(LeitorDeNetstat.TODA_A_REDE, smb.alcance());
        assertEquals(LeitorDeNetstat.ATENCAO, smb.risco());
        assertEquals(LeitorDeNetstat.UMA_PLACA, acha(r, 139).alcance());
        assertEquals(LeitorDeNetstat.ATENCAO, acha(r, 7260).risco(), "porta desconhecida pede atenção");
        assertEquals(LeitorDeNetstat.NORMAL, acha(r, 49664).risco(), "porta alta costuma ser RPC");
    }

    @Test
    void jarvisAbertoParaARede_viraAlertaAlto() {
        String exposto = "  TCP    0.0.0.0:8080   0.0.0.0:0   LISTENING\n  TCP    0.0.0.0:3389   0.0.0.0:0   LISTENING\n";
        PortasService.Relatorio r = LeitorDeNetstat.ler(exposto, 8080);
        assertFalse(r.jarvisSoNoPc());
        assertEquals(2, r.alertas(), "API do Jarvis e RDP abertas para a rede");
        assertTrue(acha(r, 8080).nota().contains("Jarvis"));
    }

    @Test
    void textoTortoOuVazio_naoQuebra() {
        String torto = String.join("\n",
                "TCP 0.0.0.0:99999 0.0.0.0:0 LISTENING",
                "TCP 0.0.0.0:0 0.0.0.0:0 LISTENING",
                "TCP lixo",
                "\u001b[31m TCP 0.0.0.0:22 0.0.0.0:0 LISTENING",
                "  TCP    0.0.0.0:22   0.0.0.0:0   LISTENING   ; rm -rf /");
        PortasService.Relatorio r = LeitorDeNetstat.ler(torto, 8080);
        assertEquals(0, r.escutando(), "porta fora de 1..65535 ou linha estranha é ignorada");
        assertEquals(0, LeitorDeNetstat.ler("", 8080).escutando());
    }

    @Test
    void listaTemLimite() {
        StringBuilder muitas = new StringBuilder();
        for (int p = 1000; p < 1100; p++) {
            muitas.append("  TCP    0.0.0.0:").append(p).append("   0.0.0.0:0   LISTENING\n");
        }
        PortasService.Relatorio r = LeitorDeNetstat.ler(muitas.toString(), 8080);
        assertEquals(100, r.abertasParaARede());
        assertEquals(LeitorDeNetstat.MAXIMO_NA_LISTA, r.portas().size());
    }

    private static PortasService.PortaAberta acha(PortasService.Relatorio r, int porta) {
        return r.portas().stream().filter(p -> p.porta() == porta).findFirst().orElseThrow();
    }
}
