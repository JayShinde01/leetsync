package com.leetsync.service;

import com.leetsync.config.GitHubProperties;
import com.leetsync.dto.SubmissionRequest;
import com.leetsync.entity.User;
import com.leetsync.entity.UserSettings;
import com.leetsync.exception.GitHubException;
import com.leetsync.repository.UserRepository;
import com.leetsync.repository.UserSettingsRepository;
import com.leetsync.security.TokenEncryptionService;
import com.leetsync.util.FileExtensionUtil;
import com.leetsync.util.FileNameUtil;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class GitHubService {

    private final GitHubProperties properties;
    private final RestClient restClient;
    private final TokenEncryptionService tokenEncryptionService;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public GitHubService(
            GitHubProperties properties,
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
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/vnd.github+json"
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "LeetSync-Backend/3.0"
                )
                .build();
    }

    // ============================================================
    // CURRENT APPLICATION USER
    // ============================================================

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new GitHubException(
                    "User not authenticated",
                    "GITHUB_UNAUTHORIZED",
                    401
            );
        }

        /*
         * JwtAuthenticationFilter places the actual User entity
         * inside Authentication.
         */
        if (authentication.getPrincipal() instanceof User user) {
            return user;
        }

        /*
         * Fallback for authentication implementations where
         * the principal is represented by username.
         */
        String username = authentication.getName();

        return userRepository
                .findByGithubUsername(username)
                .orElseThrow(() ->
                        new GitHubException(
                                "Application user not found: " + username,
                                "USER_NOT_FOUND",
                                404
                        ));
    }

    // ============================================================
    // DECRYPT GITHUB TOKEN
    // ============================================================

    private String getUserToken(User user) {

        String encryptedToken =
                user.getEncryptedGithubAccessToken();

        if (encryptedToken == null ||
                encryptedToken.isBlank()) {

            throw new GitHubException(
                    "GitHub token not configured for user",
                    "TOKEN_NOT_CONFIGURED",
                    400
            );
        }

        try {

            String token =
                    tokenEncryptionService.decrypt(encryptedToken);

            if (token == null || token.isBlank()) {

                throw new GitHubException(
                        "Decrypted GitHub token is empty",
                        "TOKEN_INVALID",
                        401
                );
            }

            return token;

        } catch (GitHubException e) {
            throw e;

        } catch (Exception e) {

            log.error(
                    "Failed to decrypt GitHub token for user {}",
                    user.getGithubUsername(),
                    e
            );

            throw new GitHubException(
                    "Failed to decrypt GitHub access token",
                    "TOKEN_DECRYPT_FAILED",
                    500,
                    e
            );
        }
    }

    // ============================================================
    // VERIFY GITHUB TOKEN
    // ============================================================

    private String getAuthenticatedGitHubUsername(String token) {

        try {

            Map<String, Object> githubUser =
                    restClient.get()
                            .uri("/user")
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token
                            )
                            .retrieve()
                            .body(Map.class);

            if (githubUser == null) {

                throw new GitHubException(
                        "GitHub returned an empty user response",
                        "GITHUB_INVALID_RESPONSE",
                        502
                );
            }

            Object login =
                    githubUser.get("login");

            if (login == null ||
                    login.toString().isBlank()) {

                throw new GitHubException(
                        "GitHub response does not contain login",
                        "GITHUB_INVALID_RESPONSE",
                        502
                );
            }

            String githubUsername =
                    login.toString();

            log.info(
                    "GitHub token authenticated successfully as {}",
                    githubUsername
            );

            return githubUsername;

        } catch (HttpClientErrorException.Unauthorized e) {

            log.error(
                    "GitHub token is invalid or revoked"
            );

            throw new GitHubException(
                    "GitHub authentication failed. Please reconnect GitHub.",
                    "GITHUB_AUTH_FAILED",
                    401,
                    e
            );

        } catch (HttpClientErrorException.Forbidden e) {

            log.error(
                    "GitHub token does not have sufficient permissions"
            );

            throw new GitHubException(
                    "GitHub access forbidden. Please reconnect GitHub and check permissions.",
                    "GITHUB_FORBIDDEN",
                    403,
                    e
            );

        } catch (HttpClientErrorException e) {

            log.error(
                    "GitHub API error while validating token: {}",
                    e.getStatusCode()
            );

            throw new GitHubException(
                    "GitHub API error while validating authentication",
                    "GITHUB_AUTH_CHECK_FAILED",
                    e.getStatusCode().value(),
                    e
            );

        } catch (GitHubException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Unexpected error while validating GitHub token",
                    e
            );

            throw new GitHubException(
                    "Failed to validate GitHub authentication",
                    "GITHUB_AUTH_CHECK_FAILED",
                    502,
                    e
            );
        }
    }

    // ============================================================
    // REPOSITORY NAME
    // ============================================================

    private String getUserRepositoryName(User user) {

        return userSettingsRepository
                .findByUserId(user.getId())
                .map(UserSettings::getRepositoryName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("leetcode");
    }

    // ============================================================
    // CHECK REPOSITORY
    // ============================================================

    public boolean repositoryExists() {

        User user = getCurrentUser();

        String token = getUserToken(user);

        /*
         * IMPORTANT:
         * Don't blindly trust githubUsername stored in our DB.
         *
         * Ask GitHub who this token actually belongs to.
         */
        String githubUsername =
                getAuthenticatedGitHubUsername(token);

        String repo =
                getUserRepositoryName(user);

        log.info(
                "Checking GitHub repository: {}/{}",
                githubUsername,
                repo
        );

        try {

            restClient.get()
                    .uri(
                            "/repos/{owner}/{repo}",
                            githubUsername,
                            repo
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token
                    )
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "GitHub repository exists: {}/{}",
                    githubUsername,
                    repo
            );

            return true;

        } catch (HttpClientErrorException.NotFound e) {

            log.info(
                    "GitHub repository does not exist: {}/{}",
                    githubUsername,
                    repo
            );

            return false;

        } catch (HttpClientErrorException.Unauthorized e) {

            throw new GitHubException(
                    "GitHub authentication failed. Please reconnect GitHub.",
                    "GITHUB_AUTH_FAILED",
                    401,
                    e
            );

        } catch (HttpClientErrorException.Forbidden e) {

            throw new GitHubException(
                    "GitHub access forbidden. Check repository permissions.",
                    "GITHUB_FORBIDDEN",
                    403,
                    e
            );

        } catch (HttpClientErrorException e) {

            log.error(
                    "GitHub repository check failed: {}",
                    e.getStatusCode(),
                    e
            );

            throw new GitHubException(
                    "GitHub API error while checking repository",
                    "GITHUB_CHECK_FAILED",
                    e.getStatusCode().value(),
                    e
            );

        } catch (Exception e) {

            log.error(
                    "Unexpected error while checking GitHub repository",
                    e
            );

            throw new GitHubException(
                    "Failed to access GitHub repository: "
                            + e.getMessage(),
                    "GITHUB_CHECK_FAILED",
                    502,
                    e
            );
        }
    }

    // ============================================================
    // CREATE REPOSITORY
    // ============================================================

    public void createRepository() {

        User user = getCurrentUser();

        String token = getUserToken(user);

        String githubUsername =
                getAuthenticatedGitHubUsername(token);

        String repo =
                getUserRepositoryName(user);

        log.info(
                "Creating GitHub repository '{}' for user {}",
                repo,
                githubUsername
        );

        try {

            Map<String, Object> requestBody =
                    Map.of(
                            "name", repo,
                            "description",
                            "Automatically synchronized LeetCode solutions",
                            "private", false,
                            "auto_init", true
                    );

            restClient.post()
                    .uri("/user/repos")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token
                    )
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Repository created successfully: {}/{}",
                    githubUsername,
                    repo
            );

        } catch (HttpClientErrorException.Unauthorized e) {

            throw new GitHubException(
                    "GitHub authentication failed during repository creation",
                    "GITHUB_AUTH_FAILED",
                    401,
                    e
            );

        } catch (HttpClientErrorException.Forbidden e) {

            throw new GitHubException(
                    "GitHub access forbidden. Check token permissions.",
                    "GITHUB_FORBIDDEN",
                    403,
                    e
            );

        } catch (HttpClientErrorException.UnprocessableEntity e) {

            throw new GitHubException(
                    "Repository already exists or repository name is invalid",
                    "GITHUB_REPO_CONFLICT",
                    422,
                    e
            );

        } catch (HttpClientErrorException e) {

            throw new GitHubException(
                    "GitHub API error while creating repository",
                    "GITHUB_CREATE_FAILED",
                    e.getStatusCode().value(),
                    e
            );

        } catch (Exception e) {

            log.error(
                    "Failed to create GitHub repository",
                    e
            );

            throw new GitHubException(
                    "Failed to create GitHub repository",
                    "GITHUB_CREATE_FAILED",
                    502,
                    e
            );
        }
    }

    // ============================================================
    // ENSURE REPOSITORY
    // ============================================================

    public void ensureRepositoryExists() {

        if (!repositoryExists()) {

            createRepository();
        }
    }

    // ============================================================
    // CREATE / UPDATE SOLUTION FILE
    // ============================================================

    public void createSolutionFile(
            SubmissionRequest request) {

        User user = getCurrentUser();

        String token = getUserToken(user);

        String githubUsername =
                getAuthenticatedGitHubUsername(token);

        String repo =
                getUserRepositoryName(user);

        try {

            String extension =
                    FileExtensionUtil.getExtension(
                            request.getLanguage()
                    );

            String className =
                    FileNameUtil.createClassName(
                            request.getProblemTitle()
                    );

            String folderName =
                    String.format(
                            "%04d-%s",
                            request.getProblemNo(),
                            request.getProblemTitle()
                                    .toLowerCase()
                                    .replaceAll(
                                            "[^a-z0-9]+",
                                            "-"
                                    )
                                    .replaceAll(
                                            "^-|-$",
                                            ""
                                    )
                    );

            String path =
                    folderName +
                    "/" +
                    className +
                    extension;

            String encodedContent =
                    Base64.getEncoder()
                            .encodeToString(
                                    request.getCode()
                                            .getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                            );

            String existingSha =
                    getExistingFileSha(path);

            Map<String, Object> requestBody;

            if (existingSha == null) {

                requestBody =
                        Map.of(
                                "message",
                                "Solved LeetCode #"
                                        + request.getProblemNo()
                                        + " - "
                                        + request.getProblemTitle(),

                                "content",
                                encodedContent
                        );

            } else {

                requestBody =
                        Map.of(
                                "message",
                                "Updated LeetCode #"
                                        + request.getProblemNo()
                                        + " - "
                                        + request.getProblemTitle(),

                                "content",
                                encodedContent,

                                "sha",
                                existingSha
                        );
            }

            restClient.put()
                    .uri(
                            "/repos/{owner}/{repo}/contents/{path}",
                            githubUsername,
                            repo,
                            path
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token
                    )
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Solution {} successfully: {} for user {}",
                    existingSha == null
                            ? "created"
                            : "updated",
                    path,
                    githubUsername
            );

        } catch (HttpClientErrorException e) {

            log.error(
                    "GitHub API error while creating solution file: {} {}",
                    e.getStatusCode(),
                    e.getMessage()
            );

            throw new GitHubException(
                    "Failed to sync solution file to GitHub",
                    "GITHUB_FILE_OPERATION_FAILED",
                    e.getStatusCode().value(),
                    e
            );

        } catch (IllegalArgumentException e) {

            log.warn(
                    "Invalid submission parameter: {}",
                    e.getMessage()
            );

            throw e;

        } catch (GitHubException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Unexpected error while creating solution file",
                    e
            );

            throw new GitHubException(
                    "Failed to sync solution file to GitHub",
                    "GITHUB_FILE_OPERATION_FAILED",
                    502,
                    e
            );
        }
    }

    // ============================================================
    // GET EXISTING FILE SHA
    // ============================================================

    public String getExistingFileSha(String path) {

        User user = getCurrentUser();

        String token = getUserToken(user);

        String githubUsername =
                getAuthenticatedGitHubUsername(token);

        String repo =
                getUserRepositoryName(user);

        try {

            Map<String, Object> response =
                    restClient.get()
                            .uri(
                                    "/repos/{owner}/{repo}/contents/{path}",
                                    githubUsername,
                                    repo,
                                    path
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token
                            )
                            .retrieve()
                            .body(Map.class);

            if (response == null ||
                    response.get("sha") == null) {

                throw new GitHubException(
                        "GitHub response missing SHA",
                        "GITHUB_INVALID_RESPONSE",
                        502
                );
            }

            return response
                    .get("sha")
                    .toString();

        } catch (HttpClientErrorException.NotFound e) {

            return null;

        } catch (HttpClientErrorException.Unauthorized e) {

            throw new GitHubException(
                    "GitHub authentication failed. Please reconnect GitHub.",
                    "GITHUB_AUTH_FAILED",
                    401,
                    e
            );

        } catch (HttpClientErrorException.Forbidden e) {

            throw new GitHubException(
                    "GitHub access forbidden while checking file",
                    "GITHUB_FORBIDDEN",
                    403,
                    e
            );

        } catch (HttpClientErrorException e) {

            log.error(
                    "GitHub API error while checking SHA: {} {}",
                    e.getStatusCode(),
                    e.getMessage()
            );

            throw new GitHubException(
                    "Failed to check existing file",
                    "GITHUB_CHECK_FAILED",
                    e.getStatusCode().value(),
                    e
            );

        } catch (GitHubException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Unexpected error while checking SHA",
                    e
            );

            throw new GitHubException(
                    "Failed to check existing file",
                    "GITHUB_CHECK_FAILED",
                    502,
                    e
            );
        }
    }
}