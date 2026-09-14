package com.leetsync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user settings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingsRequest {
    
    private String repositoryName;
    private String defaultBranch;
    private Boolean autoSyncEnabled;
}
