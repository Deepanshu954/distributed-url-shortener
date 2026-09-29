package io.portfolio.urlshortener.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analytics-db")
public record AnalyticsDbProperties(
        String jdbcUrl,
        String username,
        String password,
        int poolSize) {

    public AnalyticsDbProperties {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = "jdbc:h2:mem:analytics;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
        }
        if (username == null) {
            username = "sa";
        }
        if (password == null) {
            password = "";
        }
        if (poolSize <= 0) {
            poolSize = 5;
        }
    }
}
