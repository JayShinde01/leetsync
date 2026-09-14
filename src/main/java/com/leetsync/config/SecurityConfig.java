package com.leetsync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.leetsync.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security configuration for V3 multi-user LeetSync.
 *
 * Configuration:
 * - Stateless JWT authentication
 * - CORS support for Chrome Extension
 * - Public GitHub OAuth endpoints
 * - Protected application endpoints
 * - Disabled CSRF, form login and HTTP Basic
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configure Spring Security.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        log.info(
            "Configuring Spring Security for V3 multi-user authentication"
        );

        http
            // Stateless API → CSRF is not required
            .csrf(csrf -> csrf.disable())

            // Disable Spring's default login page
            .formLogin(form -> form.disable())

            // Disable HTTP Basic authentication
            .httpBasic(basic -> basic.disable())

            // Enable CORS
            .cors(Customizer.withDefaults())

            // JWT authentication → no server-side session
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            // Endpoint authorization
            .authorizeHttpRequests(auth -> auth

                // Public health endpoint
                .requestMatchers("/api/health")
                .permitAll()

                // Public GitHub OAuth endpoints
                .requestMatchers("/api/auth/github")
                .permitAll()

                .requestMatchers("/api/auth/github/callback")
                .permitAll()

                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/api/**")
                .permitAll()

                // Swagger / OpenAPI
                .requestMatchers("/swagger-ui.html")
                .permitAll()

                .requestMatchers("/swagger-ui/**")
                .permitAll()

                .requestMatchers("/v3/api-docs/**")
                .permitAll()

                // Authenticated endpoints
                .requestMatchers("/api/auth/me")
                .authenticated()

                .requestMatchers("/api/auth/logout")
                .authenticated()

                .requestMatchers("/api/submissions/**")
                .authenticated()

                .requestMatchers("/api/settings/**")
                .authenticated()

                // Everything else requires authentication
                .anyRequest()
                .authenticated()
            )

            // Run JWT filter before Spring's authentication filter
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    /**
     * Configure CORS for:
     * - Chrome Extension
     * - Local development
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        log.info(
            "Configuring CORS for Chrome Extension and local development"
        );

        CorsConfiguration configuration = new CorsConfiguration();

        /*
         * Chrome Extension.
         *
         * The actual extension ID can change during development,
         * so we allow the chrome-extension origin pattern.
         */
        configuration.addAllowedOriginPattern(
            "chrome-extension://*"
        );

        // Local development
        configuration.addAllowedOriginPattern(
            "http://localhost:*"
        );

        configuration.addAllowedOriginPattern(
            "http://127.0.0.1:*"
        );

        // HTTP methods
        configuration.addAllowedMethod("GET");
        configuration.addAllowedMethod("POST");
        configuration.addAllowedMethod("PUT");
        configuration.addAllowedMethod("DELETE");
        configuration.addAllowedMethod("OPTIONS");

        // Request headers
        configuration.addAllowedHeader("*");

        // Response headers
        configuration.addExposedHeader("Authorization");
        configuration.addExposedHeader("Content-Type");

        /*
         * JWT is sent through Authorization header.
         * We are not using authentication cookies.
         */
        configuration.setAllowCredentials(false);

        // Cache browser preflight requests
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            configuration
        );

        return source;
    }

    /**
     * Password encoder.
     *
     * Currently GitHub OAuth is used instead of passwords,
     * but this bean can be useful if local authentication
     * is introduced in the future.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}