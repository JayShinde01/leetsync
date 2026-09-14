package com.leetsync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * - Stateless JWT-based authentication (no sessions)
 * - CORS support for Chrome Extension
 * - Public/Protected endpoint configuration
 * - JWT filter integration
 * - Disabled CSRF (stateless API)
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    /**
     * Configure HTTP security.
     * Enables JWT authentication and endpoint protection.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security for V3 multi-user authentication");
        
        http
            // Disable CSRF (stateless API doesn't need it)
            .csrf(csrf -> csrf.disable())
            // Disable form login and basic auth (no default login page)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            // Configure CORS
            .cors(Customizer.withDefaults())
            // Use stateless session management (no cookies)
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/auth/github").permitAll()
                .requestMatchers("/api/auth/github/callback").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/api/submissions/**").authenticated()
                .requestMatchers("/api/settings/**").authenticated()
                .anyRequest().authenticated()
            )
            // Add JWT filter before Spring's UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * Configure CORS for Chrome Extension and local development.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("Configuring CORS for Chrome Extension and local development");
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow Chrome Extension origins
        configuration.addAllowedOriginPattern("chrome-extension://*");
        
        // Allow localhost for development
        configuration.addAllowedOriginPattern("http://localhost:*");
        configuration.addAllowedOriginPattern("http://127.0.0.1:*");
        
        // Allow required methods
        configuration.addAllowedMethod("GET");
        configuration.addAllowedMethod("POST");
        configuration.addAllowedMethod("PUT");
        configuration.addAllowedMethod("DELETE");
        configuration.addAllowedMethod("OPTIONS");
        
        // Allow required headers
        configuration.addAllowedHeader("*");
        configuration.addAllowedHeader("Authorization");
        configuration.addAllowedHeader("Content-Type");
        
        // Expose response headers
        configuration.addExposedHeader("Authorization");
        configuration.addExposedHeader("Content-Type");
        
        // Don't send credentials with requests
        // (stateless JWT doesn't require cookies)
        configuration.setAllowCredentials(false);
        
        // Cache preflight response
        configuration.setMaxAge(3600L);
        
        // Register CORS configuration
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
    
    /**
     * Password encoder for user authentication (if needed in future).
     * Currently not used as we use GitHub OAuth.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
