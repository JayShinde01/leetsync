package com.leetsync.controller;

import com.leetsync.dto.SubmissionRequest;
import com.leetsync.dto.SubmissionResponse;
import com.leetsync.exception.InvalidSubmissionException;
import com.leetsync.service.GitHubService;
import com.leetsync.service.ReadmeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for LeetCode solution submissions.
 * Handles submission requests from the Chrome Extension.
 * 
 * Endpoints:
 * - GET /api/health - Health check
 * - POST /api/submissions - Submit accepted LeetCode solution
 * - GET /api/submissions/github - Check GitHub repository status
 * - GET /api/submissions/github/setup - Setup GitHub repository
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin("*")
@Tag(name = "Submissions", description = "LeetCode solution submission endpoints")
public class SubmissionController {

    private final GitHubService gitHubService;
    private final ReadmeService readmeService;

    public SubmissionController(GitHubService gitHubService, ReadmeService readmeService) {
        this.gitHubService = gitHubService;
        this.readmeService = readmeService;
        log.info("SubmissionController initialized");
    }

    /**
     * Health check endpoint.
     * Used by Chrome Extension to verify backend connectivity.
     * 
     * @return status UP with service name
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if LeetSync backend is running")
    @ApiResponse(responseCode = "200", description = "Backend is healthy",
            content = @Content(schema = @Schema(example = "{\"status\": \"UP\", \"service\": \"LeetSync Backend\"}")))
    public ResponseEntity<?> health() {
        log.debug("Health check requested");
        return ResponseEntity.ok(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("status", "UP");
                    put("service", "LeetSync Backend");
                }}
        );
    }

    /**
     * Check if the configured GitHub repository exists.
     * 
     * @return status message about repository
     */
    @GetMapping("/submissions/github")
    @Operation(summary = "Check GitHub repository", description = "Verify if GitHub repository exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Repository status checked"),
            @ApiResponse(responseCode = "502", description = "GitHub API error")
    })
    public ResponseEntity<String> checkGitHubRepository() {
        log.debug("GitHub repository check requested");

        boolean exists = gitHubService.repositoryExists();

        if (exists) {
            log.info("GitHub repository exists");
            return ResponseEntity.ok(
                    "GitHub repository 'leetcode' exists."
            );
        }

        log.info("GitHub repository does not exist");
        return ResponseEntity.ok(
                "GitHub repository 'leetcode' does not exist."
        );
    }

    /**
     * Setup the GitHub repository.
     * Creates the repository if it doesn't exist.
     * 
     * @return confirmation message
     */
    @GetMapping("/submissions/github/setup")
    @Operation(summary = "Setup GitHub repository", description = "Create GitHub repository if it doesn't exist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Repository setup completed"),
            @ApiResponse(responseCode = "502", description = "GitHub API error")
    })
    public ResponseEntity<String> setupGitHubRepository() {
        log.info("GitHub repository setup requested");

        gitHubService.ensureRepositoryExists();

        log.info("GitHub repository setup completed");
        return ResponseEntity.ok(
                "GitHub repository setup completed."
        );
    }

    /**
     * Submit an accepted LeetCode solution.
     * 
     * The solution must have status "Accepted" to be synced.
     * Creates or updates the solution file on GitHub and updates README.
     * 
     * Expected request format from Chrome Extension:
     * {
     *   "problemNo": 144,
     *   "problemTitle": "Binary Tree Preorder Traversal",
     *   "language": "java",
     *   "code": "class Solution { ... }",
     *   "status": "Accepted"
     * }
     * 
     * @param request submission details
     * @return structured response with submission details
     */
    @PostMapping("/submissions")
    @Operation(summary = "Submit solution", description = "Submit an accepted LeetCode solution to sync with GitHub")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Solution synced successfully",
                    content = @Content(schema = @Schema(example = 
                    "{\"success\": true, \"message\": \"Solution synced successfully\", \"problemNo\": 144, \"problemTitle\": \"Binary Tree Preorder Traversal\", \"language\": \"java\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(example = 
                    "{\"success\": false, \"message\": \"Validation failed: Problem number must be greater than 0\", \"errorCode\": \"VALIDATION_ERROR\"}"))),
            @ApiResponse(responseCode = "502", description = "GitHub API error",
                    content = @Content(schema = @Schema(example = 
                    "{\"success\": false, \"message\": \"GitHub repository update failed\", \"errorCode\": \"GITHUB_ERROR\"}")))
    })
    public ResponseEntity<SubmissionResponse> receiveSubmission(
            @Valid @RequestBody SubmissionRequest request) {

        log.info("Received submission for problem: {} ({})", 
                request.getProblemNo(), 
                request.getProblemTitle());

        // Ensure repository exists before processing
        gitHubService.ensureRepositoryExists();
        log.debug("GitHub repository is available");

        // Create/update solution file on GitHub
        gitHubService.createSolutionFile(request);
        log.debug("Solution file synced to GitHub");

        // Update README with new problem
        readmeService.updateReadme(request);
        log.debug("README updated");

        // Return structured success response
        SubmissionResponse response = SubmissionResponse.builder()
                .success(true)
                .message("Solution synced successfully")
                .problemNo(request.getProblemNo())
                .problemTitle(request.getProblemTitle())
                .language(request.getLanguage())
                .build();

        log.info("Submission processed successfully for problem: {}", request.getProblemNo());
        return ResponseEntity.ok(response);
    }
}

