package io.portfolio.urlshortener.shortener;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;

public record UpdateLinkRequest(
        @JsonAlias({"newLongUrl"}) String longUrl,
        Instant expiresAt,
        Long ttlSeconds) {
}
