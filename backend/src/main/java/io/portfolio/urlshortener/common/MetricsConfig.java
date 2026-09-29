package io.portfolio.urlshortener.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom application metrics exposed under /actuator/prometheus for APM dashboards.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter linksCreatedSnowflakeCounter(MeterRegistry registry) {
        return Counter.builder("links.created.total")
                .tag("type", "snowflake")
                .description("Total short links generated with Snowflake IDs")
                .register(registry);
    }

    @Bean
    public Counter linksCreatedCustomCounter(MeterRegistry registry) {
        return Counter.builder("links.created.total")
                .tag("type", "custom")
                .description("Total short links generated with custom aliases")
                .register(registry);
    }

    @Bean
    public Counter redirectHitCounter(MeterRegistry registry) {
        return Counter.builder("redirect.requests.total")
                .tag("outcome", "hit")
                .description("Total redirects served from cache (Hit)")
                .register(registry);
    }

    @Bean
    public Counter redirectMissCounter(MeterRegistry registry) {
        return Counter.builder("redirect.requests.total")
                .tag("outcome", "miss")
                .description("Total redirects loaded from DB on cache miss")
                .register(registry);
    }

    @Bean
    public Counter redirectNegativeHitCounter(MeterRegistry registry) {
        return Counter.builder("redirect.requests.total")
                .tag("outcome", "negative_hit")
                .description("Total redirects blocked by anti-penetration negative cache")
                .register(registry);
    }

    @Bean
    public Timer redirectLatencyTimer(MeterRegistry registry) {
        return Timer.builder("redirect.latency")
                .description("Latency distribution for URL redirection resolution")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
