package com.leetsync.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation to ensure submission status is "Accepted".
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AcceptedStatusValidator.class)
@Documented
public @interface ValidAcceptedStatus {
    
    String message() default "Status must be 'Accepted' for submission to be synced";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
