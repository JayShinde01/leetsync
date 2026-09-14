package com.leetsync.dto;

import com.leetsync.validation.ValidAcceptedStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for LeetCode solution submission requests from the Chrome Extension.
 * Validates that submissions contain all required fields before syncing to GitHub.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRequest {
	
	@NotNull(message = "Problem number is required")
	@Min(value = 1, message = "Problem number must be greater than 0")
	private Integer problemNo;
	
	@NotBlank(message = "Problem title is required")
	private String problemTitle;
	
	@NotBlank(message = "Programming language is required")
	private String language;
	
	@NotBlank(message = "Solution code is required")
	private String code;
	
	@NotBlank(message = "Status is required")
	@ValidAcceptedStatus(message = "Only 'Accepted' submissions are synced")
	private String status;
}

