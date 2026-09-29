package io.portfolio.urlshortener.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

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
    private LinkStatsRepository linkStatsRepository;

    @Test
    void getStats_existing_returnsStats() throws Exception {
        when(linkStatsRepository.findById("mycode"))
                .thenReturn(Optional.of(new LinkStats("mycode", 5, Instant.now(), "https://google.com")));

        mockMvc.perform(get("/api/links/mycode/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(5))
                .andExpect(jsonPath("$.shortCode").value("mycode"))
                .andExpect(jsonPath("$.lastReferrer").value("https://google.com"));
    }

    @Test
    void getStats_noClicksYet_returnsZeroStats() throws Exception {
        when(linkStatsRepository.findById("newcode"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/links/newcode/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.shortCode").value("newcode"));
    }
}
