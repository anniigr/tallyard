package io.tallyard.shared.tenant;

/** Capabilities an API key may hold. */
public enum ApiScope {
    /** Submit usage events. */
    INGEST,
    /** Read usage, periods, invoices, entitlements. */
    READ,
    /** Manage catalog, periods, credits, webhooks and keys. Implies READ. */
    ADMIN;

    public boolean implies(ApiScope required) {
        return this == required || (this == ADMIN && required == READ);
    }
}
