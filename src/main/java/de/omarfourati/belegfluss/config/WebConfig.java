package de.omarfourati.belegfluss.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

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

    /** Not in Tomcat's mime table: without it the app manifest would go out as application/octet-stream. */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("webmanifest", MediaType.valueOf("application/manifest+json"));
    }

    /**
     * Built files under /assets/ carry a content hash in their name and never change, so browsers may keep them.
     * The HTML pages point to the current hashes and must be revalidated, or phones keep running an old version.
     * The same goes for the service worker and the manifest of the installable app.
     */
    @Bean
    OncePerRequestFilter cacheHeaders() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String path = request.getRequestURI();
                if (path.startsWith("/assets/")) {
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
                } else if (path.equals("/") || path.endsWith(".html") || path.equals("/app/")
                        || path.equals("/app/sw.js") || path.equals("/app/manifest.webmanifest")) {
                    // a cached service worker would delay every update
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
                }
                chain.doFilter(request, response);
            }
        };
    }
}
