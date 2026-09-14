package com.leetsync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for GitHub API integration.
 * Holds common settings such as connection timeouts.
 * User-specific data (token, username, repository) are stored per User entity.
 */
@Component
@ConfigurationProperties(prefix = "github")
@Getter
@Setter
public class GitHubProperties {

    // Optional base URL (default will be used if omitted)
    private String baseUrl = "https://api.github.com";
    // Connection and read timeouts (milliseconds)
    private int connectTimeout = 5000;
    private int readTimeout = 30000;
}
