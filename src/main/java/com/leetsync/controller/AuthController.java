package com.leetsync.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leetsync.dto.AuthResponse;
import com.leetsync.dto.UserResponse;
import com.leetsync.entity.User;
import com.leetsync.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication controller for V3 multi-user LeetSync.
 * 
 * Handles:
 * - GitHub OAuth login initiation
 * - GitHub OAuth callback processing
 * - User profile retrieval
 * - Logout (token invalidation on client side)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "GitHub OAuth and user authentication endpoints")
public class AuthController {
    
    private final AuthService authService;
    @Value("${github.client-id}")
    private String githubClientId;
    @Value("${github.oauth-redirect-uri}")
    private String githubRedirectUri;
    
    /**
     * GitHub OAuth login initiation.
     * 
     * Redirects to GitHub authorization endpoint.
     * Client should navigate to the URL returned by this endpoint.
     * 
     * @return Redirect URL to GitHub OAuth
     */
    @GetMapping("/github")
    @Operation(summary = "Initiate GitHub login", description = "Returns URL for GitHub OAuth authorization")
    @ApiResponse(responseCode = "200", description = "GitHub login URL",
        content = @Content(schema = @Schema(example = "{\"url\": \"https://github.com/login/oauth/authorize?...\"}")))
    public ResponseEntity<?> initiateGitHubLogin() {
        log.info("GitHub login initiated");
        
        // GitHub OAuth authorization URL
        String githubAuthUrl = String.format(
            "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=repo",
            githubClientId,
            githubRedirectUri
        );
        
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{
            put("url", githubAuthUrl);
        }});
    }
    
    /**
     * GitHub OAuth callback endpoint.
     * 
     * Handles the callback from GitHub after user authorization.
     * Exchanges authorization code for access token and creates/updates user.
     * 
     * @param code GitHub authorization code
     * @return AuthResponse with JWT token
     */
    @GetMapping("/github/callback")
    @Operation(summary = "GitHub OAuth callback", description = "Processes GitHub authorization code and returns JWT token")
    @ApiResponse(responseCode = "200", description = "Authentication successful",
        content = @Content(schema = @Schema(example = 
            "{\"success\": true, \"message\": \"...\", \"token\": \"eyJhbGc...\", \"user\": {...}}")))
    @ApiResponse(responseCode = "400", description = "OAuth failed")
    public ResponseEntity<AuthResponse> handleGitHubCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String error
    ) {
        // Handle OAuth errors from GitHub
        if (error != null) {
            log.warn("GitHub OAuth error: {}", error);
            return ResponseEntity.badRequest().body(
                AuthResponse.builder()
                    .success(false)
                    .message("GitHub authorization failed: " + error)
                    .build()
            );
        }
        
        // Check if authorization code is present
        if (code == null || code.isBlank()) {
            log.warn("Missing authorization code in GitHub callback");
            return ResponseEntity.badRequest().body(
                AuthResponse.builder()
                    .success(false)
                    .message("Authorization code is required")
                    .build()
            );
        }
        
        // Process the callback
        AuthResponse authResponse = authService.handleGitHubCallback(code);
        
        if (authResponse.isSuccess()) {
            log.info("GitHub authentication successful");
            return ResponseEntity.ok(authResponse);
        } else {
            log.warn("GitHub authentication failed");
            return ResponseEntity.badRequest().body(authResponse);
        }
    }
    
    /**
     * Get current authenticated user profile.
     * 
     * Requires JWT authentication.
     * 
     * @param authentication Spring Security authentication
     * @return UserResponse with profile information
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns profile of authenticated user")
    @ApiResponse(responseCode = "200", description = "User profile",
        content = @Content(schema = @Schema(example = 
            "{\"id\": 1, \"githubUsername\": \"john_doe\", \"githubConnected\": true, ...}")))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated user attempted to access /api/auth/me");
            return ResponseEntity.status(401).body(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("success", false);
                    put("message", "Not authenticated");
                }}
            );
        }
        
        User user = (User) authentication.getPrincipal();
        log.debug("Retrieving profile for user: {}", user.getGithubUsername());
        
        UserResponse userResponse = authService.getUserProfile(user.getId());
        
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{
            put("success", true);
            put("user", userResponse);
        }});
    }
    
    /**
     * Logout endpoint.
     * 
     * Note: JWT is stateless, so logout is handled client-side by deleting the token.
     * This endpoint provides confirmation to client.
     * 
     * @return Logout confirmation
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout current user (stateless, client deletes token)")
    @ApiResponse(responseCode = "200", description = "Logout successful")
    public ResponseEntity<?> logout(Authentication authentication) {
        if (authentication != null) {
            User user = (User) authentication.getPrincipal();
            log.info("User logout: {}", user.getGithubUsername());
        }
        
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{
            put("success", true);
            put("message", "Logged out successfully. Please delete the authentication token client-side.");
        }});
    }
}
