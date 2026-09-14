package com.leetsync.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.leetsync.entity.User;
import com.leetsync.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT Authentication Filter for Spring Security.
 * 
 * Intercepts every HTTP request to:
 * 1. Extract JWT token from Authorization header
 * 2. Validate JWT token
 * 3. Load user from database
 * 4. Set authentication in SecurityContext
 * 
 * This allows Spring Security to protect endpoints with @PreAuthorize or hasRole().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            // Extract JWT token from Authorization header
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            String token = null;
            
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                token = authHeader.substring(BEARER_PREFIX.length());
            }
            
            // If token exists, validate and set authentication
            if (token != null && jwtService.isTokenValid(token)) {
                Long userId = jwtService.extractUserId(token);
                
                // Load user from database
                User user = userRepository.findById(userId).orElse(null);
                
                if (user != null && Boolean.TRUE.equals(user.getGithubConnected())) {
                    // Create authentication token
                    UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            null  // No authorities/roles in this simple setup
                        );
                    
                    // Set in security context
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    
                    log.debug("JWT authentication successful for user: {}", user.getGithubUsername());
                } else {
                    log.warn("User not found or GitHub disconnected for JWT token");
                }
            }
            
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
        }
        
        // Continue with the request regardless of JWT validation
        // Security configuration determines which endpoints require authentication
        filterChain.doFilter(request, response);
    }
}
