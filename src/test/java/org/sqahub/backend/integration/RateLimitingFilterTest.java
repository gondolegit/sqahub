package org.sqahub.backend.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.sqahub.backend.dto.AuthenticationRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Memastikan RateLimitingFilter benar-benar memblokir percobaan login beruntun.
 * Threshold diturunkan jadi 3/menit khusus test ini (lewat @TestPropertySource) supaya
 * tesnya cepat dan deterministik, tidak perlu menunggu/mengulang puluhan kali.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.auth.max-attempts=3",
        "app.rate-limit.auth.window-minutes=1"
})
class RateLimitingFilterTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("POST /auth/authenticate - Percobaan ke-4 dalam 1 menit ditolak 429 Too Many Requests")
    void repeatedLoginAttempts_getRateLimited() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        AuthenticationRequest body = AuthenticationRequest.builder()
                .username("user-tidak-ada-" + System.currentTimeMillis())
                .password("salah").build();
        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        // 3 percobaan pertama: gagal karena user tidak ada (401), TAPI belum kena rate limit
        for (int i = 1; i <= 3; i++) {
            ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/authenticate", request, String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode(), "Percobaan ke-" + i + " harusnya 401, bukan rate-limited");
        }

        // Percobaan ke-4: sudah melebihi kuota 3/menit -> 429
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/authenticate", request, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode());
    }
}
