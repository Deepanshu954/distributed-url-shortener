package io.portfolio.urlshortener.analytics;

import io.portfolio.urlshortener.analytics.controller.StatsController;
import io.portfolio.urlshortener.analytics.dto.StatsResponse;
import io.portfolio.urlshortener.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void getStats_existing_returnsStats() throws Exception {
        when(analyticsService.getStats("mycode"))
                .thenReturn(new StatsResponse("mycode", 5, Instant.now(), "https://google.com"));

        mockMvc.perform(get("/api/links/mycode/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(5))
                .andExpect(jsonPath("$.shortCode").value("mycode"))
                .andExpect(jsonPath("$.lastReferrer").value("https://google.com"));

        mockMvc.perform(get("/api/urls/mycode/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(5));
    }

    @Test
    void getStats_noClicksYet_returnsZeroStats() throws Exception {
        when(analyticsService.getStats("newcode"))
                .thenReturn(new StatsResponse("newcode", 0, null, null));

        mockMvc.perform(get("/api/links/newcode/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.shortCode").value("newcode"));
    }
}
