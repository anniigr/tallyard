package io.tallyard.shared.error;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Signals a temporarily unavailable dependency; callers should retry after {@link #retryAfter()}. */
public class ServiceUnavailableException extends TallyardException {

    private final Duration retryAfter;

    public ServiceUnavailableException(String code, String message, Duration retryAfter, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message, cause);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
