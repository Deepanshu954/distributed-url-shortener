package io.portfolio.urlshortener.shortener;

import io.portfolio.urlshortener.shortener.controller.UrlController;
import io.portfolio.urlshortener.shortener.dto.CreateUrlRequest;
import io.portfolio.urlshortener.shortener.dto.UrlMetadataResponse;
import io.portfolio.urlshortener.shortener.dto.UrlResponse;
import io.portfolio.urlshortener.shortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UrlController.class)
@AutoConfigureMockMvc(addFilters = false)
class UrlControllerTest {

    private static final String LONG_URL = "https://example.com/x";
    private static final UrlResponse RESPONSE =
            new UrlResponse("abc123", "http://localhost:8080/abc123", LONG_URL, null, "tok123");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @Test
    void createReturns201WithBody() throws Exception {
        when(urlService.create(any(), isNull(), anyString(), isNull()))
                .thenReturn(new UrlService.CreationResult(RESPONSE, false));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG_URL + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.longUrl").value(LONG_URL))
                .andExpect(jsonPath("$.manageToken").value("tok123"));

        // Also test /api/urls path
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG_URL + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void idempotentReplayReturns200() throws Exception {
        when(urlService.create(any(), eq("idem-1"), anyString(), isNull()))
                .thenReturn(new UrlService.CreationResult(RESPONSE, true));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-1")
                        .content("{\"longUrl\":\"" + LONG_URL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"));
    }

    @Test
    void validationErrorReturns400WithErrorBody() throws Exception {
        when(urlService.create(any(), isNull(), anyString(), isNull()))
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
        when(urlService.create(any(), isNull(), anyString(), isNull()))
                .thenThrow(new AliasConflictException("my-alias"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"" + LONG_URL + "\",\"customAlias\":\"my-alias\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void metadataReturns200() throws Exception {
        when(urlService.get("abc123")).thenReturn(new UrlMetadataResponse(
                "abc123", "http://localhost:8080/abc123", LONG_URL,
                Instant.parse("2026-07-01T00:00:00Z"), null, false));

        mockMvc.perform(get("/api/links/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.longUrl").value(LONG_URL))
                .andExpect(jsonPath("$.customAlias").value(false));

        mockMvc.perform(get("/api/urls/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"));
    }

    @Test
    void metadataUnknownCodeReturns404WithErrorBody() throws Exception {
        when(urlService.get("nope")).thenThrow(new NotFoundException("nope"));

        mockMvc.perform(get("/api/links/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateReturns200WithUpdatedLink() throws Exception {
        UrlMetadataResponse updated = new UrlMetadataResponse(
                "abc123", "http://localhost:8080/abc123", "https://updated.com",
                Instant.parse("2026-07-01T00:00:00Z"), null, false);
        when(urlService.update(eq("abc123"), eq("https://updated.com"), any(), eq("manage-tok"), anyString()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/links/abc123")
                        .header("X-Manage-Token", "manage-tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://updated.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.longUrl").value("https://updated.com"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/links/abc123")
                        .header("X-Manage-Token", "manage-tok"))
                .andExpect(status().isNoContent());

        verify(urlService).delete(eq("abc123"), eq("manage-tok"), anyString());
    }

    @Test
    void listRecentReturnsPage() throws Exception {
        UrlMetadataResponse item = new UrlMetadataResponse(
                "abc123", "http://localhost:8080/abc123", LONG_URL,
                Instant.parse("2026-07-01T00:00:00Z"), null, false);
        when(urlService.listRecent(any()))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/links?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shortCode").value("abc123"));
    }

    // --- Redirect endpoint tests ---

    @Test
    void knownCodeReturns302WithLocationAndNoBody() throws Exception {
        when(urlService.resolve(eq("abc123"), any(), any(), anyString())).thenReturn(LONG_URL);

        mockMvc.perform(get("/abc123")
                        .header("Referer", "https://ref.example")
                        .header("User-Agent", "TestUA/1.0")
                        .header("X-Request-Id", "req-42"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LONG_URL))
                .andExpect(content().string(""));

        verify(urlService).resolve("abc123", "https://ref.example", "TestUA/1.0", "req-42");
    }

    @Test
    void missingRequestIdIsGenerated() throws Exception {
        when(urlService.resolve(eq("abc123"), any(), any(), anyString())).thenReturn(LONG_URL);

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound());

        ArgumentCaptor<String> rid = ArgumentCaptor.forClass(String.class);
        verify(urlService).resolve(eq("abc123"), any(), any(), rid.capture());
        assertThat(rid.getValue()).isNotBlank();
    }

    @Test
    void unknownCodeReturns404WithErrorBody() throws Exception {
        when(urlService.resolve(eq("nope404"), any(), any(), anyString()))
                .thenThrow(new NotFoundException("nope404"));

        mockMvc.perform(get("/nope404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void infraFailureReturns503WithErrorBody() throws Exception {
        when(urlService.resolve(eq("abc123"), any(), any(), anyString()))
                .thenThrow(new InfraUnavailableException("db down"));

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void pathsOutsideShortCodePatternDoNotHitTheRedirectHandler() throws Exception {
        // 33 chars exceeds {1,32}
        mockMvc.perform(get("/" + "a".repeat(33)))
                .andExpect(status().isNotFound());
        // dotted resource names don't match pattern
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(urlService);
    }
}
