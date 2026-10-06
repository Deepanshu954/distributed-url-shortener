package io.portfolio.urlshortener.shortener.dto;

import java.time.Instant;

public record UpdateUrlRequest(
        String longUrl,
        Instant expiresAt,
        Long ttlSeconds) {
}
