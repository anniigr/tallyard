package io.tallyard.entitlements.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.entitlements.EntitlementDecision;
import io.tallyard.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/entitlements")
@Tag(name = "Entitlements", description = "Real-time allow/deny decisions")
class EntitlementController {

    private final EntitlementService entitlements;

    EntitlementController(EntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    record CheckRequest(
            @NotBlank String subject,
            @NotBlank String meter,
            @DecimalMin("0") BigDecimal requested) {}

    @Operation(
            summary = "May this subject consume `requested` more units of `meter` right now?",
            description =
                    "Answers from a snapshot that is at most `tallyard.entitlements.cache-ttl` old (default 2 s).")
    @PostMapping("/check")
    @PreAuthorize("@scopes.has('READ')")
    EntitlementDecision check(@Valid @RequestBody CheckRequest r) {
        return entitlements.check(
                TenantContext.tenantId(),
                r.subject(),
                r.meter(),
                r.requested() == null ? BigDecimal.ONE : r.requested());
    }
}
