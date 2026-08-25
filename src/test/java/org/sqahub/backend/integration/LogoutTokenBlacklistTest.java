package org.sqahub.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.sqahub.backend.dto.RegisterRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Memastikan token JWT langsung tidak valid lagi setelah logout, walau masa berlakunya
 * secara teknis belum habis - membuktikan TokenBlacklistService + JwtAuthenticationFilter
 * bekerja sama dengan benar di alur nyata (bukan cuma unit test terisolasi).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LogoutTokenBlacklistTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /auth/logout - Token langsung ditolak (401) di request berikutnya")
    void logout_invalidatesTokenImmediately() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        RegisterRequest registerBody = RegisterRequest.builder()
                .username("logout_user_" + suffix).email("logout_" + suffix + "@example.com")
                .name("Logout Test").password("P@ssw0rd" + suffix)
                .role(org.sqahub.backend.config.Role.TESTER)
                .build();

        HttpHeaders anonHeaders = new HttpHeaders();
        anonHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> registerResp = restTemplate.postForEntity(
                "/api/v1/auth/register", new HttpEntity<>(registerBody, anonHeaders), String.class);
        assertEquals(HttpStatus.OK, registerResp.getStatusCode());
        JsonNode json = objectMapper.readTree(registerResp.getBody());
        String token = json.get("token").asText();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);

        // Token masih valid -> bisa akses endpoint terproteksi
        ResponseEntity<String> beforeLogout = restTemplate.exchange(
                "/api/v1/project", HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
        assertEquals(HttpStatus.OK, beforeLogout.getStatusCode());

        // Logout
        ResponseEntity<String> logoutResp = restTemplate.exchange(
                "/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(authHeaders), String.class);
        assertEquals(HttpStatus.OK, logoutResp.getStatusCode());

        // Token yang SAMA sekarang harus ditolak, walau belum kadaluarsa
        ResponseEntity<String> afterLogout = restTemplate.exchange(
                "/api/v1/project", HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogout.getStatusCode());
    }
}
