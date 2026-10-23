package io.tallyard.shared.error;

import org.springframework.http.HttpStatus;

/** Base class for domain and application errors that map to a problem-details response. */
public abstract class TallyardException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected TallyardException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected TallyardException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    /** Stable machine-readable error code, e.g. {@code period.illegal_transition}. */
    public String code() {
        return code;
    }
}
