package com.leetsync.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS Configuration for LeetSync Backend.
 *
 * Allows requests from:
 * - LeetSync Chrome Extension
 * - Local development frontend
 * - Local development environment
 */
@Slf4j
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        log.info("Configuring CORS for LeetSync Backend");

        registry.addMapping("/api/**")

                // Use allowedOriginPatterns because Chrome extension
                // IDs are different between installations/builds.
                .allowedOriginPatterns(
                        "chrome-extension://*",
                        "http://localhost:*",
                        "http://127.0.0.1:*"
                )

                // HTTP methods supported by LeetSync APIs
                .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )

                // Allow required request headers
                .allowedHeaders("*")

                // Headers that browser JavaScript is allowed to read
                .exposedHeaders(
                        "Content-Type",
                        "Authorization"
                )

                // LeetSync does not use browser cookies/session
                // authentication. GitHub authentication stays
                // securely on the backend.
                .allowCredentials(false)

                // Cache browser preflight requests for 1 hour
                .maxAge(3600);

        log.info("CORS configuration completed successfully");
    }
}