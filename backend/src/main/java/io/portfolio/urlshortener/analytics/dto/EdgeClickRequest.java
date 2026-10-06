package io.portfolio.urlshortener.analytics.dto;

import java.time.Instant;

public record EdgeClickRequest(
        Instant timestamp) {
}
