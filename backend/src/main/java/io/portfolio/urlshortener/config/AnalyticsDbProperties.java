package io.portfolio.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.analytics-db")
public record AnalyticsDbProperties(
        String jdbcUrl,
        String username,
        String password,
        @DefaultValue("5") int poolSize) {
}
