package com.leetsync.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import com.leetsync.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for generating and validating JWT (JSON Web Tokens) for application authentication.
 * 
 * JWT is used for authenticating requests from the Chrome Extension.
 * This is SEPARATE from the GitHub OAuth access token.
 * 
 * JWT contains user information and is stateless - no session storage needed.
 * Chrome Extension stores only this JWT, never the GitHub OAuth access token.
 */
@Slf4j
@Service
public class JwtService {
    
    private final String jwtSecret;
    private final long jwtExpiration;
    
    public JwtService(
        @Value("${jwt.secret}") String jwtSecret,
        @Value("${jwt.expiration}") long jwtExpiration
    ) {
        this.jwtSecret = jwtSecret;
        this.jwtExpiration = jwtExpiration;
        
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("CRITICAL: JWT secret is not configured. Set JWT_SECRET environment variable.");
            throw new IllegalArgumentException("JWT secret must be configured");
        }
        
        if (jwtSecret.length() < 32) {
            log.warn("JWT secret is shorter than recommended (32 characters). Consider using a longer key.");
        }
    }
    
    /**
     * Generates a JWT token for a user.
     * Token includes user ID and GitHub username.
     * 
     * @param user The user to generate token for
     * @return JWT token string
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("githubUsername", user.getGithubUsername());
        claims.put("email", user.getEmail());
        
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getId().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        
        log.debug("JWT token generated for user: {}", user.getGithubUsername());
        return token;
    }

    /**
     * Validate a JWT token.
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract the user ID (subject) from a JWT token.
     */
    public Long extractUserId(String token) {
        Claims claims = ((JwtParserBuilder) Jwts.builder())
            .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
            .build()
            .parseClaimsJws(token)
            .getBody();
        return Long.valueOf(claims.getSubject());
    }
}
