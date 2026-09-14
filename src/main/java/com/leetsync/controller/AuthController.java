package com.leetsync.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.leetsync.dto.AuthResponse;
import com.leetsync.dto.UserResponse;
import com.leetsync.entity.User;
import com.leetsync.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication controller for V3 multi-user LeetSync.
 *
 * Handles:
 * - GitHub OAuth login initiation
 * - GitHub OAuth callback
 * - User profile retrieval
 * - Logout
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
    name = "Authentication",
    description = "GitHub OAuth and user authentication endpoints"
)
public class AuthController {

    private final AuthService authService;

    @Value("${github.client-id}")
    private String githubClientId;

    @Value("${github.oauth-redirect-uri}")
    private String githubRedirectUri;

    /**
     * Chrome Extension OAuth success page.
     *
     * IMPORTANT:
     * Replace this extension ID with your actual Chrome extension ID.
     *
     * Example:
     * chrome-extension://abcdefghijklmnop/auth-success.html
     */
    @Value("${leetsync.extension-success-url}")
    private String extensionSuccessUrl;

    /**
     * Initiates GitHub OAuth login.
     *
     * Returns the GitHub authorization URL.
     */
    @GetMapping("/github")
    @Operation(
        summary = "Initiate GitHub login",
        description = "Returns URL for GitHub OAuth authorization"
    )
    @ApiResponse(
        responseCode = "200",
        description = "GitHub login URL"
    )
    public ResponseEntity<Map<String, String>> initiateGitHubLogin() {

        log.info("GitHub login initiated");

        String githubAuthUrl =
            "https://github.com/login/oauth/authorize"
            + "?client_id="
            + encode(githubClientId)
            + "&redirect_uri="
            + encode(githubRedirectUri)
            + "&scope="
            + encode("repo");

        Map<String, String> response = new LinkedHashMap<>();
        response.put("url", githubAuthUrl);

        return ResponseEntity.ok(response);
    }

    /**
     * Handles GitHub OAuth callback.
     *
     * After successful authentication:
     *
     * Backend
     *   ↓
     * Generate JWT
     *   ↓
     * Redirect to Chrome Extension
     *   ↓
     * auth-success.html?token=JWT
     */
    @GetMapping("/github/callback")
    @Operation(
        summary = "GitHub OAuth callback",
        description = "Processes GitHub authorization code"
    )
    public RedirectView handleGitHubCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String error
    ) {

        /*
         * Handle GitHub OAuth denial/error.
         */
        if (error != null && !error.isBlank()) {

            log.warn(
                "GitHub OAuth error: {}",
                error
            );

            String redirectUrl =
                extensionSuccessUrl
                + "?success=false"
                + "&error="
                + encode(error);

            return redirect(redirectUrl);
        }

        /*
         * Validate authorization code.
         */
        if (code == null || code.isBlank()) {

            log.warn(
                "Missing authorization code in GitHub callback"
            );

            String redirectUrl =
                extensionSuccessUrl
                + "?success=false"
                + "&error="
                + encode("Authorization code is required");

            return redirect(redirectUrl);
        }

        try {

            /*
             * Exchange GitHub authorization code,
             * create/update user,
             * encrypt GitHub token,
             * generate LeetSync JWT.
             */
            AuthResponse authResponse =
                authService.handleGitHubCallback(code);

            /*
             * Authentication failed.
             */
            if (!authResponse.isSuccess()
                    || authResponse.getToken() == null
                    || authResponse.getToken().isBlank()) {

                log.warn(
                    "GitHub authentication failed"
                );

                String message =
                    authResponse.getMessage() != null
                        ? authResponse.getMessage()
                        : "GitHub authentication failed";

                String redirectUrl =
                    extensionSuccessUrl
                    + "?success=false"
                    + "&error="
                    + encode(message);

                return redirect(redirectUrl);
            }

            /*
             * Authentication successful.
             *
             * Pass the JWT to the extension.
             */
            String token = authResponse.getToken();

            String redirectUrl =
                extensionSuccessUrl
                + "?success=true"
                + "&token="
                + encode(token);

            log.info(
                "GitHub authentication successful. "
                + "Redirecting user to Chrome Extension."
            );

            return redirect(redirectUrl);

        } catch (Exception e) {

            log.error(
                "GitHub OAuth callback processing failed",
                e
            );

            String redirectUrl =
                extensionSuccessUrl
                + "?success=false"
                + "&error="
                + encode("Authentication failed");

            return redirect(redirectUrl);
        }
    }

    /**
     * Get current authenticated user profile.
     *
     * Requires JWT authentication.
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get current user",
        description = "Returns profile of authenticated user"
    )
    public ResponseEntity<?> getCurrentUser(
        Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User)) {

            log.warn(
                "Unauthenticated user attempted to access /api/auth/me"
            );

            Map<String, Object> response =
                new LinkedHashMap<>();

            response.put("success", false);
            response.put("message", "Not authenticated");

            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
        }

        User user =
            (User) authentication.getPrincipal();

        log.debug(
            "Retrieving profile for user: {}",
            user.getGithubUsername()
        );

        UserResponse userResponse =
            authService.getUserProfile(user.getId());

        Map<String, Object> response =
            new LinkedHashMap<>();

        response.put("success", true);
        response.put("user", userResponse);

        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint.
     *
     * JWT is stateless, so the actual token is removed
     * by the Chrome Extension.
     */
    @PostMapping("/logout")
    @Operation(
        summary = "Logout",
        description = "Logout current user"
    )
    public ResponseEntity<?> logout(
        Authentication authentication
    ) {

        if (authentication != null
                && authentication.getPrincipal() instanceof User) {

            User user =
                (User) authentication.getPrincipal();

            log.info(
                "User logout: {}",
                user.getGithubUsername()
            );
        }

        Map<String, Object> response =
            new LinkedHashMap<>();

        response.put("success", true);
        response.put(
            "message",
            "Logged out successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Safely URL-encodes OAuth parameters.
     */
    private String encode(String value) {

        if (value == null) {
            return "";
        }

        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
        );
    }

    /**
     * Creates a redirect response.
     */
    private RedirectView redirect(String url) {

        RedirectView redirectView =
            new RedirectView(url);

        redirectView.setStatusCode(
            HttpStatus.FOUND
        );

        return redirectView;
    }
}