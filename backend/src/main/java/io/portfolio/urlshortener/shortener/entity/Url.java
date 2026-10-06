package io.portfolio.urlshortener.shortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "links")
public class Url {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 32, unique = true)
    private String shortCode;

    @Column(name = "long_url", nullable = false, length = 8192)
    private String longUrl;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "manage_token", length = 64)
    private String manageToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_custom_alias", nullable = false)
    private boolean isCustomAlias;

    protected Url() {
    }

    public Url(Long id, String shortCode, String longUrl, Long userId, String manageToken,
               Instant createdAt, Instant expiresAt, boolean isCustomAlias) {
        this.id = id;
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.userId = userId;
        this.manageToken = manageToken;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.isCustomAlias = isCustomAlias;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public Long getUserId() {
        return userId;
    }

    public String getManageToken() {
        return manageToken;
    }

    public void setManageToken(String manageToken) {
        this.manageToken = manageToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isCustomAlias() {
        return isCustomAlias;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Url url)) return false;
        return Objects.equals(id, url.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
