package com.example.optimizepatientimport.service;

import com.example.optimizepatientimport.api.dto.PatientImportRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PatientValidationService {

    public ValidationResult validate(PatientImportRequest request) {
        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            return ValidationResult.invalid("firstName is required");
        }
        if (request.getLastName() == null || request.getLastName().isBlank()) {
            return ValidationResult.invalid("lastName is required");
        }
        LocalDate dob = request.getDateOfBirth();
        if (dob == null) {
            return ValidationResult.invalid("dateOfBirth is required");
        }
        if (dob.isAfter(LocalDate.now())) {
            return ValidationResult.invalid("dateOfBirth cannot be in the future");
        }
        // More domain-specific rules could be added here (e.g., age limits)
        return ValidationResult.valid();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
