package io.portfolio.urlshortener.analytics.repository;

import io.portfolio.urlshortener.analytics.entity.RawClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface RawClickEventRepository extends JpaRepository<RawClickEvent, UUID> {
    default int insertIgnore(UUID eventId, Instant createdAt) {
        if (existsById(eventId)) {
            return 0;
        }
        try {
            save(new RawClickEvent(eventId, createdAt));
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
