package com.leetsync.exception;

/**
 * Exception thrown when submission validation fails.
 * Used for business logic validation beyond Bean Validation constraints.
 */
public class InvalidSubmissionException extends RuntimeException {
    
    private final String field;
    private final int httpStatus;
    
    public InvalidSubmissionException(String message) {
        super(message);
        this.field = null;
        this.httpStatus = 400;
    }
    
    public InvalidSubmissionException(String message, String field) {
        super(message);
        this.field = field;
        this.httpStatus = 400;
    }
    
    public InvalidSubmissionException(String message, String field, Throwable cause) {
        super(message, cause);
        this.field = field;
        this.httpStatus = 400;
    }
    
    public String getField() {
        return field;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}
