package com.leetsync.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.leetsync.entity.UserSettings;

/**
 * Spring Data JPA repository for UserSettings entity.
 * Provides database access for user configuration and preferences.
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
    
    /**
     * Find settings by user ID.
     * Each user has exactly one settings record.
     */
    Optional<UserSettings> findByUserId(Long userId);
}
