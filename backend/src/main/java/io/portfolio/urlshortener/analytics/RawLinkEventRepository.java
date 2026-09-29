package io.portfolio.urlshortener.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;

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
