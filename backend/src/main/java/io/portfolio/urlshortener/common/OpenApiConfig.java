package io.portfolio.urlshortener.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 configuration for SpringDoc interactive API documentation.
 * Accessible at /swagger-ui/index.html and /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TinyScale Distributed URL Shortener API")
                        .version("2.0.0")
                        .description("High-concurrency, zero-friction distributed URL shortening engine featuring " +
                                "Twitter Snowflake 64-bit ID generation, Murmur3 consistent hashing, " +
                                "stampede-proof Redis cache-aside, token-bucket rate limiting, and asynchronous click telemetry.")
                        .contact(new Contact()
                                .name("Engineering Team")
                                .url("https://github.com/Deepanshu954/distributed-url-shortener"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
