package com.leetsync.controller;

import com.leetsync.dto.AuthResponse;
import com.leetsync.dto.UserResponse;
import com.leetsync.entity.User;
import com.leetsync.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @Value("${github.client-id}")
    private String githubClientId;

    @Value("${github.oauth-redirect-uri}")
    private String githubRedirectUri;

    /*
     * Chrome extension OAuth callback.
     *
     * This must match the Chrome extension ID.
     */
    private static final String EXTENSION_OAUTH_CALLBACK =
            "https://bhjiihcbpajmpcnjpkeklnbgbjkebpge.chromiumapp.org/";

    /**
     * Start GitHub OAuth.
     *
     * The extension calls this endpoint to get
     * the GitHub authorization URL.
     */
    @GetMapping("/github")
    public ResponseEntity<Map<String, String>> startGitHubOAuth() {

        String url =
                "https://github.com/login/oauth/authorize"
                        + "?client_id=" + encode(githubClientId)
                        + "&redirect_uri=" + encode(githubRedirectUri)
                        + "&scope=" + encode("repo");

        log.info("Starting GitHub OAuth flow.");

        return ResponseEntity.ok(
                Map.of("url", url)
        );
    }

    /**
     * GitHub OAuth callback.
     *
     * GitHub redirects here after the user authorizes
     * the application.
     *
     * The backend:
     * 1. Exchanges the GitHub code for an access token.
     * 2. Creates or updates the user.
     * 3. Generates our JWT.
     * 4. Redirects to Chrome's chromiumapp.org callback.
     */
    @GetMapping("/github/callback")
    public RedirectView githubCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(
                    required = false,
                    name = "error_description"
            ) String errorDescription) {

        /*
         * GitHub authorization was rejected/cancelled.
         */
        if (error != null) {

            log.warn(
                    "GitHub OAuth returned error: {} - {}",
                    error,
                    errorDescription
            );

            String message =
                    errorDescription != null
                            ? errorDescription
                            : error;

            return redirectWithError(message);
        }

        /*
         * GitHub did not provide an authorization code.
         */
        if (code == null || code.isBlank()) {

            log.warn(
                    "GitHub OAuth callback received without code."
            );

            return redirectWithError(
                    "GitHub authorization code was missing."
            );
        }

        try {

            log.info("GitHub OAuth callback received.");

            /*
             * AuthService handles:
             * - GitHub access token exchange
             * - GitHub user information
             * - User creation/update
             * - Token encryption
             * - JWT generation
             */
            AuthResponse authResponse =
                    authService.handleGitHubCallback(code);

            if (authResponse == null) {

                log.error(
                        "AuthService returned null authentication response."
                );

                return redirectWithError(
                        "Authentication failed."
                );
            }

            /*
             * AuthService returns success=false when
             * authentication fails.
             */
            if (!authResponse.isSuccess()) {

                String message =
                        authResponse.getMessage() != null
                                ? authResponse.getMessage()
                                : "GitHub authentication failed.";

                log.warn(
                        "GitHub authentication failed: {}",
                        message
                );

                return redirectWithError(message);
            }

            /*
             * Get our application's JWT.
             */
            String jwt = authResponse.getToken();

            if (jwt == null || jwt.isBlank()) {

                log.error(
                        "Authentication succeeded but JWT was missing."
                );

                return redirectWithError(
                        "Authentication token was not generated."
                );
            }

            log.info(
                    "GitHub authentication successful. "
                            + "Redirecting to Chrome OAuth callback."
            );

            /*
             * Redirect to Chrome's OAuth callback.
             *
             * popup.js receives this URL through
             * chrome.identity.launchWebAuthFlow().
             */
            String redirectUrl =
                    EXTENSION_OAUTH_CALLBACK
                            + "?success=true"
                            + "&token=" + encode(jwt);

            return new RedirectView(redirectUrl);

        } catch (Exception e) {

            log.error(
                    "GitHub OAuth callback failed.",
                    e
            );

            return redirectWithError(
                    "GitHub authentication failed."
            );
        }
    }

    /**
     * Get currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            Authentication authentication) {

        /*
         * JWT filter should populate Authentication.
         */
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User)) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            Map.of(
                                    "success", false,
                                    "message",
                                    "Authentication required."
                            )
                    );
        }

        User user =
                (User) authentication.getPrincipal();

        try {

            UserResponse userResponse =
                    authService.getUserProfile(user.getId());

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "user", userResponse
                    )
            );

        } catch (Exception e) {

            log.error(
                    "Failed to load authenticated user profile.",
                    e
            );

            return ResponseEntity
                    .status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .body(
                            Map.of(
                                    "success", false,
                                    "message",
                                    "Could not load user profile."
                            )
                    );
        }
    }

    /**
     * Logout.
     *
     * JWT authentication is stateless.
     * The extension removes the JWT locally.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            Authentication authentication) {

        if (authentication != null
                && authentication.getPrincipal() instanceof User) {

            User user =
                    (User) authentication.getPrincipal();

            log.info(
                    "User logged out: {}",
                    user.getGithubUsername()
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message",
                        "Logged out successfully."
                )
        );
    }

    /**
     * Redirect to Chrome OAuth callback with an error.
     */
    private RedirectView redirectWithError(
            String message) {

        String redirectUrl =
                EXTENSION_OAUTH_CALLBACK
                        + "?success=false"
                        + "&error=" + encode(message);

        return new RedirectView(redirectUrl);
    }

    /**
     * Safely URL-encode query parameters.
     */
    private String encode(String value) {

        return URLEncoder.encode(
                value == null ? "" : value,
                StandardCharsets.UTF_8
        );
    }
}