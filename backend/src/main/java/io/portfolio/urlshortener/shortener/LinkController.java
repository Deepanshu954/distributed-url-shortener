package io.portfolio.urlshortener.shortener;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Public link management API.
 * Provides frictionless shortening, querying, updating, and deleting of short links.
 */
@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final ShortenService shortenService;

    public LinkController(ShortenService shortenService) {
        this.shortenService = shortenService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(
            @RequestBody CreateLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        ShortenService.CreationResult result = shortenService.create(request, idempotencyKey, rid, null);
        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.link());
    }

    @GetMapping("/{shortCode}")
    public LinkMetadataResponse get(@PathVariable String shortCode) {
        return shortenService.getLink(shortCode);
    }

    @PutMapping("/{shortCode}")
    public LinkMetadataResponse update(
            @PathVariable String shortCode,
            @RequestBody UpdateLinkRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        Instant expiry = request.expiresAt() != null
                ? request.expiresAt()
                : (request.ttlSeconds() != null ? Instant.now().plusSeconds(request.ttlSeconds()) : null);
        return shortenService.updateLink(shortCode, request.longUrl(), expiry, rid);
    }

    @DeleteMapping("/{shortCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        shortenService.deleteLink(shortCode, rid);
    }

    @GetMapping
    public Page<LinkMetadataResponse> listRecent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return shortenService.listRecentLinks(PageRequest.of(safePage, safeSize));
    }
}
