package io.tallyard.shared.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends TallyardException {
    public NotFoundException(String what, Object id) {
        super(HttpStatus.NOT_FOUND, "not_found", what + " not found: " + id);
    }
}
