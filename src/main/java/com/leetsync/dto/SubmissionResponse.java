package com.leetsync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Standard response for successful or failed submission processing.
 * Used by both success and failure scenarios to maintain consistent API contract.
 */
@Data
@Builder
@AllArgsConstructor
public class SubmissionResponse {
    
    private boolean success;
    private String message;
    private Integer problemNo;
    private String problemTitle;
    private String language;
    
    /**
     * Constructor for error responses without problem details.
     */
    public SubmissionResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
