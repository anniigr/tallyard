package io.tallyard.shared.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends TallyardException {
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
