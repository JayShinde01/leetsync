package com.leetsync.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Configuration for GitHub API RestClient.
 * Provides a pre-configured RestClient bean with authentication headers and timeouts.
 */
@Slf4j
@Configuration
public class GitHubConfig {
    
    /**
     * Creates a RestClient bean for GitHub API interactions.
     * Includes:
     * - Bearer token authentication
     * - Proper HTTP headers
     * - Error handling via exception handler
     */
    @Bean
    public RestClient githubRestClient(GitHubProperties properties) {
        
        // No global token validation; per-user tokens are handled in services.
        log.info("Initializing GitHub RestClient");
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "LeetSync-Backend/3.0")
                .build();
    }

}
