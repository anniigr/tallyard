package io.tallyard.shared.error;

import java.time.Duration;
import org.springframework.http.HttpStatus;

public class RateLimitedException extends TallyardException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Rate limit exceeded for this API key");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
