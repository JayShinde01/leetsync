package com.leetsync.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Submission history for V3 LeetSync.
 * Tracks all Accepted submissions that were successfully synced to GitHub.
 * 
 * Used for:
 * - Tracking user's synced problems
 * - Duplicate prevention (idempotency)
 * - Audit trail
 * - Statistics (total synced problems, etc.)
 */
@Entity
@Table(
    name = "submission_history",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "submission_id"}, name = "uk_user_submission")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Reference to the user who submitted the solution.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_submission_history_user"))
    private User user;
    
    /**
     * LeetCode problem number (e.g., 1, 144, 232).
     */
    @Column(nullable = false)
    private Integer problemNo;
    
    /**
     * LeetCode problem title (e.g., "Two Sum", "Binary Tree Preorder Traversal").
     */
    @Column(nullable = false)
    private String problemTitle;
    
    /**
     * Programming language used (e.g., "java", "python", "javascript").
     */
    @Column(nullable = false)
    private String language;
    
    /**
     * Submission status from LeetCode (e.g., "Accepted", "Wrong Answer").
     * Only "Accepted" submissions are stored in this table.
     */
    @Column(nullable = false)
    private String status;
    
    /**
     * LeetCode submission ID (unique identifier from LeetCode).
     * Used for idempotency - same submissionId should not create duplicate GitHub uploads.
     */
    @Column(unique = true)
    private String submissionId;
    
    /**
     * Path where the solution was stored on GitHub.
     * Example: "0144-binary-tree-preorder-traversal/BinaryTreePreorderTraversal.java"
     */
    @Column(nullable = false)
    private String githubPath;
    
    /**
     * Full URL to the file on GitHub for easy access.
     * Example: "https://github.com/username/leetcode/blob/main/0144-binary-tree-preorder-traversal/BinaryTreePreorderTraversal.java"
     */
    @Column(nullable = false)
    private String githubUrl;
    
    /**
     * Timestamp when this submission was synced to GitHub.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime syncedAt;
}
