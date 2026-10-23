package io.tallyard.billing;

import java.util.List;
import java.util.UUID;

/**
 * Result of re-deriving a rated period from the immutable event log (as of the original rating time)
 * and comparing it with the stored invoice draft.
 */
public record ReplayReport(
        UUID periodId,
        boolean matches,
        String storedHash,
        String replayedHash,
        List<String> differences,
        long durationMillis) {}
