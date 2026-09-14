package com.leetsync.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User settings for V3 LeetSync.
 * Stores per-user configuration like repository name and sync preferences.
 * 
 * One-to-one relationship with User.
 */
@Entity
@Table(name = "user_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Reference to the user who owns these settings.
     */
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_user_settings_user"))
    private User user;
    
    /**
     * GitHub repository name (e.g., "leetcode").
     * Default: "leetcode"
     * User can configure this to use a different repository.
     */
    @Column(nullable = false)
    @Builder.Default
    private String repositoryName = "leetcode";
    
    /**
     * Default branch for the repository (e.g., "main", "master").
     * Currently defaults to "main" (GitHub's default).
     */
    @Column(nullable = false)
    @Builder.Default
    private String defaultBranch = "main";
    
    /**
     * Whether automatic sync is enabled for this user.
     * When true, accepted LeetCode submissions are automatically synced.
     * When false, user must manually trigger sync.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean autoSyncEnabled = true;
    
    /**
     * Timestamp when these settings were created.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when these settings were last updated.
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
