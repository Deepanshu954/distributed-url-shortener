package io.portfolio.urlshortener.analytics.repository;

import io.portfolio.urlshortener.analytics.entity.RawLinkEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface RawLinkEventRepository extends JpaRepository<RawLinkEvent, UUID> {
    default int insertIgnore(UUID eventId, Instant createdAt) {
        if (existsById(eventId)) {
            return 0;
        }
        try {
            save(new RawLinkEvent(eventId, createdAt));
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
