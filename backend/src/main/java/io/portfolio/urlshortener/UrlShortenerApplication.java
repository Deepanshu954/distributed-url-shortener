package io.portfolio.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * JPA is multi-datasource (ADR-010): shard-side scoping lives in
 * {@link ShardJpaConfig}, analytics in
 * {@code io.portfolio.urlshortener.analytics.AnalyticsDbConfig}. Keep this class
 * annotation-light — {@code @WebMvcTest} slices use it as the context root,
 * so anything added here loads in every web slice test.
 */
@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
@ConfigurationPropertiesScan
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
