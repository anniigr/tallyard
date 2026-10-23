package io.tallyard.shared.error;

import org.springframework.http.HttpStatus;

public class ValidationException extends TallyardException {
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "validation_failed", message);
    }

    public ValidationException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
