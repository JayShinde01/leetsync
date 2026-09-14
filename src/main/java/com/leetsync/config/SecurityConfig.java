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
 * - CORS support for Chrome Extension and LeetCode
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

                // ==============================
                // PUBLIC ENDPOINTS
                // ==============================

                // Health check
                .requestMatchers("/api/health")
                .permitAll()

                // GitHub OAuth start
                .requestMatchers("/api/auth/github")
                .permitAll()

                // GitHub OAuth callback
                .requestMatchers("/api/auth/github/callback")
                .permitAll()

                // ==============================
                // CORS PREFLIGHT
                // ==============================

                .requestMatchers(
                    HttpMethod.OPTIONS,
                    "/api/**"
                )
                .permitAll()

                // ==============================
                // SWAGGER / OPENAPI
                // ==============================

                .requestMatchers("/swagger-ui.html")
                .permitAll()

                .requestMatchers("/swagger-ui/**")
                .permitAll()

                .requestMatchers("/v3/api-docs/**")
                .permitAll()

                // ==============================
                // AUTHENTICATED ENDPOINTS
                // ==============================

                .requestMatchers("/api/auth/me")
                .authenticated()

                .requestMatchers("/api/auth/logout")
                .authenticated()

                .requestMatchers("/api/submissions/**")
                .authenticated()

                .requestMatchers("/api/settings/**")
                .authenticated()

                // ==============================
                // EVERYTHING ELSE
                // ==============================

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
     * Configure CORS.
     *
     * Allowed origins:
     * - Chrome Extension
     * - LeetCode
     * - Local development
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        log.info(
            "Configuring CORS for Chrome Extension, LeetCode and local development"
        );

        CorsConfiguration configuration = new CorsConfiguration();

        // ==============================
        // CHROME EXTENSION
        // ==============================

        /*
         * The extension ID may change during development,
         * therefore we use an origin pattern.
         */
        configuration.addAllowedOriginPattern(
            "chrome-extension://*"
        );

        // ==============================
        // LEETCODE
        // ==============================

        /*
         * content.js runs inside the LeetCode page.
         *
         * Therefore browser requests from content.js
         * can have:
         *
         * Origin: https://leetcode.com
         */
        configuration.addAllowedOriginPattern(
            "https://leetcode.com"
        );

        // ==============================
        // LOCAL DEVELOPMENT
        // ==============================

        configuration.addAllowedOriginPattern(
            "http://localhost:*"
        );

        configuration.addAllowedOriginPattern(
            "http://127.0.0.1:*"
        );

        // ==============================
        // HTTP METHODS
        // ==============================

        configuration.addAllowedMethod("GET");
        configuration.addAllowedMethod("POST");
        configuration.addAllowedMethod("PUT");
        configuration.addAllowedMethod("DELETE");
        configuration.addAllowedMethod("OPTIONS");

        // ==============================
        // REQUEST HEADERS
        // ==============================

        /*
         * Allows:
         * Authorization
         * Content-Type
         * and other required headers.
         */
        configuration.addAllowedHeader("*");

        // ==============================
        // RESPONSE HEADERS
        // ==============================

        configuration.addExposedHeader("Authorization");
        configuration.addExposedHeader("Content-Type");

        // ==============================
        // CREDENTIALS
        // ==============================

        /*
         * We use JWT through the Authorization header.
         * We are NOT using authentication cookies.
         */
        configuration.setAllowCredentials(false);

        // ==============================
        // PREFLIGHT CACHE
        // ==============================

        /*
         * Browser can cache CORS preflight
         * response for 1 hour.
         */
        configuration.setMaxAge(3600L);

        // ==============================
        // REGISTER CORS CONFIGURATION
        // ==============================

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