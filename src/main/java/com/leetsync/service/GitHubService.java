package com.leetsync.service;

import com.leetsync.config.GitHubProperties;
import com.leetsync.dto.SubmissionRequest;
import com.leetsync.exception.GitHubException;
import com.leetsync.util.FileExtensionUtil;
import com.leetsync.util.FileNameUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.leetsync.repository.UserSettingsRepository;
import com.leetsync.repository.UserRepository;
import com.leetsync.entity.User;
import com.leetsync.entity.UserSettings;
import com.leetsync.security.TokenEncryptionService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Service for GitHub repository and file operations.
 * Handles repository existence checks/creation, solution file sync, and SHA retrieval.
 */
@Slf4j
@Service
public class GitHubService {

    private final GitHubProperties properties;
    private final RestClient restClient;
    private final TokenEncryptionService tokenEncryptionService;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public GitHubService(GitHubProperties properties,
                         TokenEncryptionService tokenEncryptionService,
                         UserRepository userRepository,
                         UserSettingsRepository userSettingsRepository) {
        this.properties = properties;
        this.tokenEncryptionService = tokenEncryptionService;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;

        log.info("Initializing GitHubService");
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "LeetSync-Backend/2.0")
                .build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new GitHubException("User not authenticated", "GITHUB_UNAUTHORIZED", 401);
        }
        String username = authentication.getName();
        return userRepository.findByGithubUsername(username)
                .orElseThrow(() -> new GitHubException("User not found: " + username, "USER_NOT_FOUND", 404));
    }

    private String getUserToken(User user) {
        String encryptedToken = user.getEncryptedGithubAccessToken();
        if (encryptedToken == null || encryptedToken.isBlank()) {
            throw new GitHubException("GitHub token not configured for user", "TOKEN_NOT_CONFIGURED", 400);
        }
        return tokenEncryptionService.decrypt(encryptedToken);
    }

    private String getUserRepositoryName(User user) {
        return userSettingsRepository.findByUserId(user.getId())
                .map(UserSettings::getRepositoryName)
                .orElse("leetcode");
    }

    /** Checks if the configured GitHub repository exists. */
    public boolean repositoryExists() {
        try {
            User user = getCurrentUser();
            String token = getUserToken(user);
            String repo = getUserRepositoryName(user);
            log.debug("Checking repo existence for user {}: {}/{}", user.getGithubUsername(), user.getGithubUsername(), repo);
            restClient.get()
                    .uri("/repos/{owner}/{repo}", user.getGithubUsername(), repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("GitHub auth failed while checking repository");
            throw new GitHubException("GitHub authentication failed", "GITHUB_AUTH_FAILED", 401, e);
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("GitHub access forbidden while checking repository");
            throw new GitHubException("GitHub access forbidden", "GITHUB_FORBIDDEN", 403, e);
        } catch (Exception e) {
            log.error("Failed to check repository existence", e);
            throw new GitHubException("Failed to access GitHub repository: " + e.getMessage(), "GITHUB_CHECK_FAILED", 502, e);
        }
    }

    /** Creates the repository if it does not exist. */
    public void createRepository() {
        try {
            User user = getCurrentUser();
            String token = getUserToken(user);
            String repo = getUserRepositoryName(user);
            log.info("Creating GitHub repository '{}' for user {}", repo, user.getGithubUsername());
            Map<String, Object> requestBody = Map.of(
                    "name", repo,
                    "description", "Automatically synchronized LeetCode solutions",
                    "private", false,
                    "auto_init", true
            );
            restClient.post()
                    .uri("/user/repos")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Repository created successfully: {} for user {}", repo, user.getGithubUsername());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("GitHub auth failed during repository creation");
            throw new GitHubException("GitHub authentication failed during repository creation", "GITHUB_AUTH_FAILED", 401, e);
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("GitHub access forbidden during repository creation");
            throw new GitHubException("GitHub access forbidden. Check token permissions.", "GITHUB_FORBIDDEN", 403, e);
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            log.error("Repository already exists or name invalid");
            throw new GitHubException("Repository already exists or invalid name", "GITHUB_REPO_CONFLICT", 422, e);
        } catch (Exception e) {
            log.error("Failed to create repository", e);
            throw new GitHubException("Failed to create GitHub repository: " + e.getMessage(), "GITHUB_CREATE_FAILED", 502, e);
        }
    }

    /** Ensures the repository exists, creating it if absent. */
    public void ensureRepositoryExists() {
        if (!repositoryExists()) {
            createRepository();
        }
    }

    /** Creates or updates a solution file on GitHub. */
    public void createSolutionFile(SubmissionRequest request) {
        try {
            User user = getCurrentUser();
            String token = getUserToken(user);
            String repo = getUserRepositoryName(user);

            String extension = FileExtensionUtil.getExtension(request.getLanguage());
            String className = FileNameUtil.createClassName(request.getProblemTitle());
            String folderName = String.format("%04d-%s",
                    request.getProblemNo(),
                    request.getProblemTitle()
                            .toLowerCase()
                            .replaceAll("[^a-z0-9]+", "-")
                            .replaceAll("^-|-$", ""));
            String path = folderName + "/" + className + extension;

            String encodedContent = Base64.getEncoder()
                    .encodeToString(request.getCode().getBytes(StandardCharsets.UTF_8));

            String existingSha = getExistingFileSha(path);
            Map<String, Object> requestBody;
            if (existingSha == null) {
                requestBody = Map.of(
                        "message", "Solved LeetCode #" + request.getProblemNo() + " - " + request.getProblemTitle(),
                        "content", encodedContent
                );
            } else {
                requestBody = Map.of(
                        "message", "Updated LeetCode #" + request.getProblemNo() + " - " + request.getProblemTitle(),
                        "content", encodedContent,
                        "sha", existingSha
                );
            }

            restClient.put()
                    .uri("/repos/{owner}/{repo}/contents/{path}", user.getGithubUsername(), repo, path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Solution {} successfully: {} for user {}",
                    existingSha == null ? "created" : "updated",
                    path,
                    user.getGithubUsername());
        } catch (HttpClientErrorException e) {
            log.error("GitHub API error while creating solution file: {} {}", e.getStatusCode(), e.getMessage());
            throw new GitHubException("Failed to sync solution file to GitHub: " + e.getMessage(), "GITHUB_FILE_OPERATION_FAILED", e.getStatusCode().value(), e);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid submission parameter: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while creating solution file", e);
            throw new GitHubException("Failed to sync solution file to GitHub: " + e.getMessage(), "GITHUB_FILE_OPERATION_FAILED", 502, e);
        }
    }

    /** Retrieves SHA of an existing file; returns null if not found. */
    public String getExistingFileSha(String path) {
        User user = getCurrentUser();
        String token = getUserToken(user);
        String repo = getUserRepositoryName(user);
        try {
            Map response = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", user.getGithubUsername(), repo, path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("sha") == null) {
                log.error("GitHub response missing SHA for: {}", path);
                throw new GitHubException("GitHub response missing SHA", "GITHUB_INVALID_RESPONSE", 502);
            }
            return response.get("sha").toString();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("GitHub auth failed while checking SHA for user {}", user.getGithubUsername());
            throw new GitHubException("GitHub authentication failed", "GITHUB_AUTH_FAILED", 401, e);
        } catch (HttpClientErrorException e) {
            log.error("GitHub API error while checking SHA for user {}: {} {}", user.getGithubUsername(), e.getStatusCode(), e.getMessage());
            throw new GitHubException("Failed to check existing file: " + e.getMessage(), "GITHUB_CHECK_FAILED", e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("Unexpected error while checking SHA for user {}", user.getGithubUsername(), e);
            throw new GitHubException("Failed to check existing file: " + e.getMessage(), "GITHUB_CHECK_FAILED", 502, e);
        }
    }
}
