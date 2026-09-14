package com.leetsync.exception;

/**
 * Exception thrown when GitHub API operations fail.
 * Wraps GitHub-specific errors with user-friendly messages.
 */
public class GitHubException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    
    public GitHubException(String message) {
        super(message);
        this.errorCode = "GITHUB_ERROR";
        this.httpStatus = 502;
    }
    
    public GitHubException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "GITHUB_ERROR";
        this.httpStatus = 502;
    }
    
    public GitHubException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public GitHubException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}
