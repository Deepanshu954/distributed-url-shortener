package io.portfolio.urlshortener.shortener;

import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.UrlCache;
import io.portfolio.urlshortener.contracts.UrlCache.Hit;
import io.portfolio.urlshortener.contracts.UrlCache.Miss;
import io.portfolio.urlshortener.contracts.UrlCache.NegativeHit;
import io.portfolio.urlshortener.shortener.dto.CreateUrlRequest;
import io.portfolio.urlshortener.shortener.dto.UrlMetadataResponse;
import io.portfolio.urlshortener.shortener.entity.IdempotencyKey;
import io.portfolio.urlshortener.shortener.entity.Url;
import io.portfolio.urlshortener.shortener.repository.IdempotencyKeyRepository;
import io.portfolio.urlshortener.shortener.repository.UrlRepository;
import io.portfolio.urlshortener.shortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrlServiceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String LONG_URL = "https://example.com/some/long/path";
    private static final String REQUEST_ID = "req-123";

    @Mock
    private UrlRepository urlRepository;
    @Mock
    private IdempotencyKeyRepository idempotencyKeys;
    @Mock
    private ShardRouter router;
    @Mock
    private EventPublisher publisher;
    @Mock
    private UrlCache urlCache;

    private UrlService service;

    @BeforeEach
    void setUp() {
        lenient().when(router.executeRead(anyString(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(router.executeWrite(anyString(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(urlRepository.findByShortCode(anyString())).thenReturn(Optional.empty());
        lenient().when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        lenient().when(idempotencyKeys.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(idempotencyKeys.save(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new UrlService(new SnowflakeIdGenerator(1), new UrlValidator(),
                urlRepository, idempotencyKeys, router, publisher, urlCache, BASE_URL);
    }

    private static CreateUrlRequest request(String longUrl) {
        return new CreateUrlRequest(longUrl, null, null, null);
    }

    private static Url urlEntity(String code, Instant expiresAt, String manageToken) {
        return new Url(1L, code, LONG_URL, 1L, manageToken, Instant.now().minusSeconds(60), expiresAt, false);
    }

    @Test
    void createsUrlWithBase62CodeAndPublishesCreatedEvent() {
        var result = service.create(request(LONG_URL), null, REQUEST_ID, null);

        assertThat(result.replayed()).isFalse();
        assertThat(result.url().longUrl()).isEqualTo(LONG_URL);
        assertThat(result.url().shortCode()).hasSize(5).matches("[0-9a-zA-Z]{5}");
        assertThat(result.url().shortUrl()).isEqualTo(BASE_URL + "/" + result.url().shortCode());
        assertThat(result.url().expiresAt()).isNull();
        assertThat(result.url().manageToken()).isNotBlank();

        ArgumentCaptor<Url> saved = ArgumentCaptor.forClass(Url.class);
        verify(urlRepository).save(saved.capture());
        assertThat(Base62.encode(saved.getValue().getId(), 5)).isEqualTo(result.url().shortCode());
        assertThat(saved.getValue().isCustomAlias()).isFalse();

        ArgumentCaptor<LinkEvent> event = ArgumentCaptor.forClass(LinkEvent.class);
        verify(publisher).publishLinkEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(LinkEvent.Type.CREATED);
        assertThat(event.getValue().shortCode()).isEqualTo(result.url().shortCode());
        assertThat(event.getValue().requestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void customConfiguredCodeLengthIsRespected() {
        UrlService serviceWith7 = new UrlService(new SnowflakeIdGenerator(1), new UrlValidator(),
                urlRepository, idempotencyKeys, router, publisher, urlCache, BASE_URL, 7);
        var result = serviceWith7.create(request(LONG_URL), null, REQUEST_ID, null);
        assertThat(result.url().shortCode()).hasSize(7).matches("[0-9a-zA-Z]{7}");
    }

    @Test
    void routesWriteByShortCode() {
        var result = service.create(request(LONG_URL), null, REQUEST_ID, null);
        verify(router).executeWrite(eq(result.url().shortCode()), any());
    }

    @Test
    void sameLongUrlTwiceYieldsDistinctCodes_noDedup() {
        var first = service.create(request(LONG_URL), null, REQUEST_ID, null);
        var second = service.create(request(LONG_URL), null, REQUEST_ID, null);
        assertThat(first.url().shortCode()).isNotEqualTo(second.url().shortCode());
    }

    @Test
    void invalidUrlRejectedBeforeAnyPersistence() {
        assertThatThrownBy(() -> service.create(request("ftp://example.com"), null, REQUEST_ID, null))
                .isInstanceOf(ValidationException.class);
        verify(urlRepository, never()).save(any());
        verify(publisher, never()).publishLinkEvent(any());
    }

    @Test
    void ttlSecondsBecomesExpiresAt() {
        var result = service.create(new CreateUrlRequest(LONG_URL, null, null, 3600L), null, REQUEST_ID, null);
        assertThat(result.url().expiresAt())
                .isCloseTo(Instant.now().plusSeconds(3600), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void explicitExpiresAtWinsOverTtl() {
        Instant explicit = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        var result = service.create(new CreateUrlRequest(LONG_URL, null, explicit, 60L), null, REQUEST_ID, null);
        assertThat(result.url().expiresAt()).isEqualTo(explicit);
    }

    @Test
    void pastExpiresAtAndNonPositiveTtlAreRejected() {
        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest(LONG_URL, null, Instant.now().minusSeconds(60), null), null, REQUEST_ID, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest(LONG_URL, null, null, 0L), null, REQUEST_ID, null))
                .isInstanceOf(ValidationException.class);
    }

    // --- custom alias ---

    @Test
    void customAliasIsUsedAsShortCode() {
        var result = service.create(new CreateUrlRequest(LONG_URL, "my-alias", null, null), null, REQUEST_ID, null);
        assertThat(result.url().shortCode()).isEqualTo("my-alias");
        ArgumentCaptor<Url> saved = ArgumentCaptor.forClass(Url.class);
        verify(urlRepository).save(saved.capture());
        assertThat(saved.getValue().isCustomAlias()).isTrue();
    }

    @Test
    void takenAliasThrowsConflict() {
        when(urlRepository.existsByShortCode("my-alias")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest(LONG_URL, "my-alias", null, null), null, REQUEST_ID, null))
                .isInstanceOf(AliasConflictException.class);
        verify(urlRepository, never()).save(any());
    }

    @Test
    void aliasUniquenessRaceLostAtInsertThrowsConflict() {
        when(urlRepository.save(any(Url.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest(LONG_URL, "my-alias", null, null), null, REQUEST_ID, null))
                .isInstanceOf(AliasConflictException.class);
        verify(publisher, never()).publishLinkEvent(any());
    }

    @Test
    void blocklistedAliasRejected() {
        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest(LONG_URL, "actuator", null, null), null, REQUEST_ID, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reserved");
        verify(urlRepository, never()).save(any());
    }

    // --- idempotency ---

    @Test
    void idempotencyKeyReplayReturnsExistingUrlWithoutCreating() {
        Instant created = Instant.now().minusSeconds(60);
        when(idempotencyKeys.findById("idem-1"))
                .thenReturn(Optional.of(new IdempotencyKey("idem-1", "abc123", created)));
        when(urlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(new Url(1L, "abc123", LONG_URL, null, "tok1", created, null, false)));

        var result = service.create(request(LONG_URL), "idem-1", REQUEST_ID, null);

        assertThat(result.replayed()).isTrue();
        assertThat(result.url().shortCode()).isEqualTo("abc123");
        verify(urlRepository, never()).save(any());
        verify(publisher, never()).publishLinkEvent(any());
    }

    @Test
    void staleIdempotencyKeyOutside24hWindowIsIgnored() {
        Instant stale = Instant.now().minus(25, ChronoUnit.HOURS);
        when(idempotencyKeys.findById("idem-old"))
                .thenReturn(Optional.of(new IdempotencyKey("idem-old", "old123", stale)));

        var result = service.create(request(LONG_URL), "idem-old", REQUEST_ID, null);

        assertThat(result.replayed()).isFalse();
        verify(urlRepository).save(any(Url.class));
    }

    @Test
    void freshCreateWithIdempotencyKeyStoresTheKey() {
        var result = service.create(request(LONG_URL), "idem-new", REQUEST_ID, null);

        ArgumentCaptor<IdempotencyKey> stored = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeys).save(stored.capture());
        assertThat(stored.getValue().getKey()).isEqualTo("idem-new");
        assertThat(stored.getValue().getShortCode()).isEqualTo(result.url().shortCode());
        verify(router).executeWrite(eq("idem-new"), any());
    }

    // --- metadata read ---

    @Test
    void getUrlReturnsMetadata() {
        Instant created = Instant.now().minusSeconds(5);
        when(urlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(new Url(1L, "abc123", LONG_URL, null, "tok", created, null, true)));

        UrlMetadataResponse meta = service.get("abc123");

        assertThat(meta.shortCode()).isEqualTo("abc123");
        assertThat(meta.shortUrl()).isEqualTo(BASE_URL + "/abc123");
        assertThat(meta.longUrl()).isEqualTo(LONG_URL);
        assertThat(meta.customAlias()).isTrue();
    }

    @Test
    void getUrlUnknownOrExpiredThrowsNotFound() {
        when(urlRepository.findByShortCode("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("nope")).isInstanceOf(NotFoundException.class);

        when(urlRepository.findByShortCode("gone")).thenReturn(Optional.of(new Url(
                2L, "gone", LONG_URL, null, "tok", Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(60), false)));
        assertThatThrownBy(() -> service.get("gone")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void createEvictsCacheToClearAnyPreCreationNegativeCache() {
        var result = service.create(request(LONG_URL), null, REQUEST_ID, null);
        verify(urlCache).evict(result.url().shortCode());
    }

    @Test
    void updateUrlEnforcesManageToken() {
        Url existing = new Url(1L, "code1", LONG_URL, null, "secret-token", Instant.now().minusSeconds(10), null, false);
        when(urlRepository.findByShortCode("code1")).thenReturn(Optional.of(existing));

        // Wrong token -> throws UnauthorizedException
        assertThatThrownBy(() -> service.update("code1", "https://newdestination.com", null, "wrong-token", REQUEST_ID))
                .isInstanceOf(UnauthorizedException.class);

        // Correct token -> succeeds and evicts cache
        service.update("code1", "https://newdestination.com", null, "secret-token", REQUEST_ID);
        verify(urlCache).evict("code1");
    }

    @Test
    void deleteUrlEnforcesManageToken() {
        Url existing = new Url(1L, "code1", LONG_URL, null, "secret-token", Instant.now().minusSeconds(10), null, false);
        when(urlRepository.findByShortCode("code1")).thenReturn(Optional.of(existing));

        // Wrong token -> throws UnauthorizedException
        assertThatThrownBy(() -> service.delete("code1", "wrong-token", REQUEST_ID))
                .isInstanceOf(UnauthorizedException.class);

        // Correct token -> succeeds and evicts cache
        service.delete("code1", "secret-token", REQUEST_ID);
        verify(urlCache).evict("code1");
    }

    // --- resolve & redirect tests ---

    @Test
    void cacheHitRedirectsWithoutTouchingDbAndPublishesClick() {
        when(urlCache.get("abc123")).thenReturn(new Hit(LONG_URL));

        String url = service.resolve("abc123", "https://ref.example", "TestUA/1.0", "req-9");

        assertThat(url).isEqualTo(LONG_URL);
        verifyNoInteractions(router, urlRepository);

        ArgumentCaptor<ClickEvent> click = ArgumentCaptor.forClass(ClickEvent.class);
        verify(publisher).publishClick(click.capture());
        assertThat(click.getValue().shortCode()).isEqualTo("abc123");
        assertThat(click.getValue().referrer()).isEqualTo("https://ref.example");
        assertThat(click.getValue().userAgent()).isEqualTo("TestUA/1.0");
        assertThat(click.getValue().requestId()).isEqualTo("req-9");
    }

    @Test
    void negativeHitIs404WithoutDbAndWithoutClickEvent() {
        when(urlCache.get("abc123")).thenReturn(new NegativeHit());

        assertThatThrownBy(() -> service.resolve("abc123", null, null, "req"))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(router, urlRepository, publisher);
    }

    @Test
    void missWithLockLoadsDbAndCaches() {
        when(urlCache.get("abc123")).thenReturn(new Miss());
        when(urlCache.tryLock("abc123")).thenReturn(true);
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(urlEntity("abc123", null, "tok")));

        String url = service.resolve("abc123", null, null, "req");

        assertThat(url).isEqualTo(LONG_URL);
        verify(urlCache).put(eq("abc123"), eq(LONG_URL), any(Duration.class));
        verify(urlCache).unlock("abc123");
        verify(publisher).publishClick(any());
    }

    @Test
    void unknownCodeGetsNegativeCachedAnd404() {
        when(urlCache.get("abc123")).thenReturn(new Miss());
        when(urlCache.tryLock("abc123")).thenReturn(true);
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("abc123", null, null, "req"))
                .isInstanceOf(NotFoundException.class);

        verify(urlCache).putNegative("abc123");
        verify(urlCache).unlock("abc123");
        verify(urlCache, never()).put(anyString(), anyString(), any());
        verify(publisher, never()).publishClick(any());
    }

    @Test
    void dbFailureIsInfraUnavailableNever404() {
        when(urlCache.get("abc123")).thenReturn(new Miss());
        when(urlCache.tryLock("abc123")).thenReturn(true);
        when(urlRepository.findByShortCode("abc123")).thenThrow(new QueryTimeoutException("db down"));

        assertThatThrownBy(() -> service.resolve("abc123", null, null, "req"))
                .isInstanceOf(InfraUnavailableException.class)
                .isNotInstanceOf(NotFoundException.class);

        verify(urlCache).unlock("abc123");
        verify(urlCache, never()).putNegative(anyString());
        verify(publisher, never()).publishClick(any());
    }
}
