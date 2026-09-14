package com.leetsync.exception;

import com.leetsync.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for consistent error responses across all endpoints.
 * Handles validation errors, GitHub API errors, and unexpected exceptions.
 * Does NOT expose sensitive information like GitHub tokens or stack traces.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle GitHub API operation failures.
     */
    @ExceptionHandler(GitHubException.class)
    public ResponseEntity<ErrorResponse> handleGitHubException(
            GitHubException ex,
            WebRequest request) {
        
        log.error("GitHub API error: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();
        
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(errorResponse);
    }
    
    /**
     * Handle submission validation failures.
     */
    @ExceptionHandler(InvalidSubmissionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSubmissionException(
            InvalidSubmissionException ex,
            WebRequest request) {
        
        log.warn("Invalid submission: {} (field: {})", ex.getMessage(), ex.getField());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode("INVALID_SUBMISSION")
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
    
    /**
     * Handle Bean Validation constraint violations.
     * Provides detailed field validation errors to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        String message = "Validation failed: " +
                ex.getBindingResult()
                   .getFieldError()
                   .getDefaultMessage();
        
        log.warn("Validation error: {}", message);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .message(message)
                .errorCode("VALIDATION_ERROR")
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
    
    /**
     * Handle illegal argument errors (e.g., unsupported language).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {
        
        log.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode("INVALID_ARGUMENT")
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
    
    /**
     * Handle all unexpected exceptions.
     * Does NOT expose stack traces or internal details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        
        log.error("Unexpected error occurred", ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .message("An unexpected error occurred. Please try again later.")
                .errorCode("INTERNAL_ERROR")
                .build();
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}
