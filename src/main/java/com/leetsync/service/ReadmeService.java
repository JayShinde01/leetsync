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
import com.leetsync.repository.UserRepository;
import com.leetsync.repository.UserSettingsRepository;
import com.leetsync.entity.User;
import com.leetsync.entity.UserSettings;
import com.leetsync.security.TokenEncryptionService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Service for managing README.md file on GitHub.
 * Maintains a table of solved LeetCode problems with links to solutions.
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

    public ReadmeService(GitHubProperties properties,
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
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "LeetSync-Backend/2.0")
                .build();
    }

    /**
     * Updates or creates README.md with the new problem submission.
     * Maintains a table showing all solved problems with links to solution files.
     */
    public void updateReadme(SubmissionRequest request) {
        try {
            // Resolve current user and auth details
            User user = getCurrentUser();
            String token = getUserToken(user);
            String repo = getUserRepositoryName(user);

            log.info("Updating README for user {} problem: {} ({})",
                    user.getGithubUsername(),
                    request.getProblemNo(),
                    request.getProblemTitle());

            String path = "README.md";
            Map existingFile = null;
            try {
                existingFile = restClient.get()
                        .uri("/repos/{owner}/{repo}/contents/{path}",
                                user.getGithubUsername(),
                                repo,
                                path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(Map.class);
            } catch (HttpClientErrorException.NotFound e) {
                log.debug("README.md does not exist yet, will create new one");
                existingFile = null;
            }

            String sha = null;
            String currentReadme = "";
            if (existingFile != null) {
                sha = existingFile.get("sha").toString();
                String encodedContent = existingFile.get("content").toString();
                encodedContent = encodedContent.replace("\n", "");
                currentReadme = new String(Base64.getDecoder().decode(encodedContent), StandardCharsets.UTF_8);
                log.debug("Existing README found, length: {} chars", currentReadme.length());
            }

            // Use shared utility for language extension
            String extension = FileExtensionUtil.getExtension(request.getLanguage());
            // Use shared utility for class name generation
            String className = FileNameUtil.createClassName(request.getProblemTitle());

            String folderName = String.format("%04d-%s",
                    request.getProblemNo(),
                    request.getProblemTitle()
                            .toLowerCase()
                            .replaceAll("[^a-z0-9]+", "-")
                            .replaceAll("^-|-$", ""));

            String fileName = className + extension;
            String solutionPath = folderName + "/" + fileName;
            String language = capitalize(request.getLanguage());

            String newRow = "| " + request.getProblemNo() + " | " + request.getProblemTitle() + " | " +
                    language + " | [Solution](./" + solutionPath + ") |";

            log.debug("Creating table row for problem: {} - {}", request.getProblemNo(), request.getProblemTitle());

            String updatedReadme;
            if (currentReadme.isBlank()) {
                log.info("Creating new README.md");
                updatedReadme = "# LeetSync\n\nAutomatically synchronized LeetCode solutions.\n\n## Progress\n\n**Total Solved: 1**\n\n| # | Problem | Language | Solution |\n|---|---------|----------|----------|" + newRow + "\n";
            } else {
                log.info("Updating existing README.md");
                updatedReadme = addOrUpdateProblem(currentReadme, newRow, request.getProblemNo());
            }

            String encodedReadme = Base64.getEncoder().encodeToString(updatedReadme.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> body;
            if (sha == null) {
                body = Map.of(
                        "message", "Update README - LeetCode #" + request.getProblemNo(),
                        "content", encodedReadme);
            } else {
                body = Map.of(
                        "message", "Update README - LeetCode #" + request.getProblemNo(),
                        "content", encodedReadme,
                        "sha", sha);
            }

            restClient.put()
                    .uri("/repos/{owner}/{repo}/contents/{path}",
                            user.getGithubUsername(),
                            repo,
                            path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("README.md updated successfully for problem #{}", request.getProblemNo());
        } catch (HttpClientErrorException e) {
            log.error("GitHub API error while updating README: {} {}", e.getStatusCode(), e.getMessage());
            throw new GitHubException("Failed to update README on GitHub: " + e.getMessage(),
                    "GITHUB_README_UPDATE_FAILED", e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("Unexpected error while updating README", e);
            throw new GitHubException("Failed to update README on GitHub: " + e.getMessage(),
                    "GITHUB_README_UPDATE_FAILED", 502, e);
        }
    }

    private String addOrUpdateProblem(String readme, String newRow, int problemNo) {
        String[] lines = readme.split("\\n");
        StringBuilder result = new StringBuilder();
        boolean updated = false;
        for (String line : lines) {
            if (line.startsWith("| " + problemNo + " |")) {
                result.append(newRow).append("\n");
                updated = true;
            } else {
                result.append(line).append("\n");
            }
        }
        if (!updated) {
            result.append(newRow).append("\n");
        }
        String resultText = result.toString();
        int solvedCount = 0;
        for (String line : resultText.split("\\n")) {
            if (line.matches("^\\| \\d+ \\|.*")) {
                solvedCount++;
            }
        }
        resultText = resultText.replaceAll("\\*\\*Total Solved: \\d+\\*\\*",
                "**Total Solved: " + solvedCount + "**");
        return resultText;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }

    // Helper methods for per-user GitHub interaction
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
        String encrypted = user.getEncryptedGithubAccessToken();
        if (encrypted == null || encrypted.isBlank()) {
            throw new GitHubException("GitHub token not configured for user", "TOKEN_NOT_CONFIGURED", 400);
        }
        return tokenEncryptionService.decrypt(encrypted);
    }

    private String getUserRepositoryName(User user) {
        return userSettingsRepository.findByUserId(user.getId())
                .map(UserSettings::getRepositoryName)
                .orElse("leetcode");
    }
}
