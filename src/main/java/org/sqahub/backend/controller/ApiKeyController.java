package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.ApiKeyRequest;
import org.sqahub.backend.dto.ApiKeyResponse;
import org.sqahub.backend.service.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller untuk menangani manajemen API Key (Skema 8).
 * Endpoint: /api/v1/apikey
 */
@RestController
@RequestMapping("/api/v1/apikey")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    // --- CREATE (Generate Key) ---
    // Hanya Admin dan Tester/Developer yang boleh membuat kunci baru
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<ApiKeyResponse> createApiKey(@RequestBody ApiKeyRequest request) {
        ApiKeyResponse response = apiKeyService.createApiKey(request);
        // Response ini akan berisi 'rawKey' yang hanya muncul sekali
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- READ (Get All Keys for User) ---
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ApiKeyResponse>> getAllKeysForCurrentUser() {
        List<ApiKeyResponse> response = apiKeyService.getAllKeysForCurrentUser();
        return ResponseEntity.ok(response);
    }

    // --- DELETE (Revoke Key) ---
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<Void> revokeApiKey(@PathVariable Long id) {
        apiKeyService.revokeApiKey(id);
        return ResponseEntity.noContent().build();
    }
}
