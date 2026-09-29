package io.portfolio.urlshortener.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface LinkStatsRepository extends JpaRepository<LinkStats, String> {

    @Modifying
    @Transactional("analyticsTransactionManager")
    @Query("UPDATE LinkStats s SET s.clickCount = s.clickCount + 1, s.lastClickAt = :clickedAt, s.lastReferrer = COALESCE(:referrer, s.lastReferrer) WHERE s.shortCode = :shortCode")
    int updateExistingStats(@Param("shortCode") String shortCode,
                            @Param("clickedAt") Instant clickedAt,
                            @Param("referrer") String referrer);

    default void incrementClickCount(String shortCode, Instant clickedAt, String referrer) {
        int updated = updateExistingStats(shortCode, clickedAt, referrer);
        if (updated == 0) {
            try {
                saveAndFlush(new LinkStats(shortCode, 1, clickedAt, referrer));
            } catch (Exception e) {
                // If another thread inserted the row concurrently, apply the increment
                updateExistingStats(shortCode, clickedAt, referrer);
            }
        }
    }
}
