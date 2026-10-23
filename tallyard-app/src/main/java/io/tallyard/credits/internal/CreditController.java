package io.tallyard.credits.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tallyard.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Credits", description = "Prepaid credit grants")
class CreditController {

    private final CreditService credits;

    CreditController(CreditService credits) {
        this.credits = credits;
    }

    record GrantRequest(
            @NotBlank String customerExternalId,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,

            @NotNull @DecimalMin(value = "0", inclusive = false)
            BigDecimal amount,

            Integer priority,
            Instant expiresAt) {}

    record GrantResponse(
            UUID id,
            String currency,
            BigDecimal amountGranted,
            BigDecimal amountConsumed,
            BigDecimal remaining,
            int priority,
            Instant expiresAt,
            Instant createdAt) {
        static GrantResponse from(CreditGrant g) {
            return new GrantResponse(
                    g.getId(),
                    g.getCurrency(),
                    g.getAmountGranted(),
                    g.getAmountConsumed(),
                    g.remaining(),
                    g.getPriority(),
                    g.getExpiresAt(),
                    g.getCreatedAt());
        }
    }

    @Operation(summary = "Grant prepaid credit to a customer")
    @PostMapping("/credits")
    @PreAuthorize("@scopes.has('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    GrantResponse grant(@Valid @RequestBody GrantRequest r) {
        return GrantResponse.from(credits.grantInternal(
                TenantContext.tenantId(),
                r.customerExternalId(),
                Currency.getInstance(r.currency()),
                r.amount(),
                r.priority() == null ? 0 : r.priority(),
                r.expiresAt()));
    }

    @Operation(summary = "List a customer's credit grants")
    @GetMapping("/customers/{externalId}/credits")
    @PreAuthorize("@scopes.has('READ')")
    List<GrantResponse> list(@PathVariable String externalId) {
        return credits.grantsFor(TenantContext.tenantId(), externalId).stream()
                .map(GrantResponse::from)
                .toList();
    }
}
