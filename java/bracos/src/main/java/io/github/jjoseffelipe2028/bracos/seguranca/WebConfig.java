package io.github.jjoseffelipe2028.bracos.seguranca;

import io.github.jjoseffelipe2028.bracos.central.CentralInterceptor;
import io.github.jjoseffelipe2028.bracos.central.EventosInterceptor;
import io.github.jjoseffelipe2028.bracos.central.QuadroDeEventos;
import io.github.jjoseffelipe2028.bracos.central.SessoesDaCentral;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Liga o porteiro em TODO /acoes/** (bloqueio por padrão): ação nova já nasce protegida.
 * Sem @CrossOrigin e sem liberar origens. 🔒
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String token;
    private final ObjectProvider<SessoesDaCentral> sessoesCentral;
    private final ObjectProvider<QuadroDeEventos> quadroEventos;
    private final ObjectProvider<Isca> isca;

    public WebConfig(@Value("${jarvis.token:}") String token, ObjectProvider<SessoesDaCentral> sessoesCentral,
                     ObjectProvider<QuadroDeEventos> quadroEventos, ObjectProvider<Isca> isca) {
        this.token = token;
        this.sessoesCentral = sessoesCentral;
        this.quadroEventos = quadroEventos;
        this.isca = isca;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Ticket 017: só ANOTA o resultado no fim (não decide nada). Vem antes dos porteiros
        // para que um pedido recusado também vire evento.
        // Tickets 020/023: o daily e o detector de golpe pela Central também viram evento (até recusados).
        quadroEventos.ifAvailable(q -> registry.addInterceptor(new EventosInterceptor(q))
                .addPathPatterns("/acoes/**", "/central/acoes/daily", "/central/acoes/sentinela/golpe",
                        "/central/acoes/sentinela/proposta", "/central/acoes/sentinela/isca", "/central/acoes/chat",
                        "/central/acoes/estudio"));
        // Ordem importa: primeiro confere o Host (DNS rebinding), depois o token
        registry.addInterceptor(new HostInterceptor()).addPathPatterns("/acoes/**");
        // Ticket 034: o porteiro conhece a isca (se existir). Token-isca nunca abre a porta: só dispara o alarme.
        registry.addInterceptor(new TokenInterceptor(token, isca.getIfAvailable())).addPathPatterns("/acoes/**");
        // Ticket 015: a Central tem porteiro próprio (crachá), também atrás do filtro de Host
        registry.addInterceptor(new HostInterceptor()).addPathPatterns("/central/**");
        sessoesCentral.ifAvailable(s -> registry.addInterceptor(new CentralInterceptor(s)).addPathPatterns("/central/**"));
    }
}
