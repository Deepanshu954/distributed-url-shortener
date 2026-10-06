package io.portfolio.urlshortener.analytics.service;

import io.portfolio.urlshortener.analytics.dto.StatsResponse;
import io.portfolio.urlshortener.analytics.entity.LinkStats;
import io.portfolio.urlshortener.analytics.repository.LinkStatsRepository;
import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AnalyticsService {

    private final LinkStatsRepository linkStatsRepository;
    private final EventPublisher publisher;

    public AnalyticsService(LinkStatsRepository linkStatsRepository, EventPublisher publisher) {
        this.linkStatsRepository = linkStatsRepository;
        this.publisher = publisher;
    }

    public StatsResponse getStats(String shortCode) {
        LinkStats stats = linkStatsRepository.findById(shortCode)
                .orElse(new LinkStats(shortCode, 0, null, null));

        return new StatsResponse(
                stats.getShortCode(),
                stats.getClickCount(),
                stats.getLastClickAt(),
                stats.getLastReferrer()
        );
    }

    public void recordEdgeClick(String shortCode, String referrer, String userAgent, String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        publisher.publishClick(ClickEvent.of(shortCode, referrer, userAgent, rid));
    }
}
