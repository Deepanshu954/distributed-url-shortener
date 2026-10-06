package io.portfolio.urlshortener.shortener.repository;

import io.portfolio.urlshortener.shortener.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}
