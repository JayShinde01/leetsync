package com.leetsync.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.leetsync.dto.AuthResponse;
import com.leetsync.dto.UserResponse;
import com.leetsync.entity.User;
import com.leetsync.entity.UserSettings;
import com.leetsync.repository.UserRepository;
import com.leetsync.repository.UserSettingsRepository;
import com.leetsync.repository.SubmissionHistoryRepository;
import com.leetsync.security.JwtService;
import com.leetsync.security.TokenEncryptionService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication service for V3 multi-user LeetSync.
 * 
 * Handles:
 * - GitHub OAuth login flow
 * - User creation/update
 * - JWT token generation
 * - User profile retrieval
 * 
 * Encrypts GitHub OAuth tokens before storing in database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SubmissionHistoryRepository submissionHistoryRepository;
    private final JwtService jwtService;
    private final TokenEncryptionService tokenEncryptionService;
    
    @Value("${github.client-id}")
    private String githubClientId;
    
    @Value("${github.client-secret}")
    private String githubClientSecret;
    
    @Value("${github.oauth-redirect-uri}")
    private String githubRedirectUri;
    
    /**
     * Exchanges GitHub OAuth authorization code for access token and creates/updates user.
     * 
     * @param code GitHub OAuth authorization code
     * @return AuthResponse with JWT token
     */
    @Transactional
    public AuthResponse handleGitHubCallback(String code) {
        log.info("Processing GitHub OAuth callback");
        
        try {
            // Exchange code for access token
            String accessToken = exchangeCodeForToken(code);
            
            // Fetch GitHub user information
            GitHubUserInfo githubUser = fetchGitHubUserInfo(accessToken);
            
            // Create or update user in database
            User user = createOrUpdateUser(githubUser, accessToken);
            
            // Generate JWT token for application authentication
            String jwtToken = jwtService.generateToken(user);
            
            // Build response
            UserResponse userResponse = mapUserToResponse(user);
            AuthResponse authResponse = AuthResponse.builder()
                .success(true)
                .message("Successfully authenticated with GitHub")
                .token(jwtToken)
                .user(userResponse)
                .build();
            
            log.info("GitHub OAuth successful for user: {}", githubUser.login);
            return authResponse;
            
        } catch (Exception e) {
            log.error("GitHub OAuth callback failed", e);
            return AuthResponse.builder()
                .success(false)
                .message("GitHub authentication failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Exchanges GitHub OAuth authorization code for access token.
     */
    private String exchangeCodeForToken(String code) {

        log.debug("Exchanging GitHub authorization code for access token");

        RestClient restClient = RestClient.create();

        java.util.Map<String, String> requestBody = new java.util.HashMap<>();

        requestBody.put("client_id", githubClientId);
        requestBody.put("client_secret", githubClientSecret);
        requestBody.put("code", code);
        requestBody.put("redirect_uri", githubRedirectUri);

        GitHubTokenResponse tokenResponse = restClient.post()
            .uri("https://github.com/login/oauth/access_token")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .accept(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(GitHubTokenResponse.class);

        if (tokenResponse == null || tokenResponse.access_token == null) {
            throw new RuntimeException("Failed to obtain GitHub access token");
        }

        return tokenResponse.access_token;
    }
    /**
     * Fetches GitHub user information using OAuth access token.
     */
    private GitHubUserInfo fetchGitHubUserInfo(String accessToken) {
        log.debug("Fetching GitHub user information");
        
        RestClient restClient = RestClient.create();
        
        GitHubUserInfo userInfo = restClient.get()
            .uri("https://api.github.com/user")
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .body(GitHubUserInfo.class);
        
        if (userInfo == null || userInfo.id == null) {
            throw new RuntimeException("Failed to fetch GitHub user information");
        }
        
        return userInfo;
    }
    
    /**
     * Creates a new user or updates an existing one.
     * Encrypts GitHub OAuth token before storing.
     */
    @Transactional
    private User createOrUpdateUser(GitHubUserInfo githubUser, String githubAccessToken) {
        log.debug("Creating or updating user: {}", githubUser.login);
        
        // Encrypt the GitHub access token
        String encryptedToken = tokenEncryptionService.encrypt(githubAccessToken);
        
        // Check if user already exists
        Optional<User> existingUser = userRepository.findByGithubId(githubUser.id);
        
        User user;
        if (existingUser.isPresent()) {
            // Update existing user
            user = existingUser.get();
            user.setEncryptedGithubAccessToken(encryptedToken);
            user.setGithubConnected(true);
            user.setEmail(githubUser.email);
            user.setAvatarUrl(githubUser.avatar_url);
            log.info("Updated existing user: {}", githubUser.login);
        } else {
            // Create new user
            user = User.builder()
                .githubId(githubUser.id)
                .githubUsername(githubUser.login)
                .email(githubUser.email)
                .avatarUrl(githubUser.avatar_url)
                .encryptedGithubAccessToken(encryptedToken)
                .tokenType("bearer")
                .githubConnected(true)
                .build();
            
            log.info("Created new user: {}", githubUser.login);
        }
        
        user = userRepository.save(user);
        
        // Ensure user settings exist
        Optional<UserSettings> existingSettings = userSettingsRepository.findByUserId(user.getId());
        if (existingSettings.isEmpty()) {
            UserSettings settings = UserSettings.builder()
                .user(user)
                .repositoryName("leetcode")
                .defaultBranch("main")
                .autoSyncEnabled(true)
                .build();
            userSettingsRepository.save(settings);
            log.info("Created default user settings for user: {}", user.getGithubUsername());
        }
        
        return user;
    }
    
    /**
     * Retrieves user profile by user ID.
     */
    @Transactional
    public UserResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return mapUserToResponse(user);
    }
    
    /**
     * Maps User entity to UserResponse DTO.
     */
    private UserResponse mapUserToResponse(User user) {
        UserSettings settings = userSettingsRepository.findByUserId(user.getId())
            .orElse(null);
        
        long totalSubmissions = submissionHistoryRepository.countByUserId(user.getId());
        
        return UserResponse.builder()
            .id(user.getId())
            .githubUsername(user.getGithubUsername())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .githubConnected(user.getGithubConnected())
            .repositoryName(settings != null ? settings.getRepositoryName() : "leetcode")
            .totalSubmissions(totalSubmissions)
            .build();
    }
    
    /**
     * GitHub OAuth token response from GitHub API.
     */
    public static class GitHubTokenResponse {
        public String access_token;
        public String token_type;
        public String scope;
    }
    
    /**
     * GitHub user information from GitHub API.
     */
    public static class GitHubUserInfo {
        public Long id;
        public String login;
        public String email;
        public String avatar_url;
        public String name;
        public String bio;
    }
}
