package io.portfolio.urlshortener.shortener;

import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.UrlCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Link creation, updates, deletions and metadata reads.
 * All repository access is routed through the {@link ShardRouter}.
 */
@Service
public class ShortenService {

    static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);

    private final SnowflakeIdGenerator idGenerator;
    private final UrlValidator validator;
    private final LinkRepository links;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final ShardRouter router;
    private final EventPublisher publisher;
    private final UrlCache urlCache;
    private final String baseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public ShortenService(SnowflakeIdGenerator idGenerator,
                          UrlValidator validator,
                          LinkRepository links,
                          IdempotencyKeyRepository idempotencyKeys,
                          ShardRouter router,
                          EventPublisher publisher,
                          ObjectProvider<UrlCache> urlCacheProvider,
                          @Value("${app.base-url}") String baseUrl) {
        this.idGenerator = idGenerator;
        this.validator = validator;
        this.links = links;
        this.idempotencyKeys = idempotencyKeys;
        this.router = router;
        this.publisher = publisher;
        this.urlCache = urlCacheProvider != null ? urlCacheProvider.getIfAvailable() : null;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public ShortenService(SnowflakeIdGenerator idGenerator,
                          UrlValidator validator,
                          LinkRepository links,
                          IdempotencyKeyRepository idempotencyKeys,
                          ShardRouter router,
                          EventPublisher publisher,
                          UrlCache urlCache,
                          String baseUrl) {
        this.idGenerator = idGenerator;
        this.validator = validator;
        this.links = links;
        this.idempotencyKeys = idempotencyKeys;
        this.router = router;
        this.publisher = publisher;
        this.urlCache = urlCache;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public ShortenService(SnowflakeIdGenerator idGenerator,
                          UrlValidator validator,
                          LinkRepository links,
                          IdempotencyKeyRepository idempotencyKeys,
                          ShardRouter router,
                          EventPublisher publisher,
                          String baseUrl) {
        this(idGenerator, validator, links, idempotencyKeys, router, publisher, (UrlCache) null, baseUrl);
    }

    /** {@code replayed} = served from the idempotency table → controller returns 200 not 201. */
    public record CreationResult(LinkResponse link, boolean replayed) {
    }

    public CreationResult create(CreateLinkRequest request, String idempotencyKey, String requestId, Long userId) {
        Instant now = Instant.now();

        if (hasText(idempotencyKey)) {
            Optional<LinkResponse> replay = findReplay(idempotencyKey, now);
            if (replay.isPresent()) {
                return new CreationResult(replay.get(), true);
            }
        }

        validator.validateUrl(request.longUrl());
        Instant expiresAt = resolveExpiry(request, now);

        boolean custom = hasText(request.customAlias());
        
        int maxAttempts = custom ? 1 : 3;
        int attempts = 0;
        String shortCode = null;
        Link link = null;
        
        while (attempts < maxAttempts) {
            attempts++;
            long id = idGenerator.nextId();
            
            if (custom) {
                validator.validateAlias(request.customAlias());
                shortCode = request.customAlias();
                String code = shortCode;
                boolean taken = router.executeRead(code, () -> links.existsByShortCode(code));
                if (taken) {
                    throw new AliasConflictException(code);
                }
            } else {
                shortCode = Base62.encode(id);
            }

            Link finalLink = new Link(id, shortCode, request.longUrl(), userId, now, expiresAt, custom);
            link = finalLink;
            try {
                router.executeWrite(shortCode, () -> links.save(finalLink));
                break; // Success
            } catch (DataIntegrityViolationException e) {
                if (custom || attempts == maxAttempts) {
                    throw new AliasConflictException(shortCode);
                }
            }
        }

        if (hasText(idempotencyKey)) {
            IdempotencyKey record = new IdempotencyKey(idempotencyKey, shortCode, now);
            try {
                router.executeWrite(idempotencyKey, () -> idempotencyKeys.save(record));
            } catch (DataIntegrityViolationException e) {
                // Concurrent replay race: check if another request saved the idempotency key
                Optional<LinkResponse> replay = findReplay(idempotencyKey, now);
                if (replay.isPresent()) {
                    return new CreationResult(replay.get(), true);
                }
            }
        }

        // Invalidate any potential negative-cache entry (NF sentinel) from pre-creation probes
        if (urlCache != null) {
            urlCache.evict(shortCode);
        }

        publisher.publishLinkEvent(LinkEvent.of(LinkEvent.Type.CREATED, shortCode, userId, requestId));
        return new CreationResult(toResponse(link), false);
    }

    /** Public metadata read; expired links are indistinguishable from unknown (404). */
    public LinkMetadataResponse getLink(String shortCode) {
        Link link = router.executeRead(shortCode, () -> links.findByShortCode(shortCode))
                .orElseThrow(() -> new NotFoundException(shortCode));
        if (link.isExpired(Instant.now())) {
            throw new NotFoundException(shortCode);
        }
        return toMetadataResponse(link);
    }

    /** Public link update: mutates destination URL or expiry and immediately evicts from cache. */
    public LinkMetadataResponse updateLink(String shortCode, String longUrl, Instant expiresAt, String requestId) {
        if (hasText(longUrl)) {
            validator.validateUrl(longUrl);
        }
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ValidationException("expiresAt must be in the future");
        }

        Link updated = router.executeWrite(shortCode, () -> {
            Link link = links.findByShortCode(shortCode).orElseThrow(() -> new NotFoundException(shortCode));
            if (hasText(longUrl)) {
                link.setLongUrl(longUrl);
            }
            if (expiresAt != null) {
                link.setExpiresAt(expiresAt);
            }
            return links.save(link);
        });

        // Synchronously evict cache to prevent serving stale destination URLs
        if (urlCache != null) {
            urlCache.evict(shortCode);
        }

        publisher.publishLinkEvent(LinkEvent.of(LinkEvent.Type.UPDATED, shortCode, null, requestId));
        return toMetadataResponse(updated);
    }

    /** Public link deletion: removes from DB and immediately evicts from cache. */
    public void deleteLink(String shortCode, String requestId) {
        router.executeWrite(shortCode, () -> {
            Link link = links.findByShortCode(shortCode).orElseThrow(() -> new NotFoundException(shortCode));
            links.delete(link);
            return null;
        });

        if (urlCache != null) {
            urlCache.evict(shortCode);
        }

        publisher.publishLinkEvent(LinkEvent.of(LinkEvent.Type.DELETED, shortCode, null, requestId));
    }

    /** List recent links paged. */
    public Page<LinkMetadataResponse> listRecentLinks(Pageable pageable) {
        return router.executeRead("shard1", () -> links.findAllByOrderByCreatedAtDesc(pageable))
                .map(this::toMetadataResponse);
    }

    private Optional<LinkResponse> findReplay(String idempotencyKey, Instant now) {
        Instant windowStart = now.minus(IDEMPOTENCY_WINDOW);
        return router.executeRead(idempotencyKey, () -> idempotencyKeys.findById(idempotencyKey))
                .filter(k -> k.getCreatedAt().isAfter(windowStart))
                .flatMap(k -> router.executeRead(k.getShortCode(), () -> links.findByShortCode(k.getShortCode())))
                .map(this::toResponse);
    }

    private Instant resolveExpiry(CreateLinkRequest request, Instant now) {
        if (request.expiresAt() != null) {
            if (!request.expiresAt().isAfter(now)) {
                throw new ValidationException("expiresAt must be in the future");
            }
            return request.expiresAt();
        }
        if (request.ttlSeconds() != null) {
            if (request.ttlSeconds() <= 0) {
                throw new ValidationException("ttlSeconds must be positive");
            }
            return now.plusSeconds(request.ttlSeconds());
        }
        return null;
    }

    private LinkResponse toResponse(Link link) {
        return new LinkResponse(link.getShortCode(), shortUrlFor(link.getShortCode()),
                link.getLongUrl(), link.getExpiresAt());
    }

    private LinkMetadataResponse toMetadataResponse(Link link) {
        return new LinkMetadataResponse(link.getShortCode(), shortUrlFor(link.getShortCode()),
                link.getLongUrl(), link.getCreatedAt(), link.getExpiresAt(), link.isCustomAlias());
    }

    private String shortUrlFor(String shortCode) {
        return baseUrl + "/" + shortCode;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
