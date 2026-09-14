package com.leetsync.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for the @ValidAcceptedStatus annotation.
 * Ensures that only "Accepted" submissions are synced to GitHub.
 */
public class AcceptedStatusValidator implements ConstraintValidator<ValidAcceptedStatus, String> {
    
    @Override
    public void initialize(ValidAcceptedStatus constraintAnnotation) {
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null values are handled by @NotBlank
        if (value == null) {
            return true;
        }
        
        // Only "Accepted" (case-insensitive) is valid
        return value.equalsIgnoreCase("Accepted");
    }
}
