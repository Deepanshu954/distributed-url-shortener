package io.portfolio.urlshortener.ratelimit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disables Spring Boot servlet container auto-registration for RateLimitFilter
 * so it only executes once inside Spring Security's SecurityFilterChain.
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RateLimitFilter.class)
    public FilterRegistrationBean<RateLimitFilter> disableRateLimitAutoRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(rateLimitFilter);
        reg.setEnabled(false);
        return reg;
    }
}
