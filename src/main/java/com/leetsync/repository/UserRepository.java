package com.leetsync.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.leetsync.entity.User;

/**
 * Spring Data JPA repository for User entity.
 * Provides database access for user management and GitHub OAuth operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find a user by their GitHub ID.
     * GitHub ID is the unique identifier from GitHub OAuth.
     */
    Optional<User> findByGithubId(Long githubId);
    
    /**
     * Find a user by their GitHub username.
     * Useful for checking if a user already exists or for profile lookups.
     */
    Optional<User> findByGithubUsername(String githubUsername);
}
