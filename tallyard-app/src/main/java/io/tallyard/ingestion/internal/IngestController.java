package io.tallyard.ingestion.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.error.ValidationException;
import io.tallyard.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
@Tag(name = "Ingestion", description = "Submit usage events")
class IngestController {

    private final IngestService ingest;
    private final TallyardProperties props;

    IngestController(IngestService ingest, TallyardProperties props) {
        this.ingest = ingest;
        this.props = props;
    }

    record IngestRequest(@NotEmpty List<@Valid IncomingEvent> events) {}

    @Operation(summary = "Ingest a batch of usage events", description = """
                    Each event carries a client-generated eventId that acts as an idempotency key per tenant. \
                    Re-submitting an event is always safe: duplicates are discarded downstream. \
                    The call returns 202 only after the event log (Kafka) has acknowledged every accepted event. \
                    Structurally invalid events are reported per item and do not fail the batch.""")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Batch durably accepted"),
        @ApiResponse(responseCode = "400", description = "Malformed request"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
        @ApiResponse(responseCode = "403", description = "Key lacks INGEST scope"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded; see Retry-After"),
        @ApiResponse(responseCode = "503", description = "Event log unavailable; retry with the same event ids")
    })
    @PostMapping
    @PreAuthorize("@scopes.has('INGEST')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    IngestResult ingest(@Valid @RequestBody IngestRequest request) {
        if (request.events().size() > props.ingestion().maxBatchSize()) {
            throw new ValidationException(
                    "batch_too_large",
                    "A batch may contain at most " + props.ingestion().maxBatchSize() + " events");
        }
        return ingest.ingest(TenantContext.require(), request.events());
    }
}
