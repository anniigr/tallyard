package io.tallyard.shared.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends TallyardException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "forbidden", message);
    }
}
