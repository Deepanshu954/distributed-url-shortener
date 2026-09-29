package io.portfolio.urlshortener.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * CONTRACT — frozen post-M0. Click event schema, version 1.
 *
 * <p>{@code eventId} is generated producer-side and is the idempotency key for
 * exactly-once-effect aggregation (raw insert with PK conflict detection gates
 * the aggregate increment — ADR-007). {@code requestId} propagates the HTTP
 * request id for end-to-end tracing. No PII: no full IP, no user id.
 */
public record ClickEvent(
        UUID eventId,
        String shortCode,
        Instant timestamp,
        String referrer,
        String userAgent,
        String requestId) {

    public static ClickEvent of(String shortCode, String referrer, String userAgent, String requestId) {
        String safeRef = (referrer != null && referrer.length() > 1000) ? referrer.substring(0, 1000) : referrer;
        String safeUa = (userAgent != null && userAgent.length() > 500) ? userAgent.substring(0, 500) : userAgent;
        return new ClickEvent(UUID.randomUUID(), shortCode, Instant.now(), safeRef, safeUa, requestId);
    }
}
