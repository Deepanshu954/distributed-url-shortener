package io.portfolio.urlshortener.analytics.controller;

import io.portfolio.urlshortener.analytics.dto.EdgeClickRequest;
import io.portfolio.urlshortener.analytics.dto.StatsResponse;
import io.portfolio.urlshortener.analytics.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class StatsController {

    private final AnalyticsService analyticsService;

    public StatsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Query click analytics for a specific short code. Supports both /api/links and /api/urls prefixes.
     */
    @GetMapping({"/api/links/{code}/stats", "/api/urls/{code}/stats"})
    public StatsResponse getStats(@PathVariable String code) {
        return analyticsService.getStats(code);
    }

    /**
     * Edge click telemetry ingestion endpoint called by Cloudflare Workers on edge cache hits.
     */
    @PostMapping({"/api/links/{code}/stats/click", "/api/urls/{code}/stats/click"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recordEdgeClick(
            @PathVariable String code,
            @RequestBody(required = false) EdgeClickRequest request,
            @RequestHeader(value = "Referer", required = false) String referrer,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        analyticsService.recordEdgeClick(code, referrer, userAgent, requestId);
    }
}
