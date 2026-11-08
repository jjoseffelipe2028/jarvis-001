package io.github.jjoseffelipe2028.bracos.seguranca;

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

    public WebConfig(@Value("${jarvis.token:}") String token) {
        this.token = token;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Ordem importa: primeiro confere o Host (DNS rebinding), depois o token
        registry.addInterceptor(new HostInterceptor()).addPathPatterns("/acoes/**");
        registry.addInterceptor(new TokenInterceptor(token)).addPathPatterns("/acoes/**");
    }
}
