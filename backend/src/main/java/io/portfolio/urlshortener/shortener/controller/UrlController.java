package io.portfolio.urlshortener.shortener.controller;

import io.portfolio.urlshortener.shortener.dto.CreateUrlRequest;
import io.portfolio.urlshortener.shortener.dto.UpdateUrlRequest;
import io.portfolio.urlshortener.shortener.dto.UrlMetadataResponse;
import io.portfolio.urlshortener.shortener.dto.UrlResponse;
import io.portfolio.urlshortener.shortener.service.UrlService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
public class UrlController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UrlController.class);

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Create short link endpoint. Supports both /api/links and /api/urls.
     * Dynamically adapts the returned shortUrl to the caller's hostname/domain (LAN IP or public host).
     */
    @PostMapping({"/api/links", "/api/urls"})
    public ResponseEntity<UrlResponse> create(
            @RequestBody CreateUrlRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest httpRequest) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        UrlService.CreationResult result = urlService.create(request, idempotencyKey, rid, null);
        UrlResponse res = result.url();
        String dynamicBaseUrl = extractBaseUrl(httpRequest);
        if (dynamicBaseUrl != null && !dynamicBaseUrl.isBlank()) {
            res = new UrlResponse(res.shortCode(), dynamicBaseUrl + "/" + res.shortCode(), res.longUrl(), res.expiresAt(), res.manageToken());
        }
        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(res);
    }

    /**
     * Metadata retrieval endpoint.
     */
    @GetMapping({"/api/links/{shortCode}", "/api/urls/{shortCode}"})
    public UrlMetadataResponse get(
            @PathVariable String shortCode,
            HttpServletRequest httpRequest) {
        UrlMetadataResponse meta = urlService.get(shortCode);
        String dynamicBaseUrl = extractBaseUrl(httpRequest);
        if (dynamicBaseUrl != null && !dynamicBaseUrl.isBlank()) {
            return new UrlMetadataResponse(meta.shortCode(), dynamicBaseUrl + "/" + meta.shortCode(),
                    meta.longUrl(), meta.createdAt(), meta.expiresAt(), meta.customAlias());
        }
        return meta;
    }

    private String extractBaseUrl(HttpServletRequest request) {
        if (request == null) return null;
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String host = (forwardedHost != null && !forwardedHost.isBlank()) ? forwardedHost : request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return null;
        }
        // Exclude internal container and localhost names
        if (host.startsWith("localhost") || host.startsWith("127.0.0.1") ||
            host.startsWith("app1") || host.startsWith("app2") || host.startsWith("app_backend") || host.equals("nginx")) {
            return null;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) {
            proto = request.isSecure() ? "https" : "http";
        }
        return proto + "://" + host;
    }

    /**
     * Update link destination or expiry. Requires X-Manage-Token header for authorization.
     */
    @PutMapping({"/api/links/{shortCode}", "/api/urls/{shortCode}"})
    public UrlMetadataResponse update(
            @PathVariable String shortCode,
            @RequestBody UpdateUrlRequest request,
            @RequestHeader(value = "X-Manage-Token", required = false) String manageToken,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        Instant expiry = request.expiresAt() != null
                ? request.expiresAt()
                : (request.ttlSeconds() != null ? Instant.now().plusSeconds(request.ttlSeconds()) : null);
        return urlService.update(shortCode, request.longUrl(), expiry, manageToken, rid);
    }

    /**
     * Delete link. Requires X-Manage-Token header for authorization.
     */
    @DeleteMapping({"/api/links/{shortCode}", "/api/urls/{shortCode}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Manage-Token", required = false) String manageToken,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        urlService.delete(shortCode, manageToken, rid);
    }

    /**
     * List recent links (scatter-gather across shards).
     */
    @GetMapping({"/api/links", "/api/urls"})
    public Page<UrlMetadataResponse> listRecent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return urlService.listRecent(PageRequest.of(safePage, safeSize));
    }

    /**
     * The hot path: GET /{shortCode} -> 302 Found redirect with non-blocking click telemetry.
     */
    @GetMapping("/{shortCode:[0-9a-zA-Z_-]{1,32}}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = "Referer", required = false) String referrer,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        String longUrl = urlService.resolve(shortCode, referrer, userAgent, rid);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}
