package io.portfolio.urlshortener.shortener.dto;

import java.time.Instant;

public record CreateUrlRequest(
        String longUrl,
        String customAlias,
        Instant expiresAt,
        Long ttlSeconds) {
}
