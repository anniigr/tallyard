package io.tallyard.ingestion.internal;

import java.util.List;

record IngestResult(int accepted, int invalid, List<EventStatus> results) {

    enum Status {
        ACCEPTED,
        INVALID
    }

    record EventStatus(String eventId, Status status, List<String> errors) {
        static EventStatus accepted(String eventId) {
            return new EventStatus(eventId, Status.ACCEPTED, null);
        }

        static EventStatus invalid(String eventId, List<String> errors) {
            return new EventStatus(eventId, Status.INVALID, errors);
        }
    }
}
