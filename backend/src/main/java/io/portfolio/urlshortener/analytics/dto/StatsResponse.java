package io.portfolio.urlshortener.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsResponse(
        String shortCode,
        long clickCount,
        Instant lastClickAt,
        String lastReferrer) {
}
