package de.omarfourati.belegfluss.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The static landing page is served at "/" (Spring Boot's welcome page), the Vue app at "/app/".
 * Spring only resolves index.html for the root, so the app's directory URL is forwarded explicitly.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app", "/app/");
        registry.addViewController("/app/").setViewName("forward:/app/index.html");
    }
}
