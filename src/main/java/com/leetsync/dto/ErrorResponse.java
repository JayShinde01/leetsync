package com.leetsync.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Standard error response structure.
 * Used globally by exception handlers to return consistent error information.
 * Sensitive details like stack traces and GitHub tokens are NOT included.
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private boolean success;
    private String message;
    private String errorCode;
    private LocalDateTime timestamp;
    
    /**
     * Constructor for simple error response.
     */
    public ErrorResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Constructor with error code.
     */
    public ErrorResponse(boolean success, String message, String errorCode) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
        this.timestamp = LocalDateTime.now();
    }
}
