package io.portfolio.urlshortener.integration;

import io.portfolio.urlshortener.contracts.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.sharding.enabled=true",
        "app.sharding.shards[0].name=shard1",
        "app.sharding.shards[0].primary.jdbc-url=jdbc:h2:mem:shard1_test2;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=KEY;INIT=CREATE SCHEMA IF NOT EXISTS shard1",
        "app.sharding.shards[0].primary.username=sa",
        "app.sharding.shards[0].primary.password=",
        "app.sharding.shards[0].replica.jdbc-url=jdbc:h2:mem:shard1_test2;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=KEY;INIT=CREATE SCHEMA IF NOT EXISTS shard1",
        "app.sharding.shards[0].replica.username=sa",
        "app.sharding.shards[0].replica.password=",
        "ratelimit.enabled=true",
        "app.kafka.enabled=false",
        "app.node-id=1",
        "app.analytics-db.jdbc-url=jdbc:h2:mem:analytics_test2;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "app.analytics-db.username=sa",
        "app.analytics-db.password=",
        "app.analytics-db.pool-size=2",
        "app.base-url=http://localhost:8080"
})
class FilterChainOrderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiter rateLimiter;

    @Test
    void endpoint_whenRateLimited_returns429() throws Exception {
        when(rateLimiter.check(anyString(), eq("write")))
                .thenReturn(RateLimiter.RateLimitResult.denied(5000));

        mockMvc.perform(post("/api/links")
                        .contentType("application/json")
                        .content("{\"longUrl\":\"http://example.com\"}"))
                .andExpect(status().isTooManyRequests()); // 429
    }
}
