package com.leetsync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user profile information.
 * Does NOT expose GitHub OAuth token or encrypted data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    
    private Long id;
    private String githubUsername;
    private String email;
    private String avatarUrl;
    private Boolean githubConnected;
    private String repositoryName;
    private Long totalSubmissions;
}
