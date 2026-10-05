package ai.luumo.fractalstatus;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration. Enables CORS for the SPA front-end against {@code /api/**}
 * (including the SSE log stream).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FractalstatusProperties properties;

    public WebConfig(FractalstatusProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.getCors().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
