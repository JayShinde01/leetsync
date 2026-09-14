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

/**
 * Service for managing README.md file on GitHub.
 *
 * Maintains a table of solved LeetCode problems with
 * links to solution files.
 *
 * Updates the README with each new solution submission.
 */
@Slf4j
@Service
public class ReadmeService {

    private final GitHubProperties properties;
    private final RestClient restClient;
    private final TokenEncryptionService tokenEncryptionService;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public ReadmeService(
            GitHubProperties properties,
            TokenEncryptionService tokenEncryptionService,
            UserRepository userRepository,
            UserSettingsRepository userSettingsRepository) {

        this.properties = properties;
        this.tokenEncryptionService = tokenEncryptionService;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;

        log.info("Initializing ReadmeService");

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

    /**
     * Updates or creates README.md with the new problem submission.
     */
    public void updateReadme(SubmissionRequest request) {

        try {

            // =====================================================
            // CURRENT APPLICATION USER
            // =====================================================

            User user = getCurrentUser();

            // =====================================================
            // USER GITHUB TOKEN
            // =====================================================

            String token = getUserToken(user);

            // =====================================================
            // VERIFY TOKEN AND GET ACTUAL GITHUB USERNAME
            // =====================================================

            String githubUsername =
                    getAuthenticatedGitHubUsername(token);

            // =====================================================
            // USER REPOSITORY
            // =====================================================

            String repo =
                    getUserRepositoryName(user);

            log.info(
                    "Updating README for GitHub user {} - problem #{} ({})",
                    githubUsername,
                    request.getProblemNo(),
                    request.getProblemTitle()
            );

            // =====================================================
            // README PATH
            // =====================================================

            String path = "README.md";

            Map<String, Object> existingFile = null;

            // =====================================================
            // GET EXISTING README
            // =====================================================

            try {

                existingFile =
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

            } catch (HttpClientErrorException.NotFound e) {

                log.info(
                        "README.md does not exist for {}/{}; creating it",
                        githubUsername,
                        repo
                );

                existingFile = null;
            }

            // =====================================================
            // READ EXISTING README CONTENT
            // =====================================================

            String sha = null;
            String currentReadme = "";

            if (existingFile != null) {

                Object shaObject =
                        existingFile.get("sha");

                Object contentObject =
                        existingFile.get("content");

                if (shaObject != null) {
                    sha = shaObject.toString();
                }

                if (contentObject != null) {

                    String encodedContent =
                            contentObject.toString()
                                    .replace("\n", "")
                                    .replace("\r", "");

                    try {

                        currentReadme =
                                new String(
                                        Base64.getDecoder()
                                                .decode(encodedContent),
                                        StandardCharsets.UTF_8
                                );

                    } catch (IllegalArgumentException e) {

                        log.error(
                                "Failed to decode existing README.md",
                                e
                        );

                        throw new GitHubException(
                                "Existing README contains invalid content",
                                "GITHUB_README_INVALID_CONTENT",
                                502,
                                e
                        );
                    }
                }

                log.debug(
                        "Existing README found, length: {} chars",
                        currentReadme.length()
                );
            }

            // =====================================================
            // CREATE SOLUTION PATH
            // =====================================================

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

            String fileName =
                    className + extension;

            String solutionPath =
                    folderName + "/" + fileName;

            String language =
                    capitalize(request.getLanguage());

            // =====================================================
            // CREATE README TABLE ROW
            // =====================================================

            String newRow =
                    "| "
                    + request.getProblemNo()
                    + " | "
                    + request.getProblemTitle()
                    + " | "
                    + language
                    + " | [Solution](./"
                    + solutionPath
                    + ") |";

            log.debug(
                    "Creating README row for problem #{} - {}",
                    request.getProblemNo(),
                    request.getProblemTitle()
            );

            // =====================================================
            // UPDATE README CONTENT
            // =====================================================

            String updatedReadme;

            if (currentReadme.isBlank()) {

                log.info("Creating new README.md");

                updatedReadme =
                        "# LeetSync\n\n"
                        + "Automatically synchronized LeetCode solutions.\n\n"
                        + "## Progress\n\n"
                        + "**Total Solved: 1**\n\n"
                        + "| # | Problem | Language | Solution |\n"
                        + "|---|---------|----------|----------|\n"
                        + newRow
                        + "\n";

            } else {

                log.info("Updating existing README.md");

                updatedReadme =
                        addOrUpdateProblem(
                                currentReadme,
                                newRow,
                                request.getProblemNo()
                        );
            }

            // =====================================================
            // ENCODE README
            // =====================================================

            String encodedReadme =
                    Base64.getEncoder()
                            .encodeToString(
                                    updatedReadme.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            // =====================================================
            // GITHUB REQUEST BODY
            // =====================================================

            Map<String, Object> body;

            if (sha == null) {

                body =
                        Map.of(
                                "message",
                                "Update README - LeetCode #"
                                        + request.getProblemNo(),

                                "content",
                                encodedReadme
                        );

            } else {

                body =
                        Map.of(
                                "message",
                                "Update README - LeetCode #"
                                        + request.getProblemNo(),

                                "content",
                                encodedReadme,

                                "sha",
                                sha
                        );
            }

            // =====================================================
            // CREATE / UPDATE README
            // =====================================================

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
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "README.md updated successfully for {}/{} - problem #{}",
                    githubUsername,
                    repo,
                    request.getProblemNo()
            );

        } catch (HttpClientErrorException.Unauthorized e) {

            log.error(
                    "GitHub authentication failed while updating README"
            );

            throw new GitHubException(
                    "GitHub authentication failed. Please reconnect GitHub.",
                    "GITHUB_AUTH_FAILED",
                    401,
                    e
            );

        } catch (HttpClientErrorException.Forbidden e) {

            log.error(
                    "GitHub access forbidden while updating README"
            );

            throw new GitHubException(
                    "GitHub access forbidden. Check GitHub repository permissions.",
                    "GITHUB_FORBIDDEN",
                    403,
                    e
            );

        } catch (HttpClientErrorException.NotFound e) {

            log.error(
                    "GitHub repository or README not found",
                    e
            );

            throw new GitHubException(
                    "GitHub repository not found. Check your repository settings.",
                    "GITHUB_REPOSITORY_NOT_FOUND",
                    404,
                    e
            );

        } catch (HttpClientErrorException e) {

            log.error(
                    "GitHub API error while updating README: {} {}",
                    e.getStatusCode(),
                    e.getMessage()
            );

            throw new GitHubException(
                    "Failed to update README on GitHub: "
                            + e.getMessage(),
                    "GITHUB_README_UPDATE_FAILED",
                    e.getStatusCode().value(),
                    e
            );

        } catch (GitHubException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Unexpected error while updating README",
                    e
            );

            throw new GitHubException(
                    "Failed to update README on GitHub: "
                            + e.getMessage(),
                    "GITHUB_README_UPDATE_FAILED",
                    502,
                    e
            );
        }
    }

