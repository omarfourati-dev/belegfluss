package de.omarfourati.belegfluss.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} processing. With {@code spring.threads.virtual.enabled=true}
 * Spring Boot backs the async executor with Java 21 virtual threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
