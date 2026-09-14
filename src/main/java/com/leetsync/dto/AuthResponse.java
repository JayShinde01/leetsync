package com.leetsync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authentication endpoints.
 * Contains JWT token and basic user information.
 * Does NOT expose GitHub OAuth token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    
    private boolean success;
    private String message;
    private String token;
    private UserResponse user;
}
