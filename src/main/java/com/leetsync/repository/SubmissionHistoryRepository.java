package com.leetsync.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.leetsync.entity.SubmissionHistory;

/**
 * Spring Data JPA repository for SubmissionHistory entity.
 * Provides database access for submission tracking and idempotency checks.
 */
@Repository
public interface SubmissionHistoryRepository extends JpaRepository<SubmissionHistory, Long> {
    
    /**
     * Find all submissions for a specific user.
     * Used for displaying user's sync history and statistics.
     */
    List<SubmissionHistory> findByUserId(Long userId);
    
    /**
     * Find a submission by LeetCode submission ID and user.
     * Used for idempotency - check if this exact submission was already synced.
     */
    Optional<SubmissionHistory> findByUserIdAndSubmissionId(Long userId, String submissionId);
    
    /**
     * Find a submission by problem number and user.
     * Used to check if this problem was already synced by this user.
     * (Useful for detecting duplicate submissions from the same user.)
     */
    Optional<SubmissionHistory> findByUserIdAndProblemNo(Long userId, Integer problemNo);
    
    /**
     * Count submissions for a user.
     * Used for statistics - total problems solved by this user.
     */
    long countByUserId(Long userId);
}
