package io.portfolio.urlshortener.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice. Security filters disabled (addFilters=false) — the Spring
 * Security default chain would 401 everything; security config is Track C's
 * territory.
 */
@WebMvcTest(controllers = LinkController.class)
@AutoConfigureMockMvc(addFilters = false)
class LinkControllerTest {

    private static final String LONG_URL = "https://example.com/x";
    private static final LinkResponse RESPONSE =
            new LinkResponse("abc123", "http://localhost:8080/abc123", LONG_URL, null);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShortenService shortenService;

    @Test
    void createReturns201WithBody() throws Exception {
        when(shortenService.create(any(), isNull(), anyString(), isNull()))
                .thenReturn(new ShortenService.CreationResult(RESPONSE, false));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG_URL + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.longUrl").value(LONG_URL));
    }

    @Test
    void idempotentReplayReturns200() throws Exception {
        when(shortenService.create(any(), eq("idem-1"), anyString(), isNull()))
                .thenReturn(new ShortenService.CreationResult(RESPONSE, true));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-1")
                        .content("{\"longUrl\":\"" + LONG_URL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"));
    }

    @Test
    void validationErrorReturns400WithErrorBody() throws Exception {
        when(shortenService.create(any(), isNull(), anyString(), isNull()))
                .thenThrow(new ValidationException("longUrl must use http or https"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("longUrl must use http or https"));
    }

    @Test
    void malformedJsonReturns400WithErrorBody() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void aliasConflictReturns409WithErrorBody() throws Exception {
        when(shortenService.create(any(), isNull(), anyString(), isNull()))
                .thenThrow(new AliasConflictException("my-alias"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG_URL + "\",\"customAlias\":\"my-alias\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void metadataReturns200() throws Exception {
        when(shortenService.getLink("abc123")).thenReturn(new LinkMetadataResponse(
                "abc123", "http://localhost:8080/abc123", LONG_URL,
                Instant.parse("2026-07-01T00:00:00Z"), null, false));

        mockMvc.perform(get("/api/links/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.longUrl").value(LONG_URL))
                .andExpect(jsonPath("$.customAlias").value(false));
    }

    @Test
    void metadataUnknownCodeReturns404WithErrorBody() throws Exception {
        when(shortenService.getLink("nope")).thenThrow(new NotFoundException("nope"));

        mockMvc.perform(get("/api/links/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateReturns200WithUpdatedLink() throws Exception {
        LinkMetadataResponse updated = new LinkMetadataResponse(
                "abc123", "http://localhost:8080/abc123", "https://updated.com",
                Instant.parse("2026-07-01T00:00:00Z"), null, false);
        when(shortenService.updateLink(eq("abc123"), eq("https://updated.com"), any(), anyString()))
                .thenReturn(updated);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/links/abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://updated.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.longUrl").value("https://updated.com"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/links/abc123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void listRecentReturnsPage() throws Exception {
        LinkMetadataResponse item = new LinkMetadataResponse(
                "abc123", "http://localhost:8080/abc123", LONG_URL,
                Instant.parse("2026-07-01T00:00:00Z"), null, false);
        when(shortenService.listRecentLinks(any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(item)));

        mockMvc.perform(get("/api/links?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shortCode").value("abc123"));
    }
}
