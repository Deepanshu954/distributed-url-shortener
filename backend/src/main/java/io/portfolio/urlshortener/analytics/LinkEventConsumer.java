package io.portfolio.urlshortener.analytics;

import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.UrlCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LinkEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(LinkEventConsumer.class);

    private final RawLinkEventRepository rawLinkEventRepository;
    private final LinkStatsRepository linkStatsRepository;
    private final UrlCache urlCache;

    public LinkEventConsumer(RawLinkEventRepository rawLinkEventRepository,
                             LinkStatsRepository linkStatsRepository,
                             UrlCache urlCache) {
        this.rawLinkEventRepository = rawLinkEventRepository;
        this.linkStatsRepository = linkStatsRepository;
        this.urlCache = urlCache;
    }

    @KafkaListener(topics = "link-events", groupId = "link-index", autoStartup = "${app.kafka.enabled:false}")
    @Transactional("analyticsTransactionManager")
    public void consume(LinkEvent event) {
        log.debug("Consumed link event: {}", event.eventId());
        int rows = rawLinkEventRepository.insertIgnore(event.eventId(), event.timestamp());
        if (rows == 0) {
            log.debug("Skipped duplicate link event: {}", event.eventId());
            return;
        }

        switch (event.type()) {
            case CREATED -> {
                log.debug("Link created: {}", event.shortCode());
                if (linkStatsRepository != null && !linkStatsRepository.existsById(event.shortCode())) {
                    try {
                        linkStatsRepository.save(new LinkStats(event.shortCode(), 0, null, null));
                    } catch (Exception ignored) {
                    }
                }
            }
            case DELETED -> urlCache.evict(event.shortCode());
            case UPDATED -> urlCache.evict(event.shortCode());
        }
    }
}
