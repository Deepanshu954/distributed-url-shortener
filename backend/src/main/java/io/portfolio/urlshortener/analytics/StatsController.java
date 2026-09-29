package io.portfolio.urlshortener.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/links")
public class StatsController {

    private final LinkStatsRepository linkStatsRepository;

    public StatsController(LinkStatsRepository linkStatsRepository) {
        this.linkStatsRepository = linkStatsRepository;
    }

    @GetMapping("/{code}/stats")
    public StatsResponse getStats(@PathVariable String code) {
        LinkStats stats = linkStatsRepository.findById(code)
                .orElse(new LinkStats(code, 0, null, null));

        return new StatsResponse(stats.getShortCode(), stats.getClickCount(), stats.getLastClickAt(), stats.getLastReferrer());
    }

    public record StatsResponse(String shortCode, long clickCount, Instant lastClickAt, String lastReferrer) {}
}
