package io.portfolio.urlshortener.shortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UrlResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Instant expiresAt,
        String manageToken) {

    public UrlResponse(String shortCode, String shortUrl, String longUrl, Instant expiresAt) {
        this(shortCode, shortUrl, longUrl, expiresAt, null);
    }
}
