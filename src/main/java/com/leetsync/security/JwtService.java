package com.leetsync.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.leetsync.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;

/**
 * Service for generating and validating JWT tokens for LeetSync.
 *
 * JWT is used for authenticating requests from the Chrome Extension.
 *
 * IMPORTANT:
 * - JWT is different from the GitHub OAuth access token.
 * - The Chrome Extension stores only the JWT.
 * - The GitHub OAuth token is encrypted and stored in the database.
 */
@Slf4j
@Service
public class JwtService {

    private final String jwtSecret;
    private final long jwtExpiration;
    private final SecretKey signingKey;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration}") long jwtExpiration) {

        this.jwtSecret = jwtSecret;
        this.jwtExpiration = jwtExpiration;

        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error(
                "CRITICAL: JWT secret is not configured. " +
                "Set JWT_SECRET environment variable."
            );
            throw new IllegalArgumentException(
                "JWT secret must be configured"
            );
        }

        if (jwtSecret.length() < 32) {
            log.warn(
                "JWT secret is shorter than recommended. " +
                "Use at least 32 characters."
            );
        }

        /*
         * Create the signing key once instead of creating it
         * every time a token is generated or validated.
         */
        this.signingKey = Keys.hmacShaKeyFor(
            jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Generates a JWT token for a user.
     *
     * JWT contains:
     * - userId
     * - githubUsername
     * - email
     * - subject = user ID
     * - issued time
     * - expiration time
     *
     * @param user authenticated LeetSync user
     * @return JWT token
     */
    public String generateToken(User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                "User and user ID must not be null"
            );
        }

        Map<String, Object> claims = new HashMap<>();

        claims.put("userId", user.getId());
        claims.put("githubUsername", user.getGithubUsername());

        if (user.getEmail() != null) {
            claims.put("email", user.getEmail());
        }

        Date issuedAt = new Date();
        Date expiration = new Date(
            issuedAt.getTime() + jwtExpiration
        );

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getId().toString())
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(signingKey)
                .compact();

        log.debug(
            "JWT token generated for user: {}",
            user.getGithubUsername()
        );

        return token;
    }

    /**
     * Validates a JWT token.
     *
     * @param token JWT token
     * @return true if valid, otherwise false
     */
    public boolean isTokenValid(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        try {

            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);

            return true;

        } catch (Exception e) {

            log.warn(
                "Invalid JWT token: {}",
                e.getMessage()
            );

            return false;
        }
    }

    /**
     * Extracts the user ID from the JWT subject.
     *
     * @param token JWT token
     * @return user ID
     */
    public Long extractUserId(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                "JWT token must not be null or blank"
            );
        }

        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.valueOf(claims.getSubject());
    }

    /**
     * Extracts all claims from a JWT token.
     *
     * Useful if we need additional information later.
     */
    public Claims extractClaims(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                "JWT token must not be null or blank"
            );
        }

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}