    // ============================================================
    // ADD OR UPDATE PROBLEM
    // ============================================================

    private String addOrUpdateProblem(
            String readme,
            String newRow,
            int problemNo) {

        String[] lines =
                readme.split("\\n");

        StringBuilder result =
                new StringBuilder();

        boolean updated = false;

        for (String line : lines) {

            if (line.startsWith("| " + problemNo + " |")) {

                result
                        .append(newRow)
                        .append("\n");

                updated = true;

            } else {

                result
                        .append(line)
                        .append("\n");
            }
        }

        if (!updated) {

            result
                    .append(newRow)
                    .append("\n");
        }

        String resultText =
                result.toString();

        // =====================================================
        // RECALCULATE SOLVED COUNT
        // =====================================================

        int solvedCount = 0;

        for (String line :
                resultText.split("\\n")) {

            if (line.matches(
                    "^\\| \\d+ \\|.*")) {

                solvedCount++;
            }
        }

        resultText =
                resultText.replaceAll(
                        "\\*\\*Total Solved: \\d+\\*\\*",
                        "**Total Solved: "
                                + solvedCount
                                + "**"
                );

        return resultText;
    }

    // ============================================================
    // CAPITALIZE LANGUAGE
    // ============================================================

    private String capitalize(String value) {

        if (value == null ||
                value.isBlank()) {

            return value;
        }

        return value.substring(0, 1)
                .toUpperCase()
                + value.substring(1)
                .toLowerCase();
    }

    // ============================================================
    // GET CURRENT APPLICATION USER
    // ============================================================

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
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
         * JwtAuthenticationFilter stores the actual
         * User entity as the authentication principal.
         *
         * Therefore we should NOT blindly use:
         *
         * authentication.getName()
         *
         * because that can return User.toString().
         */
        if (authentication.getPrincipal()
                instanceof User user) {

            return user;
        }

        /*
         * Fallback for authentication implementations
         * where principal is represented by username.
         */
        String username =
                authentication.getName();

        return userRepository
                .findByGithubUsername(username)
                .orElseThrow(() ->
                        new GitHubException(
                                "Application user not found: "
                                        + username,
                                "USER_NOT_FOUND",
                                404
                        ));
    }

    // ============================================================
    // GET USER GITHUB TOKEN
    // ============================================================

    private String getUserToken(User user) {

        String encrypted =
                user.getEncryptedGithubAccessToken();

        if (encrypted == null ||
                encrypted.isBlank()) {

            throw new GitHubException(
                    "GitHub token not configured for user",
                    "TOKEN_NOT_CONFIGURED",
                    400
            );
        }

        try {

            String token =
                    tokenEncryptionService.decrypt(
                            encrypted
                    );

            if (token == null ||
                    token.isBlank()) {

                throw new GitHubException(
                        "GitHub access token is empty",
                        "TOKEN_INVALID",
                        401
                );
            }

            return token;

        } catch (GitHubException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Failed to decrypt GitHub token",
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
    // VERIFY TOKEN WITH GITHUB
    // ============================================================

    private String getAuthenticatedGitHubUsername(
            String token) {

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
                    "GitHub token authenticated as {}",
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
                    "GitHub access forbidden. Please reconnect GitHub.",
                    "GITHUB_FORBIDDEN",
                    403,
                    e
            );

        } catch (HttpClientErrorException e) {

            throw new GitHubException(
                    "GitHub API error while validating token",
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
    // GET USER REPOSITORY
    // ============================================================

    private String getUserRepositoryName(User user) {

        return userSettingsRepository
                .findByUserId(user.getId())
                .map(UserSettings::getRepositoryName)
                .filter(name ->
                        name != null &&
                        !name.isBlank()
                )
                .orElse("leetcode");
    }
}