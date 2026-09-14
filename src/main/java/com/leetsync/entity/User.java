package com.leetsync.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User entity for V3 multi-user LeetSync.
 * Stores GitHub OAuth information and encrypted access tokens.
 * 
 * IMPORTANT: GitHub access tokens are encrypted before storage.
 * Tokens are NEVER logged, returned in API responses, or exposed to frontend.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * GitHub user ID from OAuth (unique identifier from GitHub).
     */
    @Column(nullable = false, unique = true)
    private Long githubId;
    
    /**
     * GitHub username (e.g., "john_doe").
     */
    @Column(nullable = false, unique = true)
    private String githubUsername;
    
    /**
     * GitHub user's email address (if available from OAuth).
     */
    @Column
    private String email;
    
    /**
     * GitHub user's avatar URL.
     */
    @Column
    private String avatarUrl;
    
    /**
     * Encrypted GitHub OAuth access token.
     * CRITICAL: This token is encrypted with LEETSYNC_ENCRYPTION_KEY.
     * It must NEVER be returned in API responses or logged.
     */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String encryptedGithubAccessToken;
    
    /**
     * GitHub OAuth token type (usually "bearer").
     */
    @Column
    private String tokenType;
    
    /**
     * GitHub account connection status.
     * Helps track if the user is still connected to GitHub.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean githubConnected = true;
    
    /**
     * Timestamp when the user was created in LeetSync.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when the user record was last updated.
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
