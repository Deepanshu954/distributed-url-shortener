package io.portfolio.urlshortener.shortener.service;

import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.UrlCache;
import io.portfolio.urlshortener.contracts.UrlCache.CacheResult;
import io.portfolio.urlshortener.contracts.UrlCache.Hit;
import io.portfolio.urlshortener.contracts.UrlCache.NegativeHit;
import io.portfolio.urlshortener.sharding.ShardProperties;
import io.portfolio.urlshortener.shortener.AliasConflictException;
import io.portfolio.urlshortener.shortener.Base62;
import io.portfolio.urlshortener.shortener.InfraUnavailableException;
import io.portfolio.urlshortener.shortener.NotFoundException;
import io.portfolio.urlshortener.shortener.SnowflakeIdGenerator;
import io.portfolio.urlshortener.shortener.UnauthorizedException;
import io.portfolio.urlshortener.shortener.UrlValidator;
import io.portfolio.urlshortener.shortener.ValidationException;
import io.portfolio.urlshortener.shortener.dto.CreateUrlRequest;
import io.portfolio.urlshortener.shortener.dto.UrlMetadataResponse;
import io.portfolio.urlshortener.shortener.dto.UrlResponse;
import io.portfolio.urlshortener.shortener.entity.IdempotencyKey;
import io.portfolio.urlshortener.shortener.entity.Url;
import io.portfolio.urlshortener.shortener.repository.IdempotencyKeyRepository;
import io.portfolio.urlshortener.shortener.repository.UrlRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UrlService {

    static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);
    static final Duration MAX_CACHE_TTL = Duration.ofHours(24);
    static final int NON_HOLDER_RETRIES = 3;
    static final long DEFAULT_RETRY_DELAY_MS = 50;

    public static final int DEFAULT_CODE_LENGTH = 5;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UrlService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final UrlValidator validator;
    private final UrlRepository urlRepository;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final ShardRouter router;
    private final EventPublisher publisher;
    private final UrlCache urlCache;
    private final ShardProperties shardProperties;
    private final String baseUrl;
    private final int codeLength;
    private final long retryDelayMs;

    @org.springframework.beans.factory.annotation.Autowired
    public UrlService(SnowflakeIdGenerator idGenerator,
                      UrlValidator validator,
                      UrlRepository urlRepository,
                      IdempotencyKeyRepository idempotencyKeys,
                      ShardRouter router,
                      EventPublisher publisher,
                      ObjectProvider<UrlCache> urlCacheProvider,
                      ObjectProvider<ShardProperties> shardPropertiesProvider,
                      @Value("${app.base-url:http://localhost:8080}") String baseUrl,
                      @Value("${app.shortener.code-length:5}") int codeLength) {
        this.idGenerator = idGenerator;
        this.validator = validator;
        this.urlRepository = urlRepository;
        this.idempotencyKeys = idempotencyKeys;
        this.router = router;
        this.publisher = publisher;
        this.urlCache = urlCacheProvider != null ? urlCacheProvider.getIfAvailable() : null;
        this.shardProperties = shardPropertiesProvider != null ? shardPropertiesProvider.getIfAvailable() : null;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.codeLength = codeLength > 0 ? codeLength : DEFAULT_CODE_LENGTH;
        this.retryDelayMs = DEFAULT_RETRY_DELAY_MS;
    }

    public UrlService(SnowflakeIdGenerator idGenerator,
                      UrlValidator validator,
                      UrlRepository urlRepository,
                      IdempotencyKeyRepository idempotencyKeys,
                      ShardRouter router,
                      EventPublisher publisher,
                      UrlCache urlCache,
                      String baseUrl) {
        this(idGenerator, validator, urlRepository, idempotencyKeys, router, publisher,
             urlCache, baseUrl, DEFAULT_CODE_LENGTH);
    }

    public UrlService(SnowflakeIdGenerator idGenerator,
                      UrlValidator validator,
                      UrlRepository urlRepository,
                      IdempotencyKeyRepository idempotencyKeys,
                      ShardRouter router,
                      EventPublisher publisher,
                      UrlCache urlCache,
                      String baseUrl,
                      int codeLength) {
        this.idGenerator = idGenerator;
        this.validator = validator;
        this.urlRepository = urlRepository;
        this.idempotencyKeys = idempotencyKeys;
        this.router = router;
        this.publisher = publisher;
        this.urlCache = urlCache;
        this.shardProperties = null;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.codeLength = codeLength > 0 ? codeLength : DEFAULT_CODE_LENGTH;
        this.retryDelayMs = 1;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public record CreationResult(UrlResponse url, boolean replayed) {
    }

    /**
     * Shortens a long URL into an enterprise-grade short code.
     * Generates a unique manageToken for IDOR protection on future mutations/deletions.
     */
    public CreationResult create(CreateUrlRequest request, String idempotencyKey, String requestId, Long userId) {
        Instant now = Instant.now();

        if (hasText(idempotencyKey)) {
            Optional<UrlResponse> replay = findReplay(idempotencyKey, now);
            if (replay.isPresent()) {
                log.debug("Idempotent replay served for key='{}'", idempotencyKey);
                return new CreationResult(replay.get(), true);
            }
        }

        validator.validateUrl(request.longUrl());
        Instant expiresAt = resolveExpiry(request, now);

        boolean custom = hasText(request.customAlias());
        int maxAttempts = custom ? 1 : 5;
        int attempts = 0;
        String shortCode = null;
        String manageToken = UUID.randomUUID().toString().replace("-", "");
        Url url = null;

        while (attempts < maxAttempts) {
            attempts++;
            long id = idGenerator.nextId();

            if (custom) {
                validator.validateAlias(request.customAlias());
                shortCode = request.customAlias();
                String code = shortCode;
                boolean taken = router.executeRead(code, () -> urlRepository.existsByShortCode(code));
                if (taken) {
                    throw new AliasConflictException(code);
                }
            } else {
                shortCode = Base62.encode(id, codeLength);
            }

            Url finalUrl = new Url(id, shortCode, request.longUrl(), userId, manageToken, now, expiresAt, custom);
            url = finalUrl;
            try {
                router.executeWrite(shortCode, () -> urlRepository.save(finalUrl));
                break;
            } catch (DataIntegrityViolationException e) {
                if (custom || attempts == maxAttempts) {
                    throw new AliasConflictException(shortCode);
                }
                log.debug("Collision on shortCode='{}' (attempt {}/{}), retrying with next snowflake id",
                        shortCode, attempts, maxAttempts);
            }
        }

        if (hasText(idempotencyKey)) {
            IdempotencyKey record = new IdempotencyKey(idempotencyKey, shortCode, now);
            try {
                router.executeWrite(idempotencyKey, () -> idempotencyKeys.save(record));
            } catch (DataIntegrityViolationException e) {
                Optional<UrlResponse> replay = findReplay(idempotencyKey, now);
                if (replay.isPresent()) {
                    return new CreationResult(replay.get(), true);
                }
            }
        }

        if (urlCache != null) {
            urlCache.evict(shortCode);
        }

        log.info("Shortened URL minted: code={}, length={}, id={}, custom={}", shortCode, shortCode.length(), url.getId(), custom);
        publisher.publishLinkEvent(LinkEvent.of(LinkEvent.Type.CREATED, shortCode, userId, requestId));
        return new CreationResult(toResponse(url), false);
    }

    /**
     * Resolves shortCode to destination long URL with cache-aside, mutex locking, and telemetry.
     */
    public String resolve(String shortCode, String referrer, String userAgent, String requestId) {
        String longUrl = resolveLongUrl(shortCode);
        log.debug("Resolved redirect: code={} -> {} (requestId={})", shortCode, longUrl, requestId);
        publisher.publishClick(ClickEvent.of(shortCode, referrer, userAgent, requestId));
        return longUrl;
    }

    private String resolveLongUrl(String shortCode) {
        if (urlCache != null) {
            CacheResult cached = urlCache.get(shortCode);
            if (cached instanceof Hit hit) {
                return hit.longUrl();
            }
            if (cached instanceof NegativeHit) {
                throw new NotFoundException(shortCode);
            }

            if (urlCache.tryLock(shortCode)) {
                try {
                    return loadAndPopulate(shortCode);
                } finally {
                    urlCache.unlock(shortCode);
                }
            }
            return awaitHolderOrReadThrough(shortCode);
        }

        return loadDirect(shortCode);
    }

    private String loadAndPopulate(String shortCode) {
        Url url = loadFromDb(shortCode);
        Instant now = Instant.now();
        if (url == null || url.isExpired(now)) {
            if (urlCache != null) {
                urlCache.putNegative(shortCode);
            }
            throw new NotFoundException(shortCode);
        }
        if (urlCache != null) {
            urlCache.put(shortCode, url.getLongUrl(), cacheTtl(url, now));
        }
        return url.getLongUrl();
    }

    private String awaitHolderOrReadThrough(String shortCode) {
        for (int attempt = 0; attempt < NON_HOLDER_RETRIES; attempt++) {
            if (!sleepQuietly(retryDelayMs)) {
                break;
            }
            if (urlCache != null) {
                CacheResult retried = urlCache.get(shortCode);
                if (retried instanceof Hit hit) {
                    return hit.longUrl();
                }
                if (retried instanceof NegativeHit) {
                    throw new NotFoundException(shortCode);
                }
            }
        }
        return loadDirect(shortCode);
    }

    private String loadDirect(String shortCode) {
        Url url = loadFromDb(shortCode);
        if (url == null || url.isExpired(Instant.now())) {
            throw new NotFoundException(shortCode);
        }
        return url.getLongUrl();
    }

    private Url loadFromDb(String shortCode) {
        try {
            return router.executeRead(shortCode, () -> urlRepository.findByShortCode(shortCode)).orElse(null);
        } catch (RuntimeException e) {
            throw new InfraUnavailableException("link storage unavailable", e);
        }
    }

    public UrlMetadataResponse get(String shortCode) {
        Url url = router.executeRead(shortCode, () -> urlRepository.findByShortCode(shortCode))
                .orElseThrow(() -> new NotFoundException(shortCode));
        if (url.isExpired(Instant.now())) {
            throw new NotFoundException(shortCode);
        }
        return toMetadataResponse(url);
    }

    /**
     * Updates an existing link. Enforces manageToken authorization if token is set.
     */
    public UrlMetadataResponse update(String shortCode, String longUrl, Instant expiresAt, String manageToken, String requestId) {
        if (hasText(longUrl)) {
            validator.validateUrl(longUrl);
        }
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ValidationException("expiresAt must be in the future");
        }

        Url updated = router.executeWrite(shortCode, () -> {
            Url url = urlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new NotFoundException(shortCode));

            if (url.getManageToken() != null && !url.getManageToken().equals(manageToken)) {
                throw new UnauthorizedException("invalid or missing manage token for this link");
            }

            if (hasText(longUrl)) {
                url.setLongUrl(longUrl);
            }
            if (expiresAt != null) {
                url.setExpiresAt(expiresAt);
            }
            return urlRepository.save(url);
        });

        if (urlCache != null) {
            urlCache.evict(shortCode);
        }

        publisher.publishLinkEvent(LinkEvent.of(LinkEvent.Type.UPDATED, shortCode, null, requestId));
        return toMetadataResponse(updated);
    }

    /**
     * Deletes a short link. Enforces manageToken authorization if token is set.
     */
    public void delete(String shortCode, String manageToken, String requestId) {
        router.executeWrite(shortCode, () -> {
            Url url = urlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new NotFoundException(shortCode));

            if (url.getManageToken() != null && !url.getManageToken().equals(manageToken)) {
                throw new UnauthorizedException("invalid or missing manage token for this link");
            }

            urlRepository.delete(url);
            return null;
        });

        if (urlCache != null) {
            urlCache.evict(shortCode);
        }

        publisher.publishLinkEvent(LinkEvent.of(LinkEvent.Type.DELETED, shortCode, null, requestId));
    }

    /**
     * Scatter-gather query across all configured shards to return recent links globally.
     */
    public Page<UrlMetadataResponse> listRecent(Pageable pageable) {
        List<String> shards = (shardProperties != null && !shardProperties.shardNames().isEmpty())
                ? shardProperties.shardNames()
                : List.of("shard1");

        List<Url> combined = new ArrayList<>();
        for (String shard : shards) {
            try {
                Page<Url> shardPage = router.executeRead(shard, () -> urlRepository.findAllByOrderByCreatedAtDesc(pageable));
                combined.addAll(shardPage.getContent());
            } catch (Exception ignored) {
                // Ignore shard read failure gracefully during scatter
            }
        }

        combined.sort(Comparator.comparing(Url::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), combined.size());
        List<UrlMetadataResponse> pageContent = (start <= combined.size())
                ? combined.subList(start, end).stream().map(this::toMetadataResponse).toList()
                : List.of();

        return new PageImpl<>(pageContent, pageable, combined.size());
    }

    private Optional<UrlResponse> findReplay(String idempotencyKey, Instant now) {
        Instant windowStart = now.minus(IDEMPOTENCY_WINDOW);
        return router.executeRead(idempotencyKey, () -> idempotencyKeys.findById(idempotencyKey))
                .filter(k -> k.getCreatedAt().isAfter(windowStart))
                .flatMap(k -> router.executeRead(k.getShortCode(), () -> urlRepository.findByShortCode(k.getShortCode())))
                .map(this::toResponse);
    }

    private Instant resolveExpiry(CreateUrlRequest request, Instant now) {
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

    private static Duration cacheTtl(Url url, Instant now) {
        double jitter = ThreadLocalRandom.current().nextDouble(0.9, 1.1); // +/- 10% jitter (ADR-004)
        if (url.getExpiresAt() == null) {
            return Duration.ofMillis((long) (MAX_CACHE_TTL.toMillis() * jitter));
        }
        Duration remaining = Duration.between(now, url.getExpiresAt());
        Duration base = remaining.compareTo(MAX_CACHE_TTL) < 0 ? remaining : MAX_CACHE_TTL;
        return Duration.ofMillis((long) (base.toMillis() * jitter));
    }

    private UrlResponse toResponse(Url url) {
        return new UrlResponse(url.getShortCode(), shortUrlFor(url.getShortCode()),
                url.getLongUrl(), url.getExpiresAt(), url.getManageToken());
    }

    private UrlMetadataResponse toMetadataResponse(Url url) {
        return new UrlMetadataResponse(url.getShortCode(), shortUrlFor(url.getShortCode()),
                url.getLongUrl(), url.getCreatedAt(), url.getExpiresAt(), url.isCustomAlias());
    }

    private String shortUrlFor(String shortCode) {
        return baseUrl + "/" + shortCode;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
