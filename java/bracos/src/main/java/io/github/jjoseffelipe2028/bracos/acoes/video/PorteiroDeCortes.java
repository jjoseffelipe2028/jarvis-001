package io.github.jjoseffelipe2028.bracos.acoes.video;

public class PorteiroDeCortes {

    static final int MIN_SEG = 13;   // decisão do José (11/11)
    static final int MAX_SEG = 180;  // 3 minutos

    public record Liberacao(boolean libera, String motivo) {
    }

    public Liberacao liberar(PedidoDeCorte p) {
        // Regra 1: o @ do amigo é obrigatório e só aceita letras, números, _ e .
        if (p.arroba() == null || !p.arroba().matches("@?[A-Za-z0-9_.]{1,30}")) {
            return new Liberacao(false, "@ invalido");
        }
        // Regra 2: o começo não pode ser negativo
        if (p.inicioSeg() < 0) {
            return new Liberacao(false, "comeco negativo");
        }
        // Regra 3: o fim tem que vir depois do começo
        if (p.fimSeg() <= p.inicioSeg()) {
            return new Liberacao(false, "fim antes do comeco");
        }
        // Regra 4: o corte não pode passar do fim do vídeo
        if (p.fimSeg() > p.duracaoVideoSeg()) {
            return new Liberacao(false, "passa do fim do video");
        }
        // Regra 5 e 6: a duração tem que ficar entre 13 s e 3 min
        int duracao = p.fimSeg() - p.inicioSeg();
        if (duracao < MIN_SEG) {
            return new Liberacao(false, "menos de " + MIN_SEG + " s");
        }
        if (duracao > MAX_SEG) {
            return new Liberacao(false, "mais de 3 min");
        }
        // Nenhuma regra barrou: libera
        return new Liberacao(true, "ok");
    }
}