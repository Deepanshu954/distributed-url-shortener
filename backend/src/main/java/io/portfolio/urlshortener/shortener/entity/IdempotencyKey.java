package io.portfolio.urlshortener.shortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "\"key\"", length = 128)
    private String key;

    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String key, String shortCode, Instant createdAt) {
        this.key = key;
        this.shortCode = shortCode;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
