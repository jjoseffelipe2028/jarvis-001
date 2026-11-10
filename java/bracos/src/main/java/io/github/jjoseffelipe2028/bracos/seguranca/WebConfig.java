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

    public WebConfig(@Value("${jarvis.token:}") String token, ObjectProvider<SessoesDaCentral> sessoesCentral,
                     ObjectProvider<QuadroDeEventos> quadroEventos) {
        this.token = token;
        this.sessoesCentral = sessoesCentral;
        this.quadroEventos = quadroEventos;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Ticket 017: só ANOTA o resultado no fim (não decide nada). Vem antes dos porteiros
        // para que um pedido recusado também vire evento.
        quadroEventos.ifAvailable(q -> registry.addInterceptor(new EventosInterceptor(q)).addPathPatterns("/acoes/**"));
        // Ordem importa: primeiro confere o Host (DNS rebinding), depois o token
        registry.addInterceptor(new HostInterceptor()).addPathPatterns("/acoes/**");
        registry.addInterceptor(new TokenInterceptor(token)).addPathPatterns("/acoes/**");
        // Ticket 015: a Central tem porteiro próprio (crachá), também atrás do filtro de Host
        registry.addInterceptor(new HostInterceptor()).addPathPatterns("/central/**");
        sessoesCentral.ifAvailable(s -> registry.addInterceptor(new CentralInterceptor(s)).addPathPatterns("/central/**"));
    }
}